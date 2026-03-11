package com.butai.rankbattle.listener;

import com.butai.rankbattle.BRBPlugin;
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

    public PlayerConnectionListener(BRBPlugin plugin, RankManager rankManager, FrameSetManager frameSetManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        this.frameSetManager = frameSetManager;
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
        // TODO: 試合中の切断処理（E-Shift）はここに追加

        // Save frameset async, then unload
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            frameSetManager.unloadPlayer(player.getUniqueId());
        });
        rankManager.unloadPlayer(player.getUniqueId());
    }
}
