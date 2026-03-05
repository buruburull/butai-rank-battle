package com.borderrank.battle.manager;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.arena.ArenaInstance;
import com.borderrank.battle.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class TrionManager {

    private final Map<UUID, Double> currentTrion = new HashMap<>();
    private final Map<UUID, Integer> maxTrion = new HashMap<>();
    private final Map<UUID, Set<String>> activeSustainTriggers = new HashMap<>();
    private final Set<UUID> bailedOut = new HashSet<>();
    private BukkitTask tickTask;

    private static final double HP_LEAK_COEFFICIENT = 0.5;
    private static final int WARNING_THRESHOLD_HIGH = 200;
    private static final int WARNING_THRESHOLD_CRITICAL = 100;

    public void initPlayer(UUID playerId, int max) {
        maxTrion.put(playerId, max);
        currentTrion.put(playerId, (double) max);
        activeSustainTriggers.put(playerId, new HashSet<>());
        bailedOut.remove(playerId);
    }

    public boolean consumeTrion(UUID playerId, double amount) {
        Double current = currentTrion.get(playerId);
        if (current == null || current < amount) return false;
        currentTrion.put(playerId, current - amount);
        return true;
    }

    public void addTrion(UUID playerId, double amount) {
        Double current = currentTrion.getOrDefault(playerId, 0.0);
        Integer max = maxTrion.get(playerId);
        if (max == null) return;
        currentTrion.put(playerId, Math.min(current + amount, max));
    }

    public double getTrion(UUID playerId) {
        return currentTrion.getOrDefault(playerId, 0.0);
    }

    public int getMaxTrion(UUID playerId) {
        return maxTrion.getOrDefault(playerId, 0);
    }

    public void activateSustain(UUID playerId, String triggerId) {
        activeSustainTriggers.computeIfAbsent(playerId, k -> new HashSet<>()).add(triggerId);
    }

    public void deactivateSustain(UUID playerId, String triggerId) {
        var sustains = activeSustainTriggers.get(playerId);
        if (sustains != null) sustains.remove(triggerId);
    }

    public void startTickLoop(Plugin plugin, Set<UUID> activePlayers) {
        stopTickLoop();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID playerId : new ArrayList<>(activePlayers)) {
                if (bailedOut.contains(playerId)) continue;
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline() || player.isDead()) continue;

                Integer maxTrionValue = maxTrion.get(playerId);
                if (maxTrionValue == null) continue;

                double currentValue = currentTrion.getOrDefault(playerId, (double) maxTrionValue);

                double maxHealth = player.getMaxHealth();
                double currentHealth = player.getHealth();
                double hpLeak = (maxHealth - currentHealth) * HP_LEAK_COEFFICIENT;

                var sustains = activeSustainTriggers.getOrDefault(playerId, new HashSet<>());
                double sustainCost = sustains.size() * 1.0;

                double newTrion = Math.max(0, currentValue - hpLeak - sustainCost);
                currentTrion.put(playerId, newTrion);

                updateXPBar(player, newTrion, maxTrionValue);
                showTrionWarning(player, newTrion, maxTrionValue);

                if (newTrion <= 0) {
                    triggerBailout(player);
                }
            }
        }, 20L, 20L);
    }

    public void stopTickLoop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    public void triggerBailout(Player player) {
        UUID uuid = player.getUniqueId();
        if (bailedOut.contains(uuid)) return;
        bailedOut.add(uuid);
        removePlayer(uuid);

        Bukkit.broadcastMessage("\u00A7c[BAILOUT] " + player.getName() + " \u30C8\u30EA\u30AA\u30F3\u5207\u308C\uFF01");

        // Teleport to hub
        player.teleport(player.getWorld().getSpawnLocation());
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.getInventory().clear();
        player.setLevel(0);
        player.setExp(0);
        player.sendMessage("\u00A7e\u30C8\u30EA\u30AA\u30F3\u304C\u5C3D\u304D\u307E\u3057\u305F\u3002\u30ED\u30D3\u30FC\u306B\u623B\u308A\u307E\u3057\u305F\u3002");

        // Notify match about this elimination
        BRBPlugin plugin = BRBPlugin.getInstance();
        ArenaInstance match = plugin.getMatchManager().getPlayerMatch(uuid);
        if (match != null) {
            match.onKill(null, uuid);
        }
    }

    public void updateXPBar(Player player, double trion, int maxTrion) {
        player.setLevel((int) Math.ceil(trion));
        if (maxTrion > 0) {
            player.setExp(Math.max(0, Math.min(0.999f, (float)(trion / maxTrion))));
        }
    }

    private void showTrionWarning(Player player, double trion, int maxTrion) {
        if (trion < WARNING_THRESHOLD_CRITICAL) {
            String blinking = System.currentTimeMillis() % 500 < 250 ? "\u00A74" : "\u00A7c";
            player.sendActionBar(blinking + "\u26A0 CRITICAL: \u30C8\u30EA\u30AA\u30F3\u6B8B\u308A\u308F\u305A\u304B\uFF01 \u26A0");
        } else if (trion < WARNING_THRESHOLD_HIGH) {
            player.sendActionBar("\u00A7e\u26A0 \u30C8\u30EA\u30AA\u30F3\u4F4E\u4E0B: " + (int) trion + " / " + maxTrion);
        }
    }

    public boolean hasBailedOut(UUID playerId) { return bailedOut.contains(playerId); }
    public boolean isPlayerInitialized(UUID playerId) { return currentTrion.containsKey(playerId); }

    public void removePlayer(UUID playerId) {
        currentTrion.remove(playerId);
        maxTrion.remove(playerId);
        activeSustainTriggers.remove(playerId);
    }

    public void clearAll() {
        currentTrion.clear();
        maxTrion.clear();
        activeSustainTriggers.clear();
        bailedOut.clear();
        stopTickLoop();
    }

    public int getActiveSustainCount(UUID playerId) {
        var sustains = activeSustainTriggers.get(playerId);
        return sustains != null ? sustains.size() : 0;
    }
}
