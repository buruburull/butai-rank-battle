package com.borderrank.battle.command;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.manager.MapManager;
import com.borderrank.battle.manager.RankManager;
import com.borderrank.battle.manager.TriggerRegistry;
import com.borderrank.battle.model.BRBPlayer;
import com.borderrank.battle.model.MapData;
import com.borderrank.battle.util.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Command handler for /bradmin command.
 * Admin-only commands for server management.
 * Permission: brb.admin
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("brb.admin")) {
            MessageUtil.sendErrorMessage(sender, "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            MessageUtil.sendInfoMessage(sender, "Usage: /bradmin <trigger|forcestart|rp|season|map>");
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "trigger" -> handleTrigger(sender, args);
            case "forcestart" -> handleForceStart(sender);
            case "rp" -> handleRP(sender, args);
            case "season" -> handleSeason(sender, args);
            case "map" -> handleMap(sender, args);
            case "match" -> handleMatch(sender, args);
            default -> MessageUtil.sendErrorMessage(sender, "Unknown subcommand: " + subcommand);
        }

        return true;
    }

    /**
     * Handle /bradmin trigger command - reloads triggers.yml
     */
    private void handleTrigger(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendErrorMessage(sender, "Usage: /bradmin trigger <reload>");
            return;
        }

        if ("reload".equalsIgnoreCase(args[1])) {
            BRBPlugin plugin = BRBPlugin.getInstance();
            TriggerRegistry registry = plugin.getTriggerRegistry();

            if (registry.reloadTriggers()) {
                MessageUtil.sendSuccessMessage(sender, "Triggers reloaded!");
            } else {
                MessageUtil.sendErrorMessage(sender, "Failed to reload triggers.");
            }
        } else {
            MessageUtil.sendErrorMessage(sender, "Unknown trigger action: " + args[1]);
        }
    }

    /**
     * Handle /bradmin forcestart command - force starts a match with current queue.
     */
    private void handleForceStart(CommandSender sender) {
        BRBPlugin plugin = BRBPlugin.getInstance();

        if (plugin.getQueueManager().startMatch()) {
            MessageUtil.sendSuccessMessage(sender, "Match started!");
        } else {
            MessageUtil.sendErrorMessage(sender, "Not enough players in queue to start a match.");
        }
    }

    /**
     * Handle /bradmin rp command - manually set player RP.
     */
    private void handleRP(CommandSender sender, String[] args) {
        if (args.length < 4) {
            MessageUtil.sendErrorMessage(sender, "Usage: /bradmin rp set <player> <weapon> <value>");
            return;
        }

        if (!"set".equalsIgnoreCase(args[1])) {
            MessageUtil.sendErrorMessage(sender, "Unknown RP action: " + args[1]);
            return;
        }

        Player targetPlayer = Bukkit.getPlayer(args[2]);
        if (targetPlayer == null) {
            MessageUtil.sendErrorMessage(sender, "Player not found: " + args[2]);
            return;
        }

        String weaponType = args[3].toUpperCase();

        int value;
        try {
            value = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            MessageUtil.sendErrorMessage(sender, "Invalid RP value: " + args[4]);
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        rankManager.setPlayerRP(targetPlayer.getUniqueId(), weaponType, value);

        // Recalculate rank and update tab list name
        BRBPlayer brPlayer = rankManager.getPlayer(targetPlayer.getUniqueId());
        if (brPlayer != null) {
            rankManager.recalculateRank(brPlayer);
            rankManager.savePlayer(brPlayer);
        }

        MessageUtil.sendSuccessMessage(sender,
            "Set " + targetPlayer.getName() + "'s " + weaponType + " RP to " + value);
    }

    /**
     * Handle /bradmin season command - manages seasons.
     */
    private void handleSeason(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendErrorMessage(sender, "Usage: /bradmin season <start|end> [name]");
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();
        String action = args[1].toLowerCase();

        if ("start".equalsIgnoreCase(action)) {
            if (args.length < 3) {
                MessageUtil.sendErrorMessage(sender, "Usage: /bradmin season start <name>");
                return;
            }

            String seasonName = args[2];
            if (rankManager.getActiveSeason() != null) {
                MessageUtil.sendErrorMessage(sender, "シーズンがすでに進行中です。先に /bradmin season end で終了してください。");
                return;
            }
            if (rankManager.seasonNameExists(seasonName)) {
                MessageUtil.sendErrorMessage(sender, "シーズン名 '" + seasonName + "' は既に使用されています。別の名前を指定してください。");
                return;
            }
            if (rankManager.startSeason(seasonName)) {
                MessageUtil.sendSuccessMessage(sender, "シーズン '" + seasonName + "' を開始しました！");
                // Broadcast to all online players
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(net.kyori.adventure.text.Component.text("")
                        .append(net.kyori.adventure.text.Component.text("★ ", net.kyori.adventure.text.format.NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD))
                        .append(net.kyori.adventure.text.Component.text("新シーズン「" + seasonName + "」が開始されました！", net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                    );
                    p.sendMessage(net.kyori.adventure.text.Component.text("  全プレイヤーのRPがリセットされ、新たなランキング戦が始まります！", net.kyori.adventure.text.format.NamedTextColor.GREEN));
                }
            } else {
                MessageUtil.sendErrorMessage(sender, "シーズンの開始に失敗しました。");
            }
        } else if ("end".equalsIgnoreCase(action)) {
            com.borderrank.battle.model.Season currentSeason = rankManager.getActiveSeason();
            if (currentSeason == null) {
                MessageUtil.sendErrorMessage(sender, "進行中のシーズンがありません。");
                return;
            }
            String endedSeasonName = currentSeason.getName();
            if (rankManager.endSeason()) {
                MessageUtil.sendSuccessMessage(sender, "シーズン '" + endedSeasonName + "' を終了しました。全プレイヤーのRPをリセットしました。");
                // Broadcast to all online players
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(net.kyori.adventure.text.Component.text("")
                        .append(net.kyori.adventure.text.Component.text("★ ", net.kyori.adventure.text.format.NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.BOLD))
                        .append(net.kyori.adventure.text.Component.text("シーズン「" + endedSeasonName + "」が終了しました！", net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                    );
                    p.sendMessage(net.kyori.adventure.text.Component.text("  全プレイヤーのRPが1000にリセットされました。お疲れ様でした！", net.kyori.adventure.text.format.NamedTextColor.AQUA));
                }
            } else {
                MessageUtil.sendErrorMessage(sender, "シーズンの終了に失敗しました。");
            }
        } else if ("info".equalsIgnoreCase(action)) {
            com.borderrank.battle.model.Season activeSeason = rankManager.getActiveSeason();
            if (activeSeason == null) {
                MessageUtil.sendInfoMessage(sender, "現在進行中のシーズンはありません。");
                MessageUtil.sendInfoMessage(sender, "§7/bradmin season start <name> で新シーズンを開始できます。");
                return;
            }
            java.time.LocalDateTime startDate = activeSeason.getStartDate();
            long daysSinceStart = java.time.temporal.ChronoUnit.DAYS.between(startDate.toLocalDate(), java.time.LocalDate.now());
            MessageUtil.sendInfoMessage(sender, "§8§m----------§r §e§lシーズン情報 §8§m----------");
            MessageUtil.sendInfoMessage(sender, "§e◆ シーズン名: §b" + activeSeason.getName());
            MessageUtil.sendInfoMessage(sender, "§e◆ 開始日: §f" + startDate.toLocalDate().toString());
            MessageUtil.sendInfoMessage(sender, "§e◆ 経過日数: §f" + daysSinceStart + "日");
            MessageUtil.sendInfoMessage(sender, "§e◆ ステータス: §a進行中");
            MessageUtil.sendInfoMessage(sender, "§8§m--------------------------------------");
        } else {
            MessageUtil.sendErrorMessage(sender, "Unknown season action: " + action);
        }
    }

    /**
     * Handle /bradmin map command - view and manage maps.
     */
    private void handleMap(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendErrorMessage(sender, "Usage: /bradmin map <list|reset|reload>");
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        MapManager mapManager = plugin.getMapManager();
        String action = args[1].toLowerCase();

        switch (action) {
            case "list" -> {
                MessageUtil.sendInfoMessage(sender, "=== マップ一覧 ===");
                MessageUtil.sendInfoMessage(sender, "全 " + mapManager.getMapCount() + " マップ / 空き " + mapManager.getAvailableCount());
                for (MapData map : mapManager.getAllMaps()) {
                    String status = map.isInUse()
                        ? ChatColor.RED + "使用中"
                        : ChatColor.GREEN + "空き";
                    MessageUtil.sendInfoMessage(sender,
                        " - " + ChatColor.WHITE + map.getDisplayName()
                        + ChatColor.GRAY + " (" + map.getMapId() + ") "
                        + status
                        + ChatColor.GRAY + " [スポーン: " + map.getSpawnPointCount() + "]");
                }
                if (mapManager.getMapCount() == 0) {
                    MessageUtil.sendMessage(sender, ChatColor.YELLOW + "マップが定義されていません。config/triggers.yml の maps セクションを確認してください。");
                }
            }
            case "reset" -> {
                mapManager.resetMapStates();
                MessageUtil.sendSuccessMessage(sender, "全マップの状態をリセットしました。");
            }
            case "reload" -> {
                mapManager.reloadMaps();
                MessageUtil.sendSuccessMessage(sender, "マップを再読み込みしました。" + mapManager.getMapCount() + " マップ検出。");
            }
            default -> MessageUtil.sendErrorMessage(sender, "Unknown map action: " + action);
        }
    }

    /**
     * Handle /bradmin match command - view match history and stats.
     */
    private void handleMatch(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendErrorMessage(sender, "Usage: /bradmin match <recent|stats>");
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        com.borderrank.battle.database.MatchDAO matchDAO = plugin.getMatchDAO();
        if (matchDAO == null) {
            MessageUtil.sendErrorMessage(sender, "マッチ履歴システムが利用できません。");
            return;
        }

        String action = args[1].toLowerCase();

        if ("recent".equals(action)) {
            // Async DB query
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    java.util.List<com.borderrank.battle.database.MatchDAO.RecentMatch> matches = matchDAO.getRecentMatches(10);

                    // Fetch participants for each match
                    java.util.List<Integer> matchIds = new java.util.ArrayList<>();
                    for (com.borderrank.battle.database.MatchDAO.RecentMatch m : matches) {
                        matchIds.add(m.getMatchId());
                    }
                    java.util.Map<Integer, java.util.List<com.borderrank.battle.database.MatchDAO.MatchParticipant>> participantsMap = matchDAO.getMatchParticipants(matchIds);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        MessageUtil.sendInfoMessage(sender, "§8§m----------§r §e§l最近のマッチ §8§m----------");
                        if (matches.isEmpty()) {
                            MessageUtil.sendInfoMessage(sender, "§7マッチ履歴がありません。");
                        } else {
                            for (com.borderrank.battle.database.MatchDAO.RecentMatch m : matches) {
                                String typeTag = "team".equals(m.getMatchType()) ? "§d[T]" : "§e[S]";
                                int mins = m.getDurationSec() / 60;
                                int secs = m.getDurationSec() % 60;
                                // Time ago
                                String timeAgo = "";
                                if (m.getStartedAt() != null) {
                                    long diffMin = (System.currentTimeMillis() - m.getStartedAt().getTime()) / (1000 * 60);
                                    if (diffMin < 60) timeAgo = diffMin + "分前";
                                    else if (diffMin < 1440) timeAgo = (diffMin / 60) + "時間前";
                                    else timeAgo = (diffMin / 1440) + "日前";
                                }

                                // Build participant names
                                String playerNames = "";
                                java.util.List<com.borderrank.battle.database.MatchDAO.MatchParticipant> participants = participantsMap.get(m.getMatchId());
                                if (participants != null && !participants.isEmpty()) {
                                    StringBuilder sb = new StringBuilder();
                                    for (int i = 0; i < participants.size(); i++) {
                                        com.borderrank.battle.database.MatchDAO.MatchParticipant p = participants.get(i);
                                        if (i > 0) sb.append(" §7vs ");
                                        String pColor = p.getPlacement() == 1 ? "§6" : "§f";
                                        sb.append(pColor).append(p.getPlayerName());
                                    }
                                    playerNames = sb.toString();
                                }

                                MessageUtil.sendInfoMessage(sender,
                                    "§f#" + m.getMatchId() + " " + typeTag
                                    + " §7| §f" + m.getMapName()
                                    + " §7| §f" + mins + ":" + String.format("%02d", secs)
                                    + " §8(" + timeAgo + ")");
                                if (!playerNames.isEmpty()) {
                                    MessageUtil.sendInfoMessage(sender,
                                        "  §7→ " + playerNames);
                                }
                            }
                        }
                        MessageUtil.sendInfoMessage(sender, "§8§m--------------------------------------");
                    });
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                        MessageUtil.sendErrorMessage(sender, "マッチ履歴の取得に失敗しました。"));
                    e.printStackTrace();
                }
            });
        } else if ("stats".equals(action)) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    com.borderrank.battle.database.MatchDAO.MatchStats stats = matchDAO.getMatchStats();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        MessageUtil.sendInfoMessage(sender, "§8§m----------§r §e§lマッチ統計 §8§m----------");
                        MessageUtil.sendInfoMessage(sender, "§e◆ 総マッチ数: §f" + stats.getTotalMatches());
                        MessageUtil.sendInfoMessage(sender, "§e◆ ソロ: §f" + stats.getSoloMatches() + " §7| §eチーム: §f" + stats.getTeamMatches());
                        int avgMin = stats.getAvgDurationSec() / 60;
                        int avgSec = stats.getAvgDurationSec() % 60;
                        MessageUtil.sendInfoMessage(sender, "§e◆ 平均試合時間: §f" + avgMin + ":" + String.format("%02d", avgSec));
                        MessageUtil.sendInfoMessage(sender, "§8§m--------------------------------------");
                    });
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                        MessageUtil.sendErrorMessage(sender, "統計の取得に失敗しました。"));
                    e.printStackTrace();
                }
            });
        } else {
            MessageUtil.sendErrorMessage(sender, "Unknown match action: " + action);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("brb.admin")) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("trigger");
            completions.add("forcestart");
            completions.add("rp");
            completions.add("season");
            completions.add("map");
            completions.add("match");
        } else if (args.length == 2) {
            if ("trigger".equalsIgnoreCase(args[0])) {
                completions.add("reload");
            } else if ("rp".equalsIgnoreCase(args[0])) {
                completions.add("set");
            } else if ("season".equalsIgnoreCase(args[0])) {
                completions.add("start");
                completions.add("end");
                completions.add("info");
            } else if ("map".equalsIgnoreCase(args[0])) {
                completions.add("list");
                completions.add("reset");
                completions.add("reload");
            } else if ("match".equalsIgnoreCase(args[0])) {
                completions.add("recent");
                completions.add("stats");
            }
        } else if (args.length == 3) {
            if ("rp".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[1])) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 4) {
            if ("rp".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[1])) {
                completions.add("ATTACKER");
                completions.add("SHOOTER");
                completions.add("SNIPER");
            }
        }

        return completions;
    }
}
