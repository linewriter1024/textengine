package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.systems.CommandHelpSystem;
import com.benleskey.textengine.systems.DiceSystem;
import com.benleskey.textengine.util.Markup;

import java.util.Random;

/**
 * DicePlugin provides dice rolling commands using the Dice Pool System.
 * 
 * Commands:
 * - debug:roll [pool_size] [die_size] [threshold] [explosion_threshold]
 * [difficulty] - Roll a pool of dice
 * Example: "debug:roll 10 10 7 10 5" rolls 10d10 with difficulty 5 and
 * threshold 7+
 * - debug:roll [notation] - Roll generic dice notation
 * Example: "debug:roll 3d6+2"
 * 
 * Threshold for success: configurable (1-max die size)
 * Exploding dice: at explosion threshold
 */
public class DicePlugin extends Plugin implements OnPluginInitialize {

    public static final String ROLL = "debug:roll";
    public static final String ROLL_VARIANT = "roll_variant";
    public static final String ROLL_GENERIC_VARIANT = "roll_generic_variant";
    public static final String M_POOL_SIZE = "pool_size";
    public static final String M_DIE_SIZE = "die_size";
    public static final String M_THRESHOLD = "threshold";
    public static final String M_EXPLOSION_THRESHOLD = "explosion_threshold";
    public static final String M_DICE = "dice";
    public static final String M_SUCCESSES = "successes";
    public static final String M_EXPLOSIONS = "explosions";
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

        // Register help
        CommandHelpSystem helpSystem = game.getSystem(CommandHelpSystem.class);
        helpSystem.registerHelp("debug:roll <pool> <die_size> <threshold> <explosion_threshold>",
                "Roll a pool of dice with specified parameters.");
        helpSystem.registerHelp("debug:roll <notation>",
                "Roll generic dice notation (e.g., 3d6+2).");

        // Register roll command: "debug:roll [pool] [die_size] [threshold]
        // [explosion_threshold]"
        game.registerCommand(new Command(ROLL, this::handleRoll,
                new CommandVariant(ROLL_VARIANT, "^debug:roll\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*$",
                        args -> {
                            int poolSize = Integer.parseInt(args.group(1));
                            int dieSize = Integer.parseInt(args.group(2));
                            int threshold = Integer.parseInt(args.group(3));
                            int explosionThreshold = Integer.parseInt(args.group(4));
                            return CommandInput.makeNone()
                                    .put(M_POOL_SIZE, poolSize)
                                    .put(M_DIE_SIZE, dieSize)
                                    .put(M_THRESHOLD, threshold)
                                    .put(M_EXPLOSION_THRESHOLD, explosionThreshold);
                        }),
                new CommandVariant(ROLL_GENERIC_VARIANT,
                        "^debug:roll\\s+([0-9]+\\s*d\\s*[0-9]+(?:\\s*[+-]\\s*[0-9]+)?)\\s*$",
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

        // Create dice roll configuration
        DiceSystem.PoolDiceRoll roll = new DiceSystem.PoolDiceRoll(poolSize, dieSize, threshold, explosionThreshold);

        // Roll the dice with a new Random instance
        DiceSystem.PoolDiceResult result = diceSystem.rollPool(new Random(), roll);

        // Build response
        CommandOutput output = CommandOutput.make(ROLL)
                .put(M_POOL_SIZE, poolSize)
                .put(M_DIE_SIZE, dieSize)
                .put(M_THRESHOLD, threshold)
                .put(M_EXPLOSION_THRESHOLD, explosionThreshold)
                .put(M_DICE, result.getDiceString())
                .put(M_SUCCESSES, result.successes)
                .put(M_EXPLOSIONS, result.explosions);

        // Format user-friendly text
        String textContent = String.format(
                "%dd%d (threshold %d+, explosion at %d+):\nDice: %s\nSuccesses: %d%s",
                poolSize,
                dieSize,
                threshold,
                explosionThreshold,
                result.getDiceString(),
                result.successes,
                result.explosions > 0 ? " (including " + result.explosions + " from explosions)" : "");

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
