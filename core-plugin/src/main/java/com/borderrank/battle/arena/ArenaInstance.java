package com.borderrank.battle.arena;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.database.MatchDAO;
import com.borderrank.battle.manager.LoadoutManager;
import com.borderrank.battle.manager.RankManager;
import com.borderrank.battle.manager.ScoreboardManager;
import com.borderrank.battle.manager.TriggerRegistry;
import com.borderrank.battle.manager.TrionManager;
import com.borderrank.battle.model.Loadout;
import com.borderrank.battle.model.MapData;
import com.borderrank.battle.model.Season;
import com.borderrank.battle.model.TriggerData;
import com.borderrank.battle.model.WeaponType;
import com.borderrank.battle.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.GameMode;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a single match instance.
 * Manages match state, players, kills, and progression.
 */
public class ArenaInstance {

    public enum ArenaState {
        WAITING, COUNTDOWN, ACTIVE, ENDING, FINISHED
    }

    private final int matchId;
    private ArenaState state;
    private final Set<UUID> players;
    private final Set<UUID> alivePlayers;
    private final Map<UUID, Integer> kills;
    private final Map<UUID, WeaponType> playerWeaponTypes;
    private final MapData mapData;
    private final BlockTracker blockTracker;
    private final long startTime;
    private final int timeLimitSec;
    private int countdownRemaining;
    private long lastTickTime;
    // teamId -> Set<UUID>; null means solo match
    private final Map<Integer, Set<UUID>> teamData;
    // DB match_id (set after createMatch call)
    private int dbMatchId = -1;

    public ArenaInstance(int matchId, MapData mapData, int timeLimitSec) {
        this(matchId, mapData, timeLimitSec, null);
    }

    public ArenaInstance(int matchId, MapData mapData, int timeLimitSec, Map<Integer, Set<UUID>> teamData) {
        this.matchId = matchId;
        this.mapData = mapData;
        this.timeLimitSec = timeLimitSec;
        this.teamData = teamData != null ? new HashMap<>(teamData) : null;
        this.blockTracker = new BlockTracker(matchId);
        this.state = ArenaState.WAITING;
        this.players = new HashSet<>();
        this.alivePlayers = new HashSet<>();
        this.kills = new HashMap<>();
        this.playerWeaponTypes = new HashMap<>();
        this.startTime = System.currentTimeMillis();
        this.countdownRemaining = 10;
        this.lastTickTime = System.currentTimeMillis();

        if (teamData != null) {
            for (Set<UUID> members : teamData.values()) {
                players.addAll(members);
            }
        }
    }

    /**
     * Returns true if both players are on the same team (team match only).
     * Always returns false in solo matches.
     */
    public boolean isTeammate(UUID a, UUID b) {
        if (teamData == null) return false;
        for (Set<UUID> members : teamData.values()) {
            if (members.contains(a) && members.contains(b)) return true;
        }
        return false;
    }

