package com.borderrank.battle.arena;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.manager.LoadoutManager;
import com.borderrank.battle.manager.RankManager;
import com.borderrank.battle.manager.ScoreboardManager;
import com.borderrank.battle.manager.TriggerRegistry;
import com.borderrank.battle.manager.TrionManager;
import com.borderrank.battle.model.Loadout;
import com.borderrank.battle.model.TriggerData;
import com.borderrank.battle.model.WeaponType;
import com.borderrank.battle.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
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
    private final String mapName;
    private final long startTime;
    private final int timeLimitSec;
    private int countdownRemaining;
    private long lastTickTime;
    // teamId -> Set<UUID>; null means solo match
    private final Map<Integer, Set<UUID>> teamData;

    public ArenaInstance(int matchId, String mapName, int timeLimitSec) {
        this(matchId, mapName, timeLimitSec, null);
    }

    public ArenaInstance(int matchId, String mapName, int timeLimitSec, Map<Integer, Set<UUID>> teamData) {
        this.matchId = matchId;
        this.mapName = mapName;
        this.timeLimitSec = timeLimitSec;
        this.teamData = teamData != null ? new HashMap<>(teamData) : null;
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

        int spawnIndex = 0;
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            // Teleport to spawn - find safe location on top of ground
            Location spawnLoc = getSafeSpawnLocation(player.getWorld(), spawnIndex * 10, 0);
            player.teleport(spawnLoc);

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
                    // Bows/crossbows auto-pull arrows from anywhere in inventory
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

            MessageUtil.sendMessage(player, ChatColor.YELLOW + "マッチ開始！カウントダウン: " + countdownRemaining);
            spawnIndex++;
        }

        // Start trion tick loop (HP leak, sustain cost, XP bar update, bailout check)
        trionManager.startTickLoop(plugin, alivePlayers);
    }

    /**
     * Record a kill.
     */
    public void onKill(UUID killer, UUID victim) {
        if (killer != null && kills.containsKey(killer)) {
            kills.put(killer, kills.get(killer) + 1);

            Player killerPlayer = Bukkit.getPlayer(killer);
            Player victimPlayer = Bukkit.getPlayer(victim);
            if (killerPlayer != null && victimPlayer != null) {
                MessageUtil.sendSuccessMessage(killerPlayer, victimPlayer.getName() + " を撃破！ (計" + kills.get(killer) + "キル)");
            }
        }

        alivePlayers.remove(victim);

        // Notify victim
        Player victimPlayer = Bukkit.getPlayer(victim);
        if (victimPlayer != null) {
            MessageUtil.sendErrorMessage(victimPlayer, "ベイルアウト！観戦モードに移行します...");
        }

        if (alivePlayers.size() <= 1) {
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

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                scoreboardManager.updatePlayerScore(player, kills.getOrDefault(uuid, 0));
            }
        }

        long elapsedSec = (System.currentTimeMillis() - startTime) / 1000;

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
     * End the match - calculate RP and announce results.
     */
    public void end() {
        state = ArenaState.ENDING;

        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        // Stop trion tick loop
        plugin.getTrionManager().stopTickLoop();

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
        int placement = 1;
        for (Map.Entry<UUID, Integer> entry : sortedPlayers) {
            UUID uuid = entry.getKey();
            int playerKills = entry.getValue();
            boolean survived = alivePlayers.contains(uuid);

            // RP calculation: placement bonus + kill bonus + survival bonus
            int rpGain = rankManager.calculateTeamRP(placement, playerKills, survived);

            // Apply RP to the player's weapon type
            WeaponType wt = playerWeaponTypes.getOrDefault(uuid, WeaponType.ATTACKER);
            rankManager.addPlayerRP(uuid, wt.name(), rpGain);

            // Save player data
            var brPlayer = rankManager.getPlayer(uuid);
            if (brPlayer != null) {
                // Update wins/losses
                var wrp = brPlayer.getWeaponRP(wt);
                if (wrp != null) {
                    if (placement == 1) {
                        wrp.setWins(wrp.getWins() + 1);
                    } else {
                        wrp.setLosses(wrp.getLosses() + 1);
                    }
                }
                rankManager.savePlayer(brPlayer);
            }

            // Notify player
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                String rpText = rpGain >= 0 ? (ChatColor.GREEN + "+" + rpGain) : (ChatColor.RED + "" + rpGain);
                MessageUtil.sendInfoMessage(player, "=== マッチ結果 ===");
                MessageUtil.sendInfoMessage(player, "順位: #" + placement + " | キル: " + playerKills + " | RP: " + rpText);

                // Restore player state (only if alive - dead players handled by respawn)
                player.getInventory().clear();
                if (!player.isDead()) {
                    player.setHealth(player.getMaxHealth());
                    player.setFoodLevel(20);
                }
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
    }

    /**
     * Apply trigger-specific enchantments to weapon items.
     */
    private void applyTriggerEnchantments(ItemStack item, String triggerId) {
        switch (triggerId) {
            // Sniper: Egret - POWER V (high damage charged shots)
            case "egret" -> {
                item.addUnsafeEnchantment(Enchantment.POWER, 5);
            }
            // Sniper: Lightning - Piercing (arrows pass through enemies)
            case "lightning" -> {
                item.addUnsafeEnchantment(Enchantment.POWER, 2);
                item.addUnsafeEnchantment(Enchantment.PUNCH, 1);
            }
            // Sniper: Ibis - Max power (ultimate sniper)
            case "ibis" -> {
                item.addUnsafeEnchantment(Enchantment.POWER, 7);
                item.addUnsafeEnchantment(Enchantment.PUNCH, 2);
            }
            // Shooter: Asteroid - Standard bow with slight power
            case "asteroid" -> {
                item.addUnsafeEnchantment(Enchantment.POWER, 1);
            }
            // Shooter: Viper - Quick fire bow
            case "viper" -> {
                item.addUnsafeEnchantment(Enchantment.POWER, 1);
                item.addUnsafeEnchantment(Enchantment.FLAME, 1);
            }
            // Shooter: Hound - Trident with loyalty (returns after throw) + riptide
            case "hound" -> {
                item.addUnsafeEnchantment(Enchantment.LOYALTY, 3);
            }
            // Shooter: Meteora - Crossbow with multishot for AoE feel
            case "meteora" -> {
                item.addUnsafeEnchantment(Enchantment.MULTISHOT, 1);
                item.addUnsafeEnchantment(Enchantment.QUICK_CHARGE, 2);
            }
            // Attacker: Kogetsu - Sharpness V (high damage)
            case "kogetsu" -> {
                item.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
            }
            // Attacker: Scorpion - Sharpness III + faster attack
            case "scorpion" -> {
                item.addUnsafeEnchantment(Enchantment.SHARPNESS, 3);
            }
            // Attacker: Raygust - Moderate sharpness
            case "raygust" -> {
                item.addUnsafeEnchantment(Enchantment.SHARPNESS, 2);
                item.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
            }
        }
    }

    /**
     * Find a safe spawn location - on top of the highest block, not inside ground.
     */
    private Location getSafeSpawnLocation(World world, int x, int z) {
        // Get highest non-air block at this position
        int highestY = world.getHighestBlockYAt(x, z);
        Location loc = new Location(world, x + 0.5, highestY + 1.0, z + 0.5);

        // Ensure the two blocks above are air (player needs 2 blocks of space)
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
    public String getMapName() { return mapName; }
}
