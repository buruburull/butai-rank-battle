package com.butai.rankbattle.manager;

import com.butai.rankbattle.database.PlayerDAO;
import com.butai.rankbattle.database.TeamDAO;
import com.butai.rankbattle.model.BRBPlayer;
import com.butai.rankbattle.model.RankClass;
import com.butai.rankbattle.model.Team;
import com.butai.rankbattle.model.WeaponRP;
import com.butai.rankbattle.model.WeaponType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class RankManager {

    private final PlayerDAO playerDAO;
    private final Logger logger;
    private final Map<UUID, BRBPlayer> playerCache = new ConcurrentHashMap<>();

    // Team management
    private TeamDAO teamDAO;
    private final Map<UUID, Team> playerTeamCache = new ConcurrentHashMap<>();
    // pendingInvites: invitee UUID -> Team
    private final Map<UUID, Team> pendingInvites = new ConcurrentHashMap<>();

    public RankManager(PlayerDAO playerDAO, Logger logger) {
        this.playerDAO = playerDAO;
        this.logger = logger;
    }

    public void setTeamDAO(TeamDAO teamDAO) {
        this.teamDAO = teamDAO;
    }

    // ==================== Player Management ====================

    /**
     * Load player data from DB into cache. Called on join.
     */
    public BRBPlayer loadPlayer(UUID uuid, String name) {
        // Register or update in DB
        playerDAO.registerOrUpdate(uuid, name);
        playerDAO.initializeWeaponRP(uuid);

        // Build BRBPlayer from DB
        BRBPlayer player = new BRBPlayer(uuid, name);

        // Load rank class
        String rankStr = playerDAO.getRankClass(uuid);
        player.setRankClass(RankClass.fromString(rankStr));

        // Load weapon RPs
        Map<String, Integer> rpMap = playerDAO.getAllWeaponRP(uuid);
        for (WeaponType type : WeaponType.values()) {
            int rp = rpMap.getOrDefault(type.name(), 1000);
            int[] winLoss = playerDAO.getWinLoss(uuid, type.name());
            WeaponRP wrp = new WeaponRP(type, rp, winLoss[0], winLoss[1]);
            player.getWeaponRPs().put(type, wrp);
        }

        // Recalculate rank and sync to DB if changed
        if (player.recalculateRank()) {
            playerDAO.updateRankClass(uuid, player.getRankClass().name());
        }

        playerCache.put(uuid, player);

        // Load team data
        if (teamDAO != null) {
            Team team = teamDAO.loadTeamByPlayer(uuid);
            if (team != null) {
                playerTeamCache.put(uuid, team);
            }
        }

        logger.info("Loaded player: " + name + " [" + player.getRankClass().getDisplayName() + " / RP:" + player.getTotalRP() + "]");
        return player;
    }

    /**
     * Unload player from cache. Called on quit.
     */
    public void unloadPlayer(UUID uuid) {
        playerCache.remove(uuid);
        playerTeamCache.remove(uuid);
        pendingInvites.remove(uuid);
    }

    /**
     * Get cached player data. Returns null if not loaded.
     */
    public BRBPlayer getPlayer(UUID uuid) {
        return playerCache.get(uuid);
    }

    /**
     * Update RP after a match and recalculate rank.
     */
    public void applyMatchResult(UUID uuid, WeaponType weaponType, int rpChange, boolean isWin) {
        BRBPlayer player = playerCache.get(uuid);
        if (player == null) return;

        WeaponRP wrp = player.getWeaponRP(weaponType);
        wrp.addRp(rpChange);
        if (isWin) {
            wrp.addWin();
        } else {
            wrp.addLoss();
        }

        // Save to DB
        playerDAO.updateMatchResult(uuid, weaponType.name(), rpChange, isWin);

        // Recalculate rank
        if (player.recalculateRank()) {
            playerDAO.updateRankClass(uuid, player.getRankClass().name());
        }
    }

    /**
     * Calculate RP change using asymmetric Elo formula.
     * Returns int[] { winnerGain, loserLoss } (loserLoss is positive value).
     */
    public int[] calculateRP(int winnerRP, int loserRP) {
        int base = 30;
        double coefficient = 1.0 + (double)(loserRP - winnerRP) / 1000.0;
        int winnerGain = (int) Math.round(base * coefficient);
        winnerGain = Math.max(5, Math.min(120, winnerGain));

        int loserLoss = (int) Math.round(winnerGain * 0.7);
        loserLoss = Math.max(5, loserLoss);

        // Add participation bonus
        int participationBonus = 5;
        winnerGain += participationBonus;
        loserLoss -= participationBonus;
        if (loserLoss < 0) loserLoss = 0;

        return new int[]{winnerGain, loserLoss};
    }

    /**
     * Get top players (delegates to DAO).
     */
    public List<Map<String, Object>> getTopPlayers(int limit) {
        return playerDAO.getTopPlayers(limit);
    }

    /**
     * Get top players by weapon type.
     */
    public List<Map<String, Object>> getTopByWeapon(String weaponType, int limit) {
        return playerDAO.getTopByWeapon(weaponType, limit);
    }

    /**
     * Get all cached players count.
     */
    public int getCachedPlayerCount() {
        return playerCache.size();
    }

    // ==================== Team Management ====================

    /**
     * Create a new team. Returns error message or null on success.
     */
    public String createTeam(UUID leaderUuid, String teamName) {
        if (teamDAO == null) return "チームシステムが初期化されていません。";

        BRBPlayer player = playerCache.get(leaderUuid);
        if (player == null) return "プレイヤーデータが見つかりません。";

        // B rank+ required
        if (player.getRankClass().getRequiredRP() < RankClass.B.getRequiredRP()
                && player.getRankClass() != RankClass.S && player.getRankClass() != RankClass.A) {
            return "チーム作成にはB級以上のランクが必要です。(現在: " + player.getRankClass().getDisplayName() + ")";
        }

        // Already in a team?
        if (playerTeamCache.containsKey(leaderUuid)) {
            return "既にチームに所属しています。先に /team leave で離脱してください。";
        }

        // Name validation
        if (teamName.length() < 2 || teamName.length() > 16) {
            return "チーム名は2〜16文字で指定してください。";
        }

        if (teamDAO.teamNameExists(teamName)) {
            return "チーム名 '" + teamName + "' は既に使用されています。";
        }

        int teamId = teamDAO.createTeam(teamName, leaderUuid);
        if (teamId < 0) return "チーム作成に失敗しました。";

        Team team = new Team(teamId, teamName, leaderUuid);
        playerTeamCache.put(leaderUuid, team);

        logger.info("Team created: " + teamName + " by " + player.getName());
        return null;
    }

    /**
     * Send a team invitation. Returns error message or null on success.
     */
    public String invitePlayer(UUID leaderUuid, UUID targetUuid) {
        Team team = playerTeamCache.get(leaderUuid);
        if (team == null) return "チームに所属していません。";
        if (!team.isLeader(leaderUuid)) return "チームリーダーのみ招待できます。";
        if (team.getMemberCount() >= 4) return "チームメンバーは最大4人です。";
        if (playerTeamCache.containsKey(targetUuid)) return "対象プレイヤーは既にチームに所属しています。";
        if (pendingInvites.containsKey(targetUuid)) return "対象プレイヤーには既に招待が送られています。";

        pendingInvites.put(targetUuid, team);
        return null;
    }

    /**
     * Accept a pending invitation. Returns error message or null on success.
     */
    public String acceptInvite(UUID targetUuid) {
        Team team = pendingInvites.remove(targetUuid);
        if (team == null) return "保留中の招待がありません。";
        if (playerTeamCache.containsKey(targetUuid)) return "既にチームに所属しています。";

        if (teamDAO != null) {
            teamDAO.addMember(team.getTeamId(), targetUuid);
        }

        team.addMember(targetUuid);
        playerTeamCache.put(targetUuid, team);

        // Update cache for all team members
        for (UUID member : team.getMembers()) {
            playerTeamCache.put(member, team);
        }

        return null;
    }

    /**
     * Deny a pending invitation. Returns error message or null on success.
     */
    public String denyInvite(UUID targetUuid) {
        Team removed = pendingInvites.remove(targetUuid);
        if (removed == null) return "保留中の招待がありません。";
        return null;
    }

    /**
     * Leave the current team. Returns error message or null on success.
     */
    public String leaveTeam(UUID uuid) {
        Team team = playerTeamCache.remove(uuid);
        if (team == null) return "チームに所属していません。";

        team.removeMember(uuid);

        if (teamDAO != null) {
            teamDAO.removeMember(team.getTeamId(), uuid);
        }

        // If leader left
        if (team.isLeader(uuid)) {
            if (team.getMembers().isEmpty()) {
                // No members left, delete team
                if (teamDAO != null) {
                    teamDAO.deleteTeam(team.getTeamId());
                }
                // Clear cache for this team
                playerTeamCache.values().removeIf(t -> t.getTeamId() == team.getTeamId());
                return null;
            } else {
                // Transfer leadership to next member
                UUID newLeader = team.getMembers().iterator().next();
                team.setLeaderId(newLeader);
                if (teamDAO != null) {
                    teamDAO.updateLeader(team.getTeamId(), newLeader);
                }
            }
        }

        return null;
    }

    /**
     * Get the team for a player (from cache). Returns null if not in a team.
     */
    public Team getTeam(UUID uuid) {
        return playerTeamCache.get(uuid);
    }

    /**
     * Get a team by name (from DB).
     */
    public Team getTeamByName(String name) {
        if (teamDAO == null) return null;
        return teamDAO.loadTeamByName(name);
    }

    /**
     * Check if a player has a pending invite.
     */
    public boolean hasPendingInvite(UUID uuid) {
        return pendingInvites.containsKey(uuid);
    }

    /**
     * Get the team name from a pending invite.
     */
    public String getPendingInviteTeamName(UUID uuid) {
        Team team = pendingInvites.get(uuid);
        return team != null ? team.getName() : null;
    }
}
