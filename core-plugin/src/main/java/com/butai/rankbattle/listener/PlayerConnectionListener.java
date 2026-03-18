package com.butai.rankbattle.listener;

import com.butai.rankbattle.BRBPlugin;
import com.butai.rankbattle.command.FrameCommand;
import com.butai.rankbattle.manager.FrameSetManager;
import com.butai.rankbattle.manager.LobbyManager;
import com.butai.rankbattle.manager.RankManager;
import com.butai.rankbattle.model.BRBPlayer;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final BRBPlugin plugin;
    private final RankManager rankManager;
    private final FrameSetManager frameSetManager;
    private final FrameCommand frameCommand;

    public PlayerConnectionListener(BRBPlugin plugin, RankManager rankManager,
                                    FrameSetManager frameSetManager, FrameCommand frameCommand) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        this.frameSetManager = frameSetManager;
        this.frameCommand = frameCommand;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load player data async, then send welcome on main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            BRBPlayer brbPlayer = rankManager.loadPlayer(player.getUniqueId(), player.getName());
            frameSetManager.loadPlayer(player.getUniqueId());
            if (plugin.getEtherGrowthManager() != null) {
                plugin.getEtherGrowthManager().loadPlayer(player.getUniqueId());
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    // Teleport to lobby and set ADVENTURE mode
                    LobbyManager lobbyManager = plugin.getLobbyManager();
                    if (lobbyManager != null) {
                        lobbyManager.sendToLobby(player);
                    } else {
                        player.setGameMode(GameMode.ADVENTURE);
                    }

                    frameCommand.refreshHotbar(player);
                    MessageUtil.send(player, "§6BUTAI Rank Battle §7へようこそ！");
                    MessageUtil.sendInfo(player, "ランク: " + brbPlayer.getRankClass().getColoredName()
                            + " §8| §7総合RP: §f" + brbPlayer.getTotalRP());

                    // Show disconnect penalty/warning if applicable
                    if (plugin.getQueueManager() != null) {
                        String reconnectMsg = plugin.getQueueManager().getDisconnectTracker()
                                .getReconnectMessage(player.getUniqueId());
                        if (reconnectMsg != null) {
                            MessageUtil.send(player, reconnectMsg);
                        }
                    }
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Handle spectator disconnect
        if (plugin.getQueueManager() != null) {
            plugin.getQueueManager().handleSpectatorDisconnect(player.getUniqueId());
        }

        // Handle match disconnect (queue removal + E-Shift in match + penalty tracking)
        if (plugin.getQueueManager() != null) {
            String penaltyMsg = plugin.getQueueManager().handleDisconnect(player.getUniqueId());
            if (penaltyMsg != null) {
                plugin.getLogger().info("Disconnect penalty for " + player.getName() + ": " + penaltyMsg);
            }
        }

        // Save frameset and growth data async, then unload
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            frameSetManager.unloadPlayer(player.getUniqueId());
            if (plugin.getEtherGrowthManager() != null) {
                plugin.getEtherGrowthManager().unloadPlayer(player.getUniqueId());
            }
        });
        rankManager.unloadPlayer(player.getUniqueId());
    }
}
