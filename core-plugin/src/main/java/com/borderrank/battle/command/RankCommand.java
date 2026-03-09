package com.borderrank.battle.command;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.database.MatchDAO;
import com.borderrank.battle.manager.RankManager;
import com.borderrank.battle.model.BRBPlayer;
import com.borderrank.battle.model.Season;
import com.borderrank.battle.model.WeaponRP;
import com.borderrank.battle.model.WeaponType;
import com.borderrank.battle.model.Team;
import com.borderrank.battle.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command handler for /rank command.
 * Allows players to manage their ranking and view statistics.
 */
public class RankCommand implements CommandExecutor, TabCompleter {

    // Rank color codes
    private static final String S_COLOR = "\u00a76\u00a7l"; // Gold bold
    private static final String A_COLOR = "\u00a7c";         // Red
    private static final String B_COLOR = "\u00a7b";         // Aqua
    private static final String C_COLOR = "\u00a7a";         // Green
    private static final String D_COLOR = "\u00a77";         // Gray

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendErrorMessage(sender, "This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            MessageUtil.sendInfoMessage(player, "Usage: /rank <solo|team|cancel|stats|top>");
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "solo" -> handleSolo(player);
            case "team" -> handleTeam(player);
            case "cancel" -> handleCancel(player);
            case "stats" -> handleStats(player, args);
            case "top" -> handleTop(player, args);
            case "history" -> handleHistory(player, args);
            default -> MessageUtil.sendErrorMessage(player, "Unknown subcommand: " + subcommand);
        }

