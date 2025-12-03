package com.benleskey.textengine.plugins.games.ember.dice;

import com.benleskey.textengine.systems.DiceSystem;

/**
 * EmberPoolDiceRoll encapsulates the Ember game's dice pool system.
 */
public class EmberPoolDiceRoll extends DiceSystem.PoolDiceRoll {
    public static final int DEFAULT_DIE_SIZE = 10;
    public static final int DEFAULT_THRESHOLD = 8;
    public static final int DEFAULT_EXPLOSION_THRESHOLD = 10;

    /**
     * Create a pool dice roll with standard Ember settings
     * 
     * @param poolSize Number of dice to roll (typically attribute + skill)
     */
    public EmberPoolDiceRoll(int poolSize) {
        this(poolSize, DEFAULT_THRESHOLD, DEFAULT_EXPLOSION_THRESHOLD);
    }

    public EmberPoolDiceRoll(int poolSize, int threshold, int explosionThreshold) {
        super(poolSize, DEFAULT_DIE_SIZE, threshold, explosionThreshold);
    }

    public static EmberPoolDiceRoll fromOffsets(int poolSize, int thresholdDecrease, int explosionThresholdDecrease) {
        return new EmberPoolDiceRoll(
                poolSize,
                DEFAULT_THRESHOLD - thresholdDecrease,
                DEFAULT_EXPLOSION_THRESHOLD - explosionThresholdDecrease);
    }
}
