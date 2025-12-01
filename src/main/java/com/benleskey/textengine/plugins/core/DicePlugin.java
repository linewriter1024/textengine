package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.systems.DiceSystem;
import com.benleskey.textengine.util.Markup;

import java.util.Random;

/**
 * DicePlugin provides dice rolling commands using the Dice Pool System.
 * 
 * Commands:
 * - roll [pool_size] [difficulty] [threshold] - Roll a pool of d10 dice
 * Example: "roll 10 5 7" rolls 10d10 with difficulty 5 and threshold 7+
 * Threshold for success: configurable (1-10)
 * Exploding dice: 10's roll again
 */
public class DicePlugin extends Plugin implements OnPluginInitialize {

    public static final String ROLL = "roll";
    public static final String ROLL_VARIANT = "roll_variant";
    public static final String ROLL_GENERIC_VARIANT = "roll_generic_variant";
    public static final String M_POOL_SIZE = "pool_size";
    public static final String M_DIE_SIZE = "die_size";
    public static final String M_THRESHOLD = "threshold";
    public static final String M_EXPLOSION_THRESHOLD = "explosion_threshold";
    public static final String M_DIFFICULTY = "difficulty";
    public static final String M_DICE = "dice";
    public static final String M_SUCCESSES = "successes";
    public static final String M_EXPLOSIONS = "explosions";
    public static final String M_OUTCOME = "outcome";
    public static final String M_NOTATION = "notation";
    public static final String M_TOTAL = "total";
    public static final String M_GENERIC_DICE = "generic_dice";

    private DiceSystem diceSystem;

    public DicePlugin(Game game) {
        super(game);
    }

    @Override
    public void onPluginInitialize() {
        // Register the DiceSystem
        diceSystem = new DiceSystem(game);
        game.registerSystem(diceSystem);

        // Register roll command: "roll [pool] [die_size] [threshold]
        // [explosion_threshold] [difficulty]"
        game.registerCommand(new Command(ROLL, this::handleRoll,
                new CommandVariant(ROLL_VARIANT, "^roll\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*$",
                        args -> {
                            int poolSize = Integer.parseInt(args.group(1));
                            int dieSize = Integer.parseInt(args.group(2));
                            int threshold = Integer.parseInt(args.group(3));
                            int explosionThreshold = Integer.parseInt(args.group(4));
                            int difficulty = Integer.parseInt(args.group(5));
                            return CommandInput.makeNone()
                                    .put(M_POOL_SIZE, poolSize)
                                    .put(M_DIE_SIZE, dieSize)
                                    .put(M_THRESHOLD, threshold)
                                    .put(M_EXPLOSION_THRESHOLD, explosionThreshold)
                                    .put(M_DIFFICULTY, difficulty);
                        }),
                new CommandVariant(ROLL_GENERIC_VARIANT, "^roll\\s+([0-9]+\\s*d\\s*[0-9]+(?:\\s*[+-]\\s*[0-9]+)?)\\s*$",
                        args -> {
                            String notation = args.group(1).trim();
                            return CommandInput.makeNone()
                                    .put(M_NOTATION, notation);
                        })));
    }

    private void handleRoll(com.benleskey.textengine.Client client, CommandInput input) {
        // Check which variant was used by checking if pool size is present
        if (input.values.containsKey(M_POOL_SIZE)) {
            handlePoolRoll(client, input);
        } else {
            handleGenericRoll(client, input);
        }
    }

    private void handlePoolRoll(com.benleskey.textengine.Client client, CommandInput input) {
        int poolSize = input.get(M_POOL_SIZE);
        int dieSize = input.get(M_DIE_SIZE);
        int threshold = input.get(M_THRESHOLD);
        int explosionThreshold = input.get(M_EXPLOSION_THRESHOLD);
        int difficulty = input.get(M_DIFFICULTY);

        // Create dice roll configuration
        DiceSystem.DiceRoll roll = new DiceSystem.DiceRoll(poolSize, dieSize, threshold, explosionThreshold);

        // Roll the dice with a new Random instance
        DiceSystem.DiceResult result = diceSystem.roll(new Random(), roll);

        // Evaluate outcome
        DiceSystem.Outcome outcome = diceSystem.getOutcome(result.successes, difficulty);

        // Build response
        CommandOutput output = CommandOutput.make(ROLL)
                .put(M_POOL_SIZE, poolSize)
                .put(M_DIE_SIZE, dieSize)
                .put(M_THRESHOLD, threshold)
                .put(M_EXPLOSION_THRESHOLD, explosionThreshold)
                .put(M_DIFFICULTY, difficulty)
                .put(M_DICE, result.getDiceString())
                .put(M_SUCCESSES, result.successes)
                .put(M_EXPLOSIONS, result.explosions)
                .put(M_OUTCOME, outcome.name().toLowerCase());

        // Format user-friendly text
        String textContent = String.format(
                "%dd%d (difficulty %d, threshold %d+, explosion at %d+):\nDice: %s\nSuccesses: %d%s\nOutcome: %s",
                poolSize,
                dieSize,
                difficulty,
                threshold,
                explosionThreshold,
                result.getDiceString(),
                result.successes,
                result.explosions > 0 ? " (including " + result.explosions + " from explosions)" : "",
                outcome.label);

        output.text(Markup.escape(textContent));
        client.sendOutput(output);
    }

    private void handleGenericRoll(com.benleskey.textengine.Client client, CommandInput input) {
        String notation = input.get(M_NOTATION);

        try {
            // Parse and roll generic dice
            DiceSystem.GenericDiceResult result = diceSystem.rollGeneric(new Random(), notation);

            // Build response
            CommandOutput output = CommandOutput.make(ROLL)
                    .put(M_NOTATION, notation)
                    .put(M_GENERIC_DICE, result.getDiceString())
                    .put(M_TOTAL, result.total);

            // Format user-friendly text
            String textContent = String.format(
                    "%s:\nDice: %s\nTotal: %d",
                    result.roll.toString(),
                    result.getDiceString(),
                    result.total);

            output.text(Markup.escape(textContent));
            client.sendOutput(output);
        } catch (IllegalArgumentException e) {
            // Return error response
            CommandOutput output = CommandOutput.make(ROLL)
                    .error("invalid_notation")
                    .text(Markup.escape("Error: " + e.getMessage()));
            client.sendOutput(output);
        }
    }
}
