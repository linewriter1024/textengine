package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;

import java.util.Random;

/**
 * DiceSystem implements a Dice Pool System.
 * 
 * Core mechanic: Roll X dice, count successes, and use contested rolls to
 * compare against opposing pools.
 * Threshold for success: configurable per roll
 * Exploding dice: Rolling equal to explosion threshold causes another roll.
 */
public class DiceSystem extends SingletonGameSystem implements OnSystemInitialize {

    public DiceSystem(Game game) {
        super(game);
    }

    @Override
    public void onSystemInitialize() throws DatabaseException {
        int v = getSchema().getVersionNumber();
        if (v == 0) {
            // No database tables needed - dice rolls are stateless
            getSchema().setVersionNumber(1);
        }
    }

    /**
     * Roll a pool of dice with all parameters specified in PoolDiceRoll.
     * 
     * @param random Random instance for dice rolls
     * @param roll   PoolDiceRoll containing all roll parameters
     * @return PoolDiceResult containing successes and details
     */
    public PoolDiceResult rollPool(Random random, PoolDiceRoll roll) {
        java.util.List<Integer> allDice = new java.util.ArrayList<>();
        int successes = 0;
        int explosions = 0;

        // Roll initial pool
        for (int i = 0; i < roll.poolSize; i++) {
            int die = random.nextInt(roll.dieSize) + 1;
            allDice.add(die);
            if (die >= roll.threshold) {
                successes++;
                // Exploding dice: roll again if at explosion threshold
                if (die >= roll.explosionThreshold) {
                    int explosionResult = countPoolExplosions(random, roll, allDice);
                    successes += explosionResult;
                    explosions += explosionResult;
                }
            }
        }

        int[] diceArray = new int[allDice.size()];
        for (int i = 0; i < allDice.size(); i++) {
            diceArray[i] = allDice.get(i);
        }

        return new PoolDiceResult(roll.poolSize, roll.threshold, diceArray, successes, explosions);
    }

    /**
     * Roll a contested pool where two sides roll against each other.
     * The initiator wins on ties.
     * 
     * @param random        Random instance for dice rolls
     * @param contestedRoll ContestedPoolDiceRoll containing both rolls
     * @return ContestedPoolDiceResult containing both results and winner
     */
    public ContestedPoolDiceResult rollContestedPool(Random random, ContestedPoolDiceRoll contestedRoll) {
        PoolDiceResult initiatorResult = rollPool(random, contestedRoll.initiator);
        PoolDiceResult opposerResult = rollPool(random, contestedRoll.opposer);
        return new ContestedPoolDiceResult(initiatorResult, opposerResult);
    }

    /**
     * Roll generic dice notation (nDx+c).
     * 
     * @param random   Random instance for dice rolls
     * @param notation String like "3d6+2" or "2d20" with optional spaces
     * @return GenericDiceResult containing roll results
     * @throws IllegalArgumentException if notation is invalid
     */
    public GenericDiceResult rollGeneric(Random random, String notation) {
        GenericDiceRoll roll = parseGenericDiceNotation(notation);
        return rollGeneric(random, roll);
    }

    /**
     * Roll generic dice with parameters.
     * 
     * @param random Random instance for dice rolls
     * @param roll   GenericDiceRoll containing all parameters
     * @return GenericDiceResult containing roll results
     */
    public GenericDiceResult rollGeneric(Random random, GenericDiceRoll roll) {
        int[] dice = new int[roll.numDice];
        int total = 0;

        // Roll all dice
        for (int i = 0; i < roll.numDice; i++) {
            dice[i] = random.nextInt(roll.dieSize) + 1;
            total += dice[i];
        }

        total += roll.modifier;
        return new GenericDiceResult(roll, dice, total);
    }