        return true;
    }

    /**
     * Handle /rank solo command - adds player to solo queue.
     */
    private void handleSolo(Player player) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        if (!plugin.getQueueManager().isInQueue(player.getUniqueId())) {
            plugin.getQueueManager().addToSoloQueue(player.getUniqueId());
            MessageUtil.sendSuccessMessage(player, "\u00a7eソロキュー\u00a7aに参加しました！対戦相手を待っています...");
        } else {
            MessageUtil.sendErrorMessage(player, "既にキューに参加しています！");
        }
    }

    /**
     * Handle /rank team command - adds team to team queue.
     */
    private void handleTeam(Player player) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        Team team = rankManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            MessageUtil.sendErrorMessage(player, "チームに所属していません！");
            return;
        }

        if (!team.getLeaderId().equals(player.getUniqueId())) {
            MessageUtil.sendErrorMessage(player, "チームキューはリーダーのみ登録できます！");
            return;
        }

        if (team.size() < 2) {
            MessageUtil.sendErrorMessage(player, "チームには最低2人必要です！");
            return;
        }

        // Check all members online
        for (UUID memberId : team.getMembers()) {
            if (Bukkit.getPlayer(memberId) == null) {
                MessageUtil.sendErrorMessage(player, "チームメンバー全員がオンラインである必要があります！");
                return;
            }
        }

        plugin.getQueueManager().addToTeamQueue(new java.util.HashSet<>(team.getMembers()));
        MessageUtil.sendSuccessMessage(player, "\u00a7bチームキュー\u00a7aに参加しました！対戦チームを待っています...");

        // Notify team members
        for (UUID memberId : team.getMembers()) {
            if (!memberId.equals(player.getUniqueId())) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    MessageUtil.sendInfoMessage(member, "\u00a7eチームがランクマッチキューに参加しました！");
                }
            }
        }
    }

    /**
     * Handle /rank cancel command - removes player from queue.
     */
    private void handleCancel(Player player) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        if (plugin.getQueueManager().removePlayer(player.getUniqueId())) {
            MessageUtil.sendSuccessMessage(player, "キューから離脱しました。");
        } else {
            MessageUtil.sendErrorMessage(player, "キューに参加していません！");
        }
    }

    /**
     * Handle /rank stats command - shows player's ranking points by weapon type.
     */
    private void handleStats(Player player, String[] args) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        Player targetPlayer = player;

        if (args.length > 1) {
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                MessageUtil.sendErrorMessage(player, "\u00a7cプレイヤーが見つかりません: " + args[1]);
                return;
            }
        }

        RankManager rankManager = plugin.getRankManager();
        BRBPlayer brPlayer = rankManager.getPlayer(targetPlayer.getUniqueId());

        if (brPlayer == null) {
            MessageUtil.sendErrorMessage(player, "データが見つかりません: " + targetPlayer.getName());
            return;
        }

        String rankTier = rankManager.getHighestRankTier(brPlayer);
        String rankColor = getRankColor(rankTier);

        // Header
        MessageUtil.sendInfoMessage(player, "\u00a78\u00a7m----------\u00a7r \u00a7b" + targetPlayer.getName() + " \u00a7fのステータス \u00a78\u00a7m----------");

        Season activeSeason = rankManager.getActiveSeason();
        if (activeSeason != null) {
            java.time.LocalDateTime startDate = activeSeason.getStartDate();
            long daysSinceStart = java.time.temporal.ChronoUnit.DAYS.between(startDate.toLocalDate(), java.time.LocalDate.now());
            MessageUtil.sendInfoMessage(player, "\u00a7e\u25c6 シーズン: \u00a7b" + activeSeason.getName()
                + " \u00a77(" + startDate.toLocalDate() + "～ / " + daysSinceStart + "日目)");
        } else {
            MessageUtil.sendInfoMessage(player, "\u00a7e\u25c6 シーズン: \u00a77シーズンなし");
        }

        MessageUtil.sendInfoMessage(player, "\u00a7e\u25c6 総合ランク: " + rankColor + rankTier + "\u00a7eランク");
        MessageUtil.sendInfoMessage(player, "\u00a7e\u25c6 総合RP: \u00a7f" + brPlayer.getTotalRP() + " RP");

        int totalWins = brPlayer.getTotalWins();
        int totalLosses = brPlayer.getTotalLosses();
        int totalGames = totalWins + totalLosses;
        String winRate = totalGames > 0 ? String.format("%.1f%%", (double) totalWins / totalGames * 100) : "-";
        MessageUtil.sendInfoMessage(player, "\u00a7e\u25c6 総合戦績: \u00a7a" + totalWins + "\u00a7f勝 \u00a7c" + totalLosses + "\u00a7f敗 \u00a77(" + winRate + ")");

        // Weapon stats
        MessageUtil.sendInfoMessage(player, "");
        MessageUtil.sendInfoMessage(player, "\u00a7e\u25b6 武器別ステータス:");

        for (WeaponType wt : WeaponType.values()) {
            WeaponRP wrp = brPlayer.getWeaponRP(wt);
            if (wrp != null) {
                int rp = wrp.getRp();
                int wins = wrp.getWins();
                int losses = wrp.getLosses();
                int games = wins + losses;
                String weaponWinRate = games > 0 ? String.format("%.1f%%", (double) wins / games * 100) : "-";
                String weaponRankColor = getRankColorByRP(rp);

                MessageUtil.sendInfoMessage(player, "  \u00a7f" + getWeaponDisplayName(wt) + ": "
                    + weaponRankColor + rp + " RP \u00a77| \u00a7a" + wins + "\u00a77勝 \u00a7c" + losses + "\u00a77敗 \u00a78(" + weaponWinRate + ")");
            }
        }

        // Team info
        Team team = rankManager.getPlayerTeam(targetPlayer.getUniqueId());
        if (team != null) {
            MessageUtil.sendInfoMessage(player, "");
            MessageUtil.sendInfoMessage(player, "\u00a7e\u25b6 チーム: \u00a7b" + team.getName() + " \u00a77(メンバー" + team.size() + "人)");
        }

        MessageUtil.sendInfoMessage(player, "\u00a78\u00a7m--------------------------------------");
    }

    /**
     * Handle /rank top command - shows top 10 ranking.
     */
    private void handleTop(Player player, String[] args) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        // Check if weapon type specified
        if (args.length > 1) {
            String weaponArg = args[1].toUpperCase();
            try {
                WeaponType weaponType = WeaponType.valueOf(weaponArg);
                showWeaponRanking(player, rankManager, weaponType);
                return;
            } catch (IllegalArgumentException e) {
                // Not a weapon type, show global
            }
        }

        // Global ranking
        List<BRBPlayer> topPlayers = rankManager.getGlobalTopPlayers(10);

        MessageUtil.sendInfoMessage(player, "\u00a78\u00a7m----------\u00a7r \u00a7e\u00a7l\u2606 総合ランキング TOP10 \u2606 \u00a78\u00a7m----------");

        if (topPlayers.isEmpty()) {
            MessageUtil.sendInfoMessage(player, "\u00a77ランキングデータがありません。");
        } else {
            int rank = 1;
            for (BRBPlayer brPlayer : topPlayers) {
                String rankTier = rankManager.getHighestRankTier(brPlayer);
                String rankColor = getRankColor(rankTier);
                String posColor = getPositionColor(rank);
                int totalRP = brPlayer.getTotalRP();
                int wins = brPlayer.getTotalWins();
                int losses = brPlayer.getTotalLosses();

                MessageUtil.sendInfoMessage(player, posColor + "#" + rank + " \u00a7f" + brPlayer.getPlayerName()
                    + " " + rankColor + rankTier + " \u00a77| \u00a7f" + totalRP + " RP \u00a77| \u00a7a" + wins + "\u00a77W \u00a7c" + losses + "\u00a77L");
                rank++;
            }
        }

        MessageUtil.sendInfoMessage(player, "");
        MessageUtil.sendInfoMessage(player, "\u00a77武器別: \u00a7a/rank top attacker \u00a77| \u00a7a/rank top shooter \u00a77| \u00a7a/rank top sniper");
        MessageUtil.sendInfoMessage(player, "\u00a78\u00a7m----------------------------------------------");
    }

    /**
     * Shows ranking for a specific weapon type.
     */
    private void showWeaponRanking(Player player, RankManager rankManager, WeaponType weaponType) {
        List<BRBPlayer> topPlayers = rankManager.getTopPlayers(weaponType, 10);

        String weaponName = getWeaponDisplayName(weaponType);
        MessageUtil.sendInfoMessage(player, "\u00a78\u00a7m----------\u00a7r \u00a7e\u00a7l\u2606 " + weaponName + " ランキング TOP10 \u2606 \u00a78\u00a7m----------");

        if (topPlayers.isEmpty()) {
            MessageUtil.sendInfoMessage(player, "\u00a77ランキングデータがありません。");
        } else {
            int rank = 1;
            for (BRBPlayer brPlayer : topPlayers) {
                WeaponRP wrp = brPlayer.getWeaponRP(weaponType);
                int rp = wrp != null ? wrp.getRp() : 0;
                int wins = wrp != null ? wrp.getWins() : 0;
                int losses = wrp != null ? wrp.getLosses() : 0;
                String posColor = getPositionColor(rank);
                String rpColor = getRankColorByRP(rp);

                MessageUtil.sendInfoMessage(player, posColor + "#" + rank + " \u00a7f" + brPlayer.getPlayerName()
                    + " " + rpColor + rp + " RP \u00a77| \u00a7a" + wins + "\u00a77W \u00a7c" + losses + "\u00a77L");
                rank++;
            }
        }

        MessageUtil.sendInfoMessage(player, "\u00a78\u00a7m----------------------------------------------");
    }

    /**
     * Gets the display name for a weapon type.
     */
    private String getWeaponDisplayName(WeaponType type) {
        return switch (type) {
            case ATTACKER -> "\u00a7c\u2694 Attacker";
            case SHOOTER -> "\u00a7e\u2738 Shooter";
            case SNIPER -> "\u00a7b\u2316 Sniper";
        };
    }

    /**
     * Gets the color code for a rank tier.
     */
    private String getRankColor(String rankTier) {
        return switch (rankTier) {
            case "S" -> S_COLOR;
            case "A" -> A_COLOR;
            case "B" -> B_COLOR;
            case "C" -> C_COLOR;
            default -> D_COLOR;
        };
    }

    /**
     * Gets the rank color based on RP value.
     */
    private String getRankColorByRP(int rp) {
        if (rp >= 5000) return S_COLOR;
        if (rp >= 3000) return A_COLOR;
        if (rp >= 1500) return B_COLOR;
        if (rp >= 500) return C_COLOR;
        return D_COLOR;
    }

    /**
     * Gets the color for ranking position.
     */
    private String getPositionColor(int position) {
        return switch (position) {
            case 1 -> "\u00a76\u00a7l"; // Gold bold
            case 2 -> "\u00a7f\u00a7l"; // White bold
            case 3 -> "\u00a76";         // Gold
            default -> "\u00a77";        // Gray
        };
    }

    /**
     * Handle /rank history [player] - shows recent match history.
     */
    private void handleHistory(Player player, String[] args) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        MatchDAO matchDAO = plugin.getMatchDAO();

        if (matchDAO == null) {
            MessageUtil.sendErrorMessage(player, "マッチ履歴システムが利用できません。");
            return;
        }

        Player targetPlayer = player;
        if (args.length > 1) {
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                MessageUtil.sendErrorMessage(player, "\u00a7cプレイヤーが見つかりません: " + args[1]);
                return;
            }
        }

        final Player target = targetPlayer;
        final Player viewer = player;

        // Async DB query
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<MatchDAO.MatchRecord> records = matchDAO.getPlayerHistory(target.getUniqueId(), 10);

                // Switch back to main thread for chat messages
                Bukkit.getScheduler().runTask(plugin, () -> {
                    MessageUtil.sendInfoMessage(viewer, "\u00a78\u00a7m----------\u00a7r \u00a7b" + target.getName() + " \u00a7fのマッチ履歴 \u00a78\u00a7m----------");

                    if (records.isEmpty()) {
                        MessageUtil.sendInfoMessage(viewer, "\u00a77マッチ履歴がありません。");
                    } else {
                        for (MatchDAO.MatchRecord rec : records) {
                            String rpColor = rec.getRpChange() >= 0 ? "\u00a7a+" : "\u00a7c";
                            String resultIcon = rec.getPlacement() == 1 ? "\u00a76\u2605" : "\u00a77\u2606";
                            String weaponName = getWeaponShortName(rec.getWeaponType());
                            String typeTag = rec.getMatchType().equals("team") ? "\u00a7d[T]" : "\u00a7e[S]";
                            int mins = rec.getDurationSec() / 60;
                            int secs = rec.getDurationSec() % 60;

                            MessageUtil.sendInfoMessage(viewer,
                                resultIcon + " " + typeTag + " \u00a7f#" + rec.getPlacement()
                                + " \u00a77| " + weaponName
                                + " \u00a77| \u00a7f" + rec.getKills() + "K/" + rec.getDeaths() + "D"
                                + " \u00a77| " + rpColor + rec.getRpChange() + " RP"
                                + " \u00a78(" + mins + ":" + String.format("%02d", secs) + ")");
                        }
                    }

                    MessageUtil.sendInfoMessage(viewer, "\u00a78\u00a7m--------------------------------------");
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    MessageUtil.sendErrorMessage(viewer, "マッチ履歴の取得に失敗しました。");
                });
                e.printStackTrace();
            }
        });
    }

    /**
     * Gets short weapon display name for history view.
     */
    private String getWeaponShortName(String weaponType) {
        return switch (weaponType) {
            case "ATTACKER" -> "\u00a7cATK";
            case "SHOOTER" -> "\u00a7eSHT";
            case "SNIPER" -> "\u00a7bSNP";
            default -> "\u00a77???";
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("solo");
            completions.add("team");
            completions.add("cancel");
            completions.add("stats");
            completions.add("top");
            completions.add("history");
        } else if (args.length == 2) {
            if ("stats".equalsIgnoreCase(args[0]) || "history".equalsIgnoreCase(args[0])) {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    completions.add(onlinePlayer.getName());
                }
            } else if ("top".equalsIgnoreCase(args[0])) {
                completions.add("attacker");
                completions.add("shooter");
                completions.add("sniper");
            }
        }

        return completions;
    }
}
