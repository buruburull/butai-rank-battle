package com.borderrank.battle.model;

/**
 * Represents the Ranking Points (RP) data for a specific weapon type.
 */
public class WeaponRP {
    private final WeaponType type;
    private int rp;
    private int wins;
    private int losses;

    /**
     * Constructs a WeaponRP instance.
     *
     * @param type the weapon type
     * @param rp the initial RP value (default 1000)
     * @param wins the number of wins
     * @param losses the number of losses
     */
    public WeaponRP(WeaponType type, int rp, int wins, int losses) {
        this.type = type;
        this.rp = rp;
        this.wins = wins;
        this.losses = losses;
    }

    /**
     * Constructs a WeaponRP instance with default RP value of 1000.
     *
     * @param type the weapon type
     */
    public WeaponRP(WeaponType type) {
        this(type, 1000, 0, 0);
    }

    /**
     * Adds a win to this weapon's record.
     */
    public void addWin() {
        this.wins++;
    }

    /**
     * Adds a loss to this weapon's record.
     */
    public void addLoss() {
        this.losses++;
    }

    /**
     * Gets the current RP value.
     *
     * @return the RP value
     */
    public int getRp() {
        return rp;
    }

    /**
     * Sets the RP value.
     *
     * @param rp the new RP value
     */
    public void setRp(int rp) {
        this.rp = rp;
    }

    /**
     * Gets the weapon type.
     *
     * @return the weapon type
     */
    public WeaponType getType() {
        return type;
    }

    /**
     * Gets the number of wins.
     *
     * @return the number of wins
     */
    public int getWins() {
        return wins;
    }

    /**
     * Gets the number of losses.
     *
     * @return the number of losses
     */
    public int getLosses() {
        return losses;
    }

    /**
     * Sets the number of wins.
     *
     * @param wins the number of wins
     */
    public void setWins(int wins) {
        this.wins = wins;
    }

    /**
     * Sets the number of losses.
     *
     * @param losses the number of losses
     */
    public void setLosses(int losses) {
        this.losses = losses;
    }

    /**
     * Calculates the win rate as a percentage.
     *
     * @return the win rate, or 0.0 if no games have been played
     */
    public double getWinRate() {
        int total = wins + losses;
        if (total == 0) {
            return 0.0;
        }
        return (double) wins / total * 100.0;
    }

    @Override
    public String toString() {
        return "WeaponRP{" +
                "type=" + type +
                ", rp=" + rp +
                ", wins=" + wins +
                ", losses=" + losses +
                '}';
    }
}
