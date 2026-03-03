package com.borderrank.battle.manager;

import com.borderrank.battle.database.PlayerDAO;
import com.borderrank.battle.model.BRBPlayer;
import com.borderrank.battle.model.WeaponType;

import java.util.*;

/**
 * Manages rank points (RP) calculation and rank advancement.
 * Handles both solo and team match RP calculations.
 */
public class RankManager {
    
    private final PlayerDAO playerDAO;

    // RP thresholds for rank progression
    private static final int B_RANK_THRESHOLD = 1500;
    private static final int A_RANK_THRESHOLD = 3000;
    private static final int S_RANK_THRESHOLD = 5000;

    // RP calculation constants
    private static final int BASE_RP_CHANGE = 30;
    private static final double RP_COEFFICIENT = 1.0;
    private static final double OPPONENT_SCALING = 1.0 / 1000.0; // Divide opponent RP diff by 1000
    private static final int MIN_RP_CHANGE = 5;
    private static final int MAX_RP_CHANGE = 60;

    /**
     * Constructs a RankManager with database access.
     *
     * @param playerDAO the data access object for player data
     */
    public RankManager(PlayerDAO playerDAO) {
        this.playerDAO = playerDAO;
    }

    /**
     * Calculates RP change for a solo match using an Elo-like formula.
     * Formula: base=30, coefficient=1.0+(opponentRP-playerRP)/1000
     * Final change is clamped between ±5 and ±60.
     *
     * @param winnerRP the RP of the winning player
     * @param loserRP the RP of the losing player
     * @return the RP change for the winner (winner gains, loser loses same amount)
     */
    public int calculateSoloRP(int winnerRP, int loserRP) {
        // Opponent strength factor
        double rpDifference = loserRP - winnerRP;
        double coefficient = RP_COEFFICIENT + (rpDifference * OPPONENT_SCALING);
        
        // Calculate RP change
        double rpChange = BASE_RP_CHANGE * coefficient;
        
        // Clamp to valid range
        int change = (int) Math.round(rpChange);
        change = Math.max(MIN_RP_CHANGE, Math.min(MAX_RP_CHANGE, change));
        
        return change;
    }

    /**
     * Calculates RP change for a team match based on placement and performance.
     * RP gain varies by placement and survival status.
     *
     * @param placement the team's final placement (1 = first, 2 = second, etc.)
     * @param kills the total kills achieved by the team
     * @param survived whether the team survived (did not get eliminated)
     * @return the RP change for the team
     */
    public int calculateTeamRP(int placement, int kills, boolean survived) {
        int baseRP = switch (placement) {
            case 1 -> 60;   // 1st place
            case 2 -> 40;   // 2nd place
            case 3 -> 20;   // 3rd place
            default -> 5;   // Other placements
        };
        
        // Bonus for each kill
        int killBonus = kills * 3;
        
        // Bonus for survival
        int survivalBonus = survived ? 15 : -10;
        
        return baseRP + killBonus + survivalBonus;
    }

    /**
     * Checks if a player has achieved promotion criteria to B rank.
     * Requirement: any weapon's RP >= 1500.
     *
     * @param player the player to check
     * @return true if the player qualifies for B rank promotion
     */
    public boolean checkPromotion(BRBPlayer player) {
        if (player == null) {
            return false;
        }
        
        // Check if any weapon type has reached B rank threshold
        return player.getWeaponRPs().values().stream()
                .anyMatch(rp -> rp >= B_RANK_THRESHOLD);
    }

    /**
     * Checks if a player has achieved promotion to A rank.
     * Requirement: any weapon's RP >= 3000.
     *
     * @param player the player to check
     * @return true if the player qualifies for A rank
     */
    public boolean checkARankPromotion(BRBPlayer player) {
        if (player == null) {
            return false;
        }
        
        return player.getWeaponRPs().values().stream()
                .anyMatch(rp -> rp >= A_RANK_THRESHOLD);
    }

    /**
     * Checks if a player has achieved promotion to S rank.
     * Requirement: any weapon's RP >= 5000.
     *
     * @param player the player to check
     * @return true if the player qualifies for S rank
     */
    public boolean checkSRankPromotion(BRBPlayer player) {
        if (player == null) {
            return false;
        }
        
        return player.getWeaponRPs().values().stream()
                .anyMatch(rp -> rp >= S_RANK_THRESHOLD);
    }

    /**
     * Retrieves the top players for a specific weapon type from the database.
     *
     * @param weaponType the weapon type to rank by
     * @param limit the maximum number of players to return
     * @return a list of top BRBPlayer objects, sorted by descending RP
     */
    public List<BRBPlayer> getTopPlayers(WeaponType weaponType, int limit) {
        return playerDAO.getTopPlayersByWeapon(weaponType, limit);
    }

    /**
     * Retrieves the global top players by combined RP.
     *
     * @param limit the maximum number of players to return
     * @return a list of top BRBPlayer objects, sorted by descending total RP
     */
    public List<BRBPlayer> getGlobalTopPlayers(int limit) {
        return playerDAO.getTopPlayers(limit);
    }

    /**
     * Gets the highest rank tier achieved by a player across all weapons.
     *
     * @param player the player to check
     * @return a string representing the highest rank (S, A, B, C, or D)
     */
    public String getHighestRankTier(BRBPlayer player) {
        if (player == null) {
            return "D";
        }
        
        int maxRP = player.getWeaponRPs().values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        
        if (maxRP >= S_RANK_THRESHOLD) return "S";
        if (maxRP >= A_RANK_THRESHOLD) return "A";
        if (maxRP >= B_RANK_THRESHOLD) return "B";
        return "C";
    }

    /**
     * Updates a player's RP for a specific weapon type.
     *
     * @param player the player to update
     * @param weaponType the weapon type
     * @param rpChange the RP change (positive or negative)
     */
    public void updateWeaponRP(BRBPlayer player, WeaponType weaponType, int rpChange) {
        if (player == null) {
            return;
        }
        
        var weaponRPs = player.getWeaponRPs();
        int currentRP = weaponRPs.getOrDefault(weaponType, 0);
        weaponRPs.put(weaponType, Math.max(0, currentRP + rpChange));
    }

    /**
     * Calculates the RP change for losing a match.
     * This is typically the negative of the winner's RP gain.
     *
     * @param loserRP the RP of the losing player
     * @param winnerRP the RP of the winning player
     * @return the RP change for the loser (negative value)
     */
    public int calculateLossRP(int loserRP, int winnerRP) {
        return -calculateSoloRP(winnerRP, loserRP);
    }
}