    /**
     * Start the match - give trigger items and initialize.
     */
    public void start() {
        BRBPlugin plugin = BRBPlugin.getInstance();
        LoadoutManager loadoutManager = plugin.getLoadoutManager();
        TriggerRegistry triggerRegistry = plugin.getTriggerRegistry();
        TrionManager trionManager = plugin.getTrionManager();

        state = ArenaState.COUNTDOWN;
        alivePlayers.addAll(players);

        for (UUID uuid : players) {
            kills.put(uuid, 0);
        }

        // Resolve the world for this map
        World mapWorld = Bukkit.getWorld(mapData.getWorldName());
        if (mapWorld == null) {
            mapWorld = Bukkit.getWorlds().get(0); // fallback to default world
            plugin.getLogger().warning("World '" + mapData.getWorldName() + "' not found, using default world.");
        }

        // Shuffle spawn indices for random spawn assignment
        List<Integer> spawnIndices = new ArrayList<>();
        for (int i = 0; i < mapData.getSpawnPointCount(); i++) {
            spawnIndices.add(i);
        }
        Collections.shuffle(spawnIndices);

        int playerIdx = 0;
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            // Teleport to random map spawn point
            int spawnIndex = playerIdx < spawnIndices.size() ? spawnIndices.get(playerIdx) : playerIdx;
            Location spawnLoc = mapData.getSpawnPoint(spawnIndex, mapWorld);
            if (spawnLoc == null) {
                // Fallback: use safe spawn with offset
                spawnLoc = getSafeSpawnLocation(mapWorld, spawnIndex * 10, 0);
                plugin.getLogger().warning("No spawn point #" + spawnIndex + " for map " + mapData.getMapId() + ", using fallback.");
            }
            player.teleport(spawnLoc);

            // Set game mode to SURVIVAL for combat (allows block break/place)
            player.setGameMode(GameMode.SURVIVAL);

            // Clear inventory
            player.getInventory().clear();

            // Get loadout and give trigger items
            Loadout loadout = loadoutManager.getLoadout(uuid, "default");
            if (loadout != null) {
                List<String> slots = loadout.getSlots();
                for (int i = 0; i < Math.min(slots.size(), 8); i++) {
                    String triggerId = slots.get(i);
                    if (triggerId != null && !triggerId.isEmpty()) {
                        TriggerData td = triggerRegistry.get(triggerId);
                        if (td != null) {
                            ItemStack item = new ItemStack(td.getMcItem());
                            ItemMeta meta = item.getItemMeta();
                            if (meta != null) {
                                meta.setDisplayName(ChatColor.GREEN + td.getName());
                                List<String> lore = new ArrayList<>();
                                lore.add(ChatColor.GRAY + td.getDescription());
                                meta.setLore(lore);
                                item.setItemMeta(meta);
                            }

                            // Apply enchantments based on trigger type
                            applyTriggerEnchantments(item, triggerId);

                            player.getInventory().setItem(i, item);
                        }
                    }
                }

                // Give arrows/ammo if player has bow, crossbow, or trident
                boolean needsArrows = false;
                boolean needsTridentReturn = false;
                for (int i = 0; i < Math.min(slots.size(), 8); i++) {
                    String tid = slots.get(i);
                    if (tid == null || tid.isEmpty()) continue;
                    TriggerData tdd = triggerRegistry.get(tid);
                    if (tdd == null) continue;
                    Material mat = tdd.getMcItem();
                    if (mat == Material.BOW || mat == Material.CROSSBOW) {
                        needsArrows = true;
                    }
                    if (mat == Material.TRIDENT) {
                        needsTridentReturn = true;
                    }
                }
                if (needsArrows) {
                    // Give 64 arrows in slot 9 (main inventory, not hotbar - avoids trigger slot conflict)
                    player.getInventory().setItem(9, new ItemStack(Material.ARROW, 64));
                }

                // Determine weapon type for RP calculation
                WeaponType wt = loadoutManager.getWeaponType(loadout, triggerRegistry);
                if (wt != null) {
                    playerWeaponTypes.put(uuid, wt);
                } else {
                    playerWeaponTypes.put(uuid, WeaponType.ATTACKER); // fallback
                }
            } else {
                playerWeaponTypes.put(uuid, WeaponType.ATTACKER);
            }

            // Initialize trion
            trionManager.initPlayer(uuid, 1000);

            // Full health
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);

            // Create match scoreboard and boss bar
            ScoreboardManager sbManager = plugin.getScoreboardManager();
            sbManager.createMatchScoreboard(player, mapData.getDisplayName(), timeLimitSec);

