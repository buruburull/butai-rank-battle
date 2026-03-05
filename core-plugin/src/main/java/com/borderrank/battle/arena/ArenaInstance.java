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
import org.bukkit.GameMode;
import org.bukkit.Location;
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
    private boolean trionTickStarted = false;

    public ArenaInstance(int matchId, String mapName, int timeLimitSec) {
        this.matchId = matchId;
        this.mapName = mapName;
        this.timeLimitSec = timeLimitSec;
        this.state = ArenaState.WAITING;
        this.players = new HashSet<>();
        this.alivePlayers = new HashSet<>();
        this.kills = new HashMap<>();
        this.playerWeaponTypes = new HashMap<>();
        this.startTime = System.currentTimeMillis();
        this.countdownRemaining = 10;
        this.lastTickTime = System.currentTimeMillis();
    }

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

            Location spawnLoc = player.getWorld().getHighestBlockAt(spawnIndex * 10, 0).getLocation().add(0.5, 1, 0.5);
            player.teleport(spawnLoc);
            player.setGameMode(GameMode.SURVIVAL);
            player.getInventory().clear();

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
                                lore.add(ChatColor.AQUA + "Trion: " + td.getTrionUse());
                                meta.setLore(lore);
                                item.setItemMeta(meta);
                            }
                            player.getInventory().setItem(i, item);
                        }
                    }
                }
                WeaponType wt = loadoutManager.getWeaponType(loadout, triggerRegistry);
                playerWeaponTypes.put(uuid, wt != null ? wt : WeaponType.ATTACKER);
            } else {
                playerWeaponTypes.put(uuid, WeaponType.ATTACKER);
            }

            trionManager.initPlayer(uuid, 1000);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            MessageUtil.sendMessage(player, ChatColor.YELLOW + "マッチ開始！カウントダウン: " + countdownRemaining);
            spawnIndex++;
        }

        trionManager.startTickLoop(plugin, alivePlayers);
        trionTickStarted = true;
    }

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

        BRBPlugin.getInstance().getTrionManager().removePlayer(victim);

        if (alivePlayers.size() <= 1) {
            end();
        }
    }

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
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                scoreboardManager.updatePlayerScore(player, kills.getOrDefault(uuid, 0));
            }
        }
        long elapsedSec = (System.currentTimeMillis() - startTime) / 1000;
        long remaining = timeLimitSec - elapsedSec;
        if (remaining == 60 || remaining == 30 || remaining == 10) {
            for (UUID uuid : alivePlayers) {
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

    public void end() {
        if (state == ArenaState.FINISHED || state == ArenaState.ENDING) return;
        state = ArenaState.ENDING;
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();
        TrionManager trionManager = plugin.getTrionManager();

        if (trionTickStarted) {
            trionManager.stopTickLoop();
            trionTickStarted = false;
        }

        List<Map.Entry<UUID, Integer>> sortedPlayers = new ArrayList<>(kills.entrySet());
        sortedPlayers.sort((a, b) -> {
            boolean aAlive = alivePlayers.contains(a.getKey());
            boolean bAlive = alivePlayers.contains(b.getKey());
            if (aAlive != bAlive) return bAlive ? 1 : -1;
            return Integer.compare(b.getValue(), a.getValue());
        });

        Location hub = Bukkit.getWorlds().get(0).getSpawnLocation();

        int placement = 1;
        for (Map.Entry<UUID, Integer> entry : sortedPlayers) {
            UUID uuid = entry.getKey();
            int playerKills = entry.getValue();
            boolean survived = alivePlayers.contains(uuid);

            int rpGain = rankManager.calculateTeamRP(placement, playerKills, survived);
            WeaponType wt = playerWeaponTypes.getOrDefault(uuid, WeaponType.ATTACKER);
            rankManager.addPlayerRP(uuid, wt.name(), rpGain);

            var brPlayer = rankManager.getPlayer(uuid);
            if (brPlayer != null) {
                var wrp = brPlayer.getWeaponRP(wt);
                if (wrp != null) {
                    if (placement == 1) wrp.setWins(wrp.getWins() + 1);
                    else wrp.setLosses(wrp.getLosses() + 1);
                }
                rankManager.savePlayer(brPlayer);
            }

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                String rpText = rpGain >= 0 ? (ChatColor.GREEN + "+" + rpGain) : (ChatColor.RED + "" + rpGain);
                MessageUtil.sendInfoMessage(player, "=== マッチ結果 ===");
                MessageUtil.sendInfoMessage(player, "順位: #" + placement + " | キル: " + playerKills + " | RP: " + rpText);

                // Only restore alive players (dead ones get restored on respawn)
                if (!player.isDead()) {
                    player.teleport(hub);
                    player.setGameMode(GameMode.SURVIVAL);
                    player.getInventory().clear();
                    player.setHealth(player.getMaxHealth());
                    player.setFoodLevel(20);
                    player.setLevel(0);
                    player.setExp(0);
                }
            }

            trionManager.removePlayer(uuid);
            placement++;
        }

        // Send rankings to all online players
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !player.isDead()) {
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

    public Set<UUID> getAlivePlayers() { return new HashSet<>(alivePlayers); }
    public void addPlayer(UUID uuid) { players.add(uuid); }
    public int getMatchId() { return matchId; }
    public ArenaState getState() { return state; }
    public Set<UUID> getPlayers() { return new HashSet<>(players); }
    public int getPlayerKills(UUID uuid) { return kills.getOrDefault(uuid, 0); }
    public String getMapName() { return mapName; }
}