    /**
     * Parse generic dice notation (nDx+c or nDx-c).
     * Handles spaces and optional modifier.
     * 
     * @param notation String like "3d6+2", "2d20", " 4d8 - 1 ", etc.
     * @return GenericDiceRoll with parsed values
     * @throws IllegalArgumentException if notation is invalid
     */
    public static GenericDiceRoll parseGenericDiceNotation(String notation) {
        if (notation == null || notation.trim().isEmpty()) {
            throw new IllegalArgumentException("Dice notation cannot be empty");
        }

        // Remove all spaces
        String cleaned = notation.replaceAll("\\s+", "");

        // Parse nDx+c or nDx-c or nDx
        java.util.regex.Pattern pattern = java.util.regex.Pattern
                .compile("^(\\d+)d(\\d+)([+-])(\\d+)$|^(\\d+)d(\\d+)$");
        java.util.regex.Matcher matcher = pattern.matcher(cleaned);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    String.format("Invalid dice notation '%s'. Expected format: nDx+c (e.g., 3d6+2, 2d20)", notation));
        }

        if (matcher.group(1) != null) {
            // Format: nDx+c or nDx-c
            int numDice = Integer.parseInt(matcher.group(1));
            int dieSize = Integer.parseInt(matcher.group(2));
            String operator = matcher.group(3);
            int modifier = Integer.parseInt(matcher.group(4));
            if (operator.equals("-")) {
                modifier = -modifier;
            }
            return new GenericDiceRoll(numDice, dieSize, modifier);
        } else {
            // Format: nDx
            int numDice = Integer.parseInt(matcher.group(5));
            int dieSize = Integer.parseInt(matcher.group(6));
            return new GenericDiceRoll(numDice, dieSize, 0);
        }
    }

    /**
     * Count additional successes from explosions in pool rolls, adding exploded
     * dice to the list.
     */
    private int countPoolExplosions(Random random, PoolDiceRoll roll, java.util.List<Integer> allDice) {
        int additionalSuccesses = 0;
        while (true) {
            int die = random.nextInt(roll.dieSize) + 1;
            allDice.add(die);
            if (die >= roll.threshold) {
                additionalSuccesses++;
                if (die < roll.explosionThreshold) {
                    break;
                }
            } else {
                break;
            }
        }
        return additionalSuccesses;
    }

    /**
     * Encapsulates all parameters for a pool dice roll.
     */
    public static class PoolDiceRoll {
        public final int poolSize;
        public final int dieSize;
        public final int threshold;
        public final int explosionThreshold;

        /**
         * Create a pool dice roll configuration.
         * 
         * @param poolSize           Number of dice to roll
         * @param dieSize            Maximum value per die (e.g., 10 for d10)
         * @param threshold          Minimum die value to count as success
         * @param explosionThreshold Die value that triggers explosion (usually dieSize)
         * @throws IllegalArgumentException if explosionThreshold <= threshold
         */
        public PoolDiceRoll(int poolSize, int dieSize, int threshold, int explosionThreshold) {
            if (explosionThreshold <= 1) {
                throw new IllegalArgumentException(
                        String.format(
                                "Explosion threshold (%d) must be greater than 1 to prevent every roll from exploding",
                                explosionThreshold));
            }
            this.poolSize = poolSize;
            this.dieSize = dieSize;
            this.threshold = threshold;
            this.explosionThreshold = explosionThreshold;
        }
    }

    /**
     * Encapsulates parameters for a contested pool roll between two sides.
     */
    public static class ContestedPoolDiceRoll {
        public final PoolDiceRoll initiator;
        public final PoolDiceRoll opposer;

        /**
         * Create a contested pool dice roll configuration.
         * 
         * @param initiator Roll parameters for the initiating side (wins on ties)
         * @param opposer   Roll parameters for the opposing side
         */
        public ContestedPoolDiceRoll(PoolDiceRoll initiator, PoolDiceRoll opposer) {
            this.initiator = initiator;
            this.opposer = opposer;
        }
    }

    /**
     * Immutable result of a pool dice roll.
     */
    public static class PoolDiceResult {
        public final int poolSize;
        public final int threshold;
        public final int[] dice;
        public final int successes;
        public final int explosions;

        public PoolDiceResult(int poolSize, int threshold, int[] dice, int successes, int explosions) {
            this.poolSize = poolSize;
            this.threshold = threshold;
            this.dice = dice;
            this.successes = successes;
            this.explosions = explosions;
        }

        public String getDiceString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dice.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(dice[i]);
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return String.format(
                    "PoolDiceResult{pool=%d, threshold=%d, successes=%d, explosions=%d}",
                    poolSize, threshold, successes, explosions);
        }
    }

    /**
     * Immutable result of a contested pool dice roll.
     */
    public static class ContestedPoolDiceResult {
        public final PoolDiceResult initiatorResult;
        public final PoolDiceResult opposerResult;

        public ContestedPoolDiceResult(PoolDiceResult initiatorResult, PoolDiceResult opposerResult) {
            this.initiatorResult = initiatorResult;
            this.opposerResult = opposerResult;
        }

        /**
         * Check if the initiator won (ties count as initiator victory).
         * 
         * @return true if initiator's successes >= opposer's successes
         */
        public boolean initiatorWon() {
            return initiatorResult.successes >= opposerResult.successes;
        }

        /**
         * Get the delta (initiator successes - opposer successes).
         * Positive means initiator won, negative means opposer won, zero is a tie.
         * 
         * @return delta between initiator and opposer successes
         */
        public int getDelta() {
            return initiatorResult.successes - opposerResult.successes;
        }

        @Override
        public String toString() {
            return String.format(
                    "ContestedPoolDiceResult{initiator=%s, opposer=%s, delta=%d, initiatorWon=%b}",
                    initiatorResult, opposerResult, getDelta(), initiatorWon());
        }
    }

    /**
     * Generic dice roll parameters (nDx+c).
     */
    public static class GenericDiceRoll {
        public final int numDice;
        public final int dieSize;
        public final int modifier;

        /**
         * Create a generic dice roll configuration.
         * 
         * @param numDice  Number of dice to roll
         * @param dieSize  Maximum value per die (e.g., 6 for d6, 20 for d20)
         * @param modifier Flat modifier to add to total (+c or -c)
         */
        public GenericDiceRoll(int numDice, int dieSize, int modifier) {
            this.numDice = numDice;
            this.dieSize = dieSize;
            this.modifier = modifier;
        }

        @Override
        public String toString() {
            if (modifier == 0) {
                return String.format("%dd%d", numDice, dieSize);
            } else if (modifier > 0) {
                return String.format("%dd%d+%d", numDice, dieSize, modifier);
            } else {
                return String.format("%dd%d%d", numDice, dieSize, modifier);
            }
        }
    }

    /**
     * Result of a generic dice roll.
     */
    public static class GenericDiceResult {
        public final GenericDiceRoll roll;
        public final int[] dice;
        public final int total;

        public GenericDiceResult(GenericDiceRoll roll, int[] dice, int total) {
            this.roll = roll;
            this.dice = dice;
            this.total = total;
        }

        public String getDiceString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dice.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(dice[i]);
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return String.format("GenericDiceResult{roll=%s, total=%d}", roll, total);
        }
    }
}