            MessageUtil.sendMessage(player, ChatColor.YELLOW + "マッチ開始！マップ: " + ChatColor.WHITE + mapData.getDisplayName() + ChatColor.YELLOW + " カウントダウン: " + countdownRemaining);
            playerIdx++;
        }

        // Start trion tick loop (HP leak, sustain cost, XP bar update, bailout check)
        trionManager.startTickLoop(plugin, alivePlayers);

        // Record match start in DB (async to avoid blocking main thread)
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MatchDAO matchDAO = plugin.getMatchDAO();
                if (matchDAO == null) return;
                Season season = plugin.getRankManager().getActiveSeason();
                int seasonId = season != null ? season.getId() : 1; // fallback to season 1
                String matchType = (teamData != null) ? "team" : "solo";
                dbMatchId = matchDAO.createMatch(matchType, mapData.getMapId(), seasonId);
                if (dbMatchId > 0) {
                    plugin.getLogger().info("Match #" + matchId + " recorded in DB as match_id=" + dbMatchId + " on map " + mapData.getDisplayName());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to record match start: " + e.getMessage());
            }
        });
    }

    /**
     * Record a kill.
     */
    public void onKill(UUID killer, UUID victim) {
        if (killer != null && kills.containsKey(killer)) {
            kills.put(killer, kills.get(killer) + 1);

            Player killerPlayer = Bukkit.getPlayer(killer);
            Player victimPlayer = Bukkit.getPlayer(victim);

            // Kill feed: broadcast to ALL match participants (including killer)
            String killerName = killerPlayer != null ? killerPlayer.getName() : "???";
            String victimName = victimPlayer != null ? victimPlayer.getName() : "???";
            int killCount = kills.get(killer);
            String killMsg = ChatColor.GOLD + ">> " + ChatColor.WHITE + killerName
                    + ChatColor.GRAY + " -> "
                    + ChatColor.RED + victimName
                    + ChatColor.YELLOW + " [" + killCount + " Kill]";

            for (UUID uuid : players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendMessage("");
                    p.sendMessage(killMsg);
                }
            }
        } else {
            // Bailout (trion zero or other non-player kill)
            Player victimPlayer = Bukkit.getPlayer(victim);
            String victimName = victimPlayer != null ? victimPlayer.getName() : "???";
            String bailoutMsg = ChatColor.YELLOW + ">> " + ChatColor.RED + victimName
                    + ChatColor.GRAY + " - " + ChatColor.YELLOW + "BAILOUT";

            for (UUID uuid : players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendMessage("");
                    p.sendMessage(bailoutMsg);
                }
            }
        }

        alivePlayers.remove(victim);

        // Notify victim
        Player victimPlayer = Bukkit.getPlayer(victim);
        if (victimPlayer != null) {
            MessageUtil.sendErrorMessage(victimPlayer, "ベイルアウト！ロビーに戻ります...");
        }

        // Announce remaining alive count
        int aliveCount = alivePlayers.size();
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(ChatColor.AQUA + ">> " + ChatColor.WHITE + "残り " + aliveCount + " 人");
            }
        }

        if (aliveCount <= 1) {
            end();
        }
    }

    /**
     * Tick the match.
     */
    public void tick() {
        long currentTime = System.currentTimeMillis();
        long deltaMs = currentTime - lastTickTime;
        lastTickTime = currentTime;

        switch (state) {
            case COUNTDOWN -> tickCountdown();
            case ACTIVE -> tickActive(deltaMs);
            case ENDING -> tickEnding();
        }
    }

    private void tickCountdown() {
        countdownRemaining--;

        // Update boss bar during countdown (show full bar with countdown title)
        BRBPlugin plugin = BRBPlugin.getInstance();
        ScoreboardManager scoreboardManager = plugin.getScoreboardManager();
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                scoreboardManager.updateBossBar(player, timeLimitSec, timeLimitSec);
            }
        }

        if (countdownRemaining <= 0) {
            state = ArenaState.ACTIVE;
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    MessageUtil.sendSuccessMessage(player, ChatColor.BOLD + "バトル開始！");
                }
            }
        } else if (countdownRemaining <= 5) {
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    MessageUtil.sendMessage(player, ChatColor.YELLOW + "" + countdownRemaining + "...");
                }
            }
        }
    }

    private void tickActive(long deltaMs) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        ScoreboardManager scoreboardManager = plugin.getScoreboardManager();
        TrionManager trionManager = plugin.getTrionManager();

        long elapsedSec = (System.currentTimeMillis() - startTime) / 1000;
        int timeRemaining = (int) Math.max(0, timeLimitSec - elapsedSec);
        int aliveCount = alivePlayers.size();

        // Update scoreboard and boss bar for each player
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            int playerKills = kills.getOrDefault(uuid, 0);
            double trion = trionManager.getTrion(uuid);
            int maxTrion = trionManager.getMaxTrion(uuid);

            scoreboardManager.updateFullScoreboard(player, mapData.getDisplayName(), playerKills,
                    aliveCount, timeRemaining, trion, maxTrion);
            scoreboardManager.updateBossBar(player, timeRemaining, timeLimitSec);
        }

        // Time warnings
        long remaining = timeLimitSec - elapsedSec;
        if (remaining == 60 || remaining == 30 || remaining == 10) {
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    MessageUtil.sendMessage(player, ChatColor.RED + "残り" + remaining + "秒！");
                }
            }
        }

        if (elapsedSec >= timeLimitSec) {
            end();
        }
    }

    private void tickEnding() {
        state = ArenaState.FINISHED;
    }

    /**
     * End the match - restore blocks, calculate RP and announce results.
     */
    public void end() {
        if (state == ArenaState.ENDING || state == ArenaState.FINISHED) return;
        state = ArenaState.ENDING;

        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        // Stop trion tick loop
        plugin.getTrionManager().stopTickLoop();

        // ★ Restore all block changes (before anything else)
        blockTracker.restoreAllBlocks();

        // ★ Release map back to available pool
        plugin.getMapManager().releaseMap(mapData.getMapId());

        // Sort by kills descending, then by alive status
        List<Map.Entry<UUID, Integer>> sortedPlayers = new ArrayList<>(kills.entrySet());
        sortedPlayers.sort((a, b) -> {
            // Alive players rank higher
            boolean aAlive = alivePlayers.contains(a.getKey());
            boolean bAlive = alivePlayers.contains(b.getKey());
            if (aAlive != bAlive) return bAlive ? 1 : -1;
            return Integer.compare(b.getValue(), a.getValue());
        });

        // Calculate and apply RP
        boolean isSoloMatch = (teamData == null && sortedPlayers.size() == 2);

        // Pre-collect RP values for Elo calculation (solo match)
        int winnerRP = 0;
        int loserRP = 0;
        if (isSoloMatch) {
            UUID winnerId = sortedPlayers.get(0).getKey();
            UUID loserId = sortedPlayers.get(1).getKey();
            WeaponType winnerWt = playerWeaponTypes.getOrDefault(winnerId, WeaponType.ATTACKER);
            WeaponType loserWt = playerWeaponTypes.getOrDefault(loserId, WeaponType.ATTACKER);
            var winnerPlayer = rankManager.getPlayer(winnerId);
            var loserPlayer = rankManager.getPlayer(loserId);
            if (winnerPlayer != null) {
                var wrp = winnerPlayer.getWeaponRP(winnerWt);
                winnerRP = wrp != null ? wrp.getRp() : 1000;
            }
            if (loserPlayer != null) {
                var wrp = loserPlayer.getWeaponRP(loserWt);
                loserRP = wrp != null ? wrp.getRp() : 1000;
            }
        }

        int placement = 1;
        for (Map.Entry<UUID, Integer> entry : sortedPlayers) {
            UUID uuid = entry.getKey();
            int playerKills = entry.getValue();
            boolean survived = alivePlayers.contains(uuid);

            int rpGain;
            if (isSoloMatch) {
                if (placement == 1) {
                    rpGain = rankManager.calculateSoloRP(winnerRP, loserRP);
                } else {
                    rpGain = rankManager.calculateLossRP(loserRP, winnerRP);
                }
            } else {
                rpGain = rankManager.calculateTeamRP(placement, playerKills, survived);
            }

            // Apply RP to the player's weapon type
            WeaponType wt = playerWeaponTypes.getOrDefault(uuid, WeaponType.ATTACKER);
            rankManager.addPlayerRP(uuid, wt.name(), rpGain);

            // Save player data and update rank
            var brPlayer = rankManager.getPlayer(uuid);
            if (brPlayer != null) {
                var wrp = brPlayer.getWeaponRP(wt);
                if (wrp != null) {
                    if (placement == 1) {
                        wrp.setWins(wrp.getWins() + 1);
                    } else {
                        wrp.setLosses(wrp.getLosses() + 1);
                    }
                }
                rankManager.recalculateRank(brPlayer);
                rankManager.savePlayer(brPlayer);
            }

            // Notify player
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                String rpText = rpGain >= 0 ? (ChatColor.GREEN + "+" + rpGain) : (ChatColor.RED + "" + rpGain);
                MessageUtil.sendInfoMessage(player, "=== マッチ結果 ===");
                MessageUtil.sendInfoMessage(player, "マップ: " + mapData.getDisplayName());
                MessageUtil.sendInfoMessage(player, "順位: #" + placement + " | キル: " + playerKills + " | RP: " + rpText);

                // Restore player state
                player.getInventory().clear();
                player.setGameMode(GameMode.ADVENTURE);
                if (!player.isDead()) {
                    player.setHealth(player.getMaxHealth());
                    player.setFoodLevel(20);
                }

                // Remove scoreboard and boss bar
                plugin.getScoreboardManager().removeScoreboard(player);
            }

            placement++;
        }

        // Send match summary to all players
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                MessageUtil.sendInfoMessage(player, "--- ランキング ---");
                int rank = 1;
                for (Map.Entry<UUID, Integer> entry : sortedPlayers) {
                    Player p = Bukkit.getPlayer(entry.getKey());
                    String name = p != null ? p.getName() : "Unknown";
                    String marker = alivePlayers.contains(entry.getKey()) ? " ★" : "";
                    MessageUtil.sendInfoMessage(player, "#" + rank + " " + name + " - " + entry.getValue() + "キル" + marker);
                    rank++;
                }
            }
        }

        // Save match results to DB (async)
        final List<Map.Entry<UUID, Integer>> finalSorted = new ArrayList<>(sortedPlayers);
        final Set<UUID> finalAlive = new HashSet<>(alivePlayers);
        final Map<UUID, WeaponType> finalWeapons = new HashMap<>(playerWeaponTypes);
        final int finalDbMatchId = dbMatchId;
        final boolean finalIsSolo = isSoloMatch;
        final int finalWinnerRP = winnerRP;
        final int finalLoserRP = loserRP;

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MatchDAO matchDAO = plugin.getMatchDAO();
                if (matchDAO == null || finalDbMatchId <= 0) return;

                int durationSec = (int) ((System.currentTimeMillis() - startTime) / 1000);
                matchDAO.endMatch(finalDbMatchId, durationSec);

                int p = 1;
                for (Map.Entry<UUID, Integer> entry : finalSorted) {
                    UUID uuid = entry.getKey();
                    int playerKills = entry.getValue();
                    boolean survived = finalAlive.contains(uuid);
                    WeaponType wt = finalWeapons.getOrDefault(uuid, WeaponType.ATTACKER);

                    int rpChange;
                    if (finalIsSolo) {
                        rpChange = (p == 1)
                                ? rankManager.calculateSoloRP(finalWinnerRP, finalLoserRP)
                                : rankManager.calculateLossRP(finalLoserRP, finalWinnerRP);
                    } else {
                        rpChange = rankManager.calculateTeamRP(p, playerKills, survived);
                    }

                    matchDAO.insertResult(
                        finalDbMatchId, uuid, null, wt,
                        playerKills, survived ? 0 : 1,
                        survived, rpChange, p
                    );
                    p++;
                }

                plugin.getLogger().info("Match #" + matchId + " results saved to DB (db_id=" + finalDbMatchId + ")");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to save match results: " + e.getMessage());
            }
        });
    }

    /**
     * Apply trigger-specific enchantments to weapon items.
     */
    private void applyTriggerEnchantments(ItemStack item, String triggerId) {
        switch (triggerId) {
            case "egret" -> {
                item.addUnsafeEnchantment(Enchantment.POWER, 5);
            }
            case "lightning" -> {
                item.addUnsafeEnchantment(Enchantment.POWER, 2);
                item.addUnsafeEnchantment(Enchantment.PUNCH, 1);
            }
            case "ibis" -> {
                item.addUnsafeEnchantment(Enchantment.POWER, 7);
                item.addUnsafeEnchantment(Enchantment.PUNCH, 2);
            }
            case "asteroid" -> {
                item.addUnsafeEnchantment(Enchantment.POWER, 1);
            }
            case "viper" -> {
                item.addUnsafeEnchantment(Enchantment.POWER, 1);
                item.addUnsafeEnchantment(Enchantment.FLAME, 1);
            }
            case "hound" -> {
                item.addUnsafeEnchantment(Enchantment.LOYALTY, 3);
            }
            case "meteora" -> {
                item.addUnsafeEnchantment(Enchantment.MULTISHOT, 1);
                item.addUnsafeEnchantment(Enchantment.QUICK_CHARGE, 2);
            }
            case "kogetsu" -> {
                item.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
            }
            case "scorpion" -> {
                item.addUnsafeEnchantment(Enchantment.SHARPNESS, 3);
            }
            case "raygust" -> {
                item.addUnsafeEnchantment(Enchantment.SHARPNESS, 2);
                item.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
            }
        }
    }

    /**
     * Find a safe spawn location - on top of the highest block, not inside ground.
     * Used as fallback when map spawn points are not defined.
     */
    private Location getSafeSpawnLocation(World world, int x, int z) {
        int highestY = world.getHighestBlockYAt(x, z);
        Location loc = new Location(world, x + 0.5, highestY + 1.0, z + 0.5);

        Block feetBlock = loc.getBlock();
        Block headBlock = feetBlock.getRelative(0, 1, 0);
        if (feetBlock.getType() != Material.AIR) {
            loc.setY(loc.getY() + 1);
        }
        if (headBlock.getType() != Material.AIR) {
            loc.setY(loc.getY() + 1);
        }

        return loc;
    }

    public Set<UUID> getAlivePlayers() { return new HashSet<>(alivePlayers); }
    public void addPlayer(UUID uuid) { players.add(uuid); }
    public int getMatchId() { return matchId; }
    public ArenaState getState() { return state; }
    public Set<UUID> getPlayers() { return new HashSet<>(players); }
    public int getPlayerKills(UUID uuid) { return kills.getOrDefault(uuid, 0); }
    public String getMapName() { return mapData.getMapId(); }
    public MapData getMapData() { return mapData; }
    public BlockTracker getBlockTracker() { return blockTracker; }
}
