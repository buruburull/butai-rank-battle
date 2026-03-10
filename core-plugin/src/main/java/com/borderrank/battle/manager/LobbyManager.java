package com.borderrank.battle.manager;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.model.BRBPlayer;
import com.borderrank.battle.model.WeaponRP;
import com.borderrank.battle.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages lobby NPCs, holograms, and actionbar display.
 * All settings are loaded from triggers.yml lobby section.
 */
public class LobbyManager implements Listener {

    private final BRBPlugin plugin;
    private final NamespacedKey npcKey;
    private final NamespacedKey hologramKey;

    // Spawned entity tracking
    private final List<Entity> spawnedNpcs = new ArrayList<>();
    private final List<Entity> spawnedHolograms = new ArrayList<>();
    private final Map<UUID, String> npcActions = new HashMap<>(); // entity UUID -> action

    // Ranking hologram for auto-update
    private TextDisplay rankingHologram;

    // Actionbar config
    private boolean actionbarEnabled = false;
    private String actionbarFormat = "";
    private int actionbarInterval = 40;
    private int actionbarTaskId = -1;

    public LobbyManager(BRBPlugin plugin) {
        this.plugin = plugin;
        this.npcKey = new NamespacedKey(plugin, "brb_npc");
        this.hologramKey = new NamespacedKey(plugin, "brb_hologram");
    }

    /**
     * Load all lobby elements from triggers.yml and spawn them.
     */
    public void setup() {
        File triggersFile = plugin.getTriggerRegistry().getResolvedTriggersFile();
        if (triggersFile == null) {
            plugin.getLogger().warning("[LobbyManager] triggers.yml not found - lobby not loaded.");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(triggersFile);
        String worldName = config.getString("lobby.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[LobbyManager] Lobby world '" + worldName + "' not found.");
            return;
        }

        // Clean up old entities first
        cleanup();

        // Spawn NPCs
        ConfigurationSection npcsSection = config.getConfigurationSection("lobby.npcs");
        if (npcsSection != null) {
            for (String npcId : npcsSection.getKeys(false)) {
                spawnNpc(world, npcsSection.getConfigurationSection(npcId), npcId);
            }
        }

        // Spawn Holograms
        ConfigurationSection hologramsSection = config.getConfigurationSection("lobby.holograms");
        if (hologramsSection != null) {
            for (String holoId : hologramsSection.getKeys(false)) {
                spawnHologram(world, hologramsSection.getConfigurationSection(holoId), holoId);
            }
        }

        // Setup actionbar
        ConfigurationSection actionbarSection = config.getConfigurationSection("lobby.actionbar");
        if (actionbarSection != null) {
            actionbarEnabled = actionbarSection.getBoolean("enabled", false);
            actionbarFormat = actionbarSection.getString("format", "");
            actionbarInterval = actionbarSection.getInt("update_interval", 40);

            if (actionbarEnabled) {
                startActionbarTask();
            }
        }

        // Start ranking hologram update task (every 30 seconds)
        if (rankingHologram != null) {
            plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, this::updateRankingHologram, 40, 600);
        }

