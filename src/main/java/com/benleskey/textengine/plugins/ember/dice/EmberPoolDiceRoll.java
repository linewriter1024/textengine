package com.benleskey.textengine.plugins.ember.dice;

import com.benleskey.textengine.systems.DiceSystem;

/**
 * EmberPoolDiceRoll encapsulates the Ember game's dice pool system.
 * 
 * Extends DiceSystem.PoolDiceRoll with default Ember thresholds:
 * - Success threshold: 5 (roll 5+ counts as success)
 * - Explosion threshold: 6 (roll 6 rolls again)
 * - Die size: d6
 * 
 * Standard roll: (attribute + skill) d6
 */
public class EmberPoolDiceRoll extends DiceSystem.PoolDiceRoll {
    public static final int DEFAULT_DIE_SIZE = 6;
    public static final int DEFAULT_THRESHOLD = 5;
    public static final int DEFAULT_EXPLOSION_THRESHOLD = 6;

    /**
     * Create a pool dice roll with standard Ember thresholds (5 success, 6
     * explosion, d6).
     * 
     * @param poolSize Number of dice to roll (typically attribute + skill)
     */
    public EmberPoolDiceRoll(int poolSize) {
        super(poolSize, DEFAULT_DIE_SIZE, DEFAULT_THRESHOLD, DEFAULT_EXPLOSION_THRESHOLD);
    }
}
