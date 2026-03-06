package com.borderrank.battle.listener;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.manager.RankManager;
import com.borderrank.battle.model.BRBPlayer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listener for chat events.
 * Adds rank prefix to player chat messages.
 * Format: [S級] PlayerName: message
 */
public class ChatListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        BRBPlayer brPlayer = rankManager.getPlayer(player.getUniqueId());
        Component rankPrefix = rankManager.buildRankPrefix(brPlayer);

        // Set custom chat renderer: [Rank] PlayerName: message
        event.renderer((source, sourceDisplayName, message, viewer) ->
                rankPrefix
                        .append(Component.text(source.getName(), NamedTextColor.WHITE))
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(message)
        );
    }
}