        plugin.getLogger().info("[LobbyManager] Lobby setup complete! NPCs: " + spawnedNpcs.size() +
            ", Holograms: " + spawnedHolograms.size() + ", Actionbar: " + actionbarEnabled);
    }

    /**
     * Spawn a villager NPC at the configured location.
     */
    private void spawnNpc(World world, ConfigurationSection section, String npcId) {
        if (section == null) return;

        Location loc = parseLocation(world, section.getString("location", "0,65,0"));
        if (loc == null) return;
        loc.setYaw((float) section.getDouble("yaw", 0.0));

        String name = section.getString("name", npcId);
        String subtitle = section.getString("subtitle", "");
        String action = section.getString("action", "");

        // Spawn villager NPC
        Villager villager = (Villager) world.spawnEntity(loc, EntityType.VILLAGER);
        villager.customName(LegacyComponentSerializer.legacySection().deserialize(name));
        villager.setCustomNameVisible(true);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setGravity(false);
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.setCollidable(false);
        villager.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, npcId);

        // Track NPC
        spawnedNpcs.add(villager);
        npcActions.put(villager.getUniqueId(), action);

        // Spawn subtitle hologram below NPC name
        if (!subtitle.isEmpty()) {
            Location subtitleLoc = loc.clone().add(0, -0.3, 0);
            TextDisplay display = (TextDisplay) world.spawnEntity(subtitleLoc, EntityType.TEXT_DISPLAY);
            display.text(LegacyComponentSerializer.legacySection().deserialize(subtitle));
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(false);
            display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            display.getPersistentDataContainer().set(hologramKey, PersistentDataType.STRING, npcId + "_subtitle");
            display.setPersistent(true);
            spawnedHolograms.add(display);
        }

        plugin.getLogger().info("[LobbyManager] NPC spawned: " + npcId + " (" + action + ")");
    }

    /**
     * Spawn a text hologram at the configured location.
     */
    private void spawnHologram(World world, ConfigurationSection section, String holoId) {
        if (section == null) return;

        Location loc = parseLocation(world, section.getString("location", "0,67,0"));
        if (loc == null) return;

        List<String> lines = section.getStringList("lines");
        String type = section.getString("type", "static");

        // Build multi-line text
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) text.append("\n");
            text.append(lines.get(i));
        }

        TextDisplay display = (TextDisplay) world.spawnEntity(loc, EntityType.TEXT_DISPLAY);
        display.text(LegacyComponentSerializer.legacySection().deserialize(text.toString()));
        display.setBillboard(Display.Billboard.CENTER);
        display.setShadowed(true);
        display.setBackgroundColor(org.bukkit.Color.fromARGB(100, 0, 0, 0));
        display.getPersistentDataContainer().set(hologramKey, PersistentDataType.STRING, holoId);
        display.setPersistent(true);

        spawnedHolograms.add(display);

        if ("ranking_top10".equals(type)) {
            rankingHologram = display;
        }

        plugin.getLogger().info("[LobbyManager] Hologram spawned: " + holoId + " (" + type + ")");
    }

    /**
     * Update the ranking hologram with current TOP10 data.
     */
    private void updateRankingHologram() {
        if (rankingHologram == null || rankingHologram.isDead()) return;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // Get top 10 from database
            List<BRBPlayer> topPlayers = plugin.getRankManager().getGlobalTopPlayers(10);

            StringBuilder text = new StringBuilder("§e§l🏆 ランキング TOP10\n");
            if (topPlayers.isEmpty()) {
                text.append("§7データなし");
            } else {
                for (int i = 0; i < topPlayers.size(); i++) {
                    BRBPlayer p = topPlayers.get(i);
                    String color;
                    if (i == 0) color = "§6"; // gold
                    else if (i == 1) color = "§f"; // white
                    else if (i == 2) color = "§7"; // gray
                    else color = "§8"; // dark gray

                    // Calculate overall RP
                    int totalRP = 0;
                    int count = 0;
                    for (WeaponRP wrp : p.getWeaponRPs().values()) {
                        totalRP += wrp.getRp();
                        count++;
                    }
                    int avgRP = count > 0 ? totalRP / count : 1000;

                    text.append(color).append("#").append(i + 1).append(" ")
                        .append(p.getPlayerName()).append(" §7- §f").append(avgRP).append("RP");
                    if (i < topPlayers.size() - 1) text.append("\n");
                }
            }

            // Update on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (rankingHologram != null && !rankingHologram.isDead()) {
                    rankingHologram.text(LegacyComponentSerializer.legacySection().deserialize(text.toString()));
                }
            });
        });
    }

    /**
     * Start the actionbar display task for lobby players.
     */
    private void startActionbarTask() {
        actionbarTaskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                // Only show to players in lobby (not in match, not spectating, not in queue)
                if (plugin.getMatchManager().getPlayerMatch(player.getUniqueId()) != null) continue;
                if (plugin.getMatchManager().isSpectating(player.getUniqueId())) continue;

                String message = buildActionbarMessage(player);
                if (!message.isEmpty()) {
                    player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(message));
                }
            }
        }, 20, actionbarInterval);
    }

    /**
     * Build actionbar message for a player with placeholder replacement.
     */
    private String buildActionbarMessage(Player player) {
        BRBPlayer brPlayer = plugin.getRankManager().getCachedPlayer(player.getUniqueId());
        if (brPlayer == null) return "";

        String msg = actionbarFormat;

        // Replace placeholders
        msg = msg.replace("{rank}", brPlayer.getRankClass().name());

        // Calculate average RP
        int totalRP = 0;
        int count = 0;
        for (WeaponRP wrp : brPlayer.getWeaponRPs().values()) {
            totalRP += wrp.getRp();
            count++;
        }
        int avgRP = count > 0 ? totalRP / count : 1000;
        msg = msg.replace("{rp}", String.valueOf(avgRP));

        // Queue status
        QueueManager qm = plugin.getQueueManager();
        String queueStatus;
        if (qm.isInQueue(player.getUniqueId())) {
            queueStatus = "§eキュー待ち中...";
        } else {
            queueStatus = "§7待機中";
        }
        msg = msg.replace("{queue_status}", queueStatus);

        return msg;
    }

    /**
     * Handle NPC right-click interactions.
     */
    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        String action = npcActions.get(entity.getUniqueId());
        if (action == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        switch (action) {
            case "rank_solo" -> player.performCommand("rank solo");
            case "rank_practice" -> player.performCommand("rank practice");
            case "trigger_view" -> player.performCommand("trigger view");
            case "rank_stats" -> player.performCommand("rank stats");
            case "rank_top" -> player.performCommand("rank top");
            default -> MessageUtil.sendMessage(player, "§c不明なアクション: " + action);
        }
    }

    /**
     * Prevent NPC damage.
     */
    @EventHandler
    public void onNpcDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    /**
     * Parse "x,y,z" string to Location.
     */
    private Location parseLocation(World world, String locationStr) {
        if (locationStr == null) return null;
        String[] parts = locationStr.split(",");
        if (parts.length < 3) return null;
        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("[LobbyManager] Invalid location: " + locationStr);
            return null;
        }
    }

    /**
     * Clean up all spawned lobby entities.
     */
    public void cleanup() {
        for (Entity entity : spawnedNpcs) {
            if (entity != null && !entity.isDead()) entity.remove();
        }
        for (Entity entity : spawnedHolograms) {
            if (entity != null && !entity.isDead()) entity.remove();
        }
        spawnedNpcs.clear();
        spawnedHolograms.clear();
        npcActions.clear();
        rankingHologram = null;

        if (actionbarTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(actionbarTaskId);
            actionbarTaskId = -1;
        }

        // Also remove any orphaned BRB entities from previous sessions
        World world = Bukkit.getWorld("world");
        if (world != null) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING) ||
                    entity.getPersistentDataContainer().has(hologramKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }
}
