package com.butai.rankbattle.listener;

import com.butai.rankbattle.BRBPlugin;
import com.butai.rankbattle.command.FrameCommand;
import com.butai.rankbattle.manager.FrameSetManager;
import com.butai.rankbattle.manager.RankManager;
import com.butai.rankbattle.model.BRBPlayer;
import com.butai.rankbattle.util.MessageUtil;
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

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    frameCommand.refreshHotbar(player);
                    MessageUtil.send(player, "§6BUTAI Rank Battle §7へようこそ！");
                    MessageUtil.sendInfo(player, "ランク: " + brbPlayer.getRankClass().getColoredName()
                            + " §8| §7総合RP: §f" + brbPlayer.getTotalRP());
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Handle match disconnect (queue removal + E-Shift in match)
        if (plugin.getQueueManager() != null) {
            plugin.getQueueManager().handleDisconnect(player.getUniqueId());
        }

        // Save frameset async, then unload
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            frameSetManager.unloadPlayer(player.getUniqueId());
        });
        rankManager.unloadPlayer(player.getUniqueId());
    }
}
