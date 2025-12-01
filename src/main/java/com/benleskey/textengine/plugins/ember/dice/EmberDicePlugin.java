package com.benleskey.textengine.plugins.ember.dice;

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
 * EmberDicePlugin provides dice rolling for the Ember game.
 * 
 * Includes the debug:emberroll command with d6 pool rolls using
 * Ember's default thresholds (5 success, 6 explosion).
 */
public class EmberDicePlugin extends Plugin implements OnPluginInitialize {

    public static final String DEBUG_EMBER_ROLL = "debug:emberroll";
    public static final String M_POOL_SIZE = "pool_size";
    public static final String M_DICE = "dice";
    public static final String M_SUCCESSES = "successes";
    public static final String M_EXPLOSIONS = "explosions";
    public static final String M_OUTCOME = "outcome";
    public static final String M_DIFFICULTY = "difficulty";

    private DiceSystem diceSystem;

    public EmberDicePlugin(Game game) {
        super(game);
    }

    @Override
    public void onPluginInitialize() {
        diceSystem = game.getSystem(DiceSystem.class);

        // Register debug:emberroll command: "debug:emberroll [pool_size] [difficulty]"
        // Both parameters are required
        game.registerCommand(new Command(DEBUG_EMBER_ROLL, this::handleEmberRoll,
                new CommandVariant("ember_roll", "^debug:emberroll\\s+(\\d+)\\s+(\\d+)\\s*$",
                        args -> {
                            int poolSize = Integer.parseInt(args.group(1));
                            int difficulty = Integer.parseInt(args.group(2));
                            return CommandInput.makeNone()
                                    .put(M_POOL_SIZE, poolSize)
                                    .put(M_DIFFICULTY, difficulty);
                        })));
    }

    private void handleEmberRoll(com.benleskey.textengine.Client client, CommandInput input) {
        int poolSize = input.get(M_POOL_SIZE);
        int difficulty = input.get(M_DIFFICULTY);

        // Create Ember-style roll with default thresholds
        EmberPoolDiceRoll roll = new EmberPoolDiceRoll(poolSize);

        // Roll using DiceSystem with the Ember roll's parameters
        DiceSystem.PoolDiceResult result = diceSystem.rollPool(new Random(), roll);

        // Evaluate outcome
        DiceSystem.Outcome outcome = result.getOutcome(difficulty);

        // Build response
        CommandOutput output = CommandOutput.make(DEBUG_EMBER_ROLL)
                .put(M_POOL_SIZE, poolSize)
                .put(M_DIFFICULTY, difficulty)
                .put(M_DICE, result.getDiceString())
                .put(M_SUCCESSES, result.successes)
                .put(M_EXPLOSIONS, result.explosions)
                .put(M_OUTCOME, outcome.name().toLowerCase());

        // Format user-friendly text
        String textContent = String.format(
                "Ember Roll %dd6 (difficulty %d, threshold 5+, explosion at 6+):\nDice: %s\nSuccesses: %d%s\nOutcome: %s",
                poolSize,
                difficulty,
                result.getDiceString(),
                result.successes,
                result.explosions > 0 ? " (including " + result.explosions + " from explosions)" : "",
                outcome.label);

        output.text(Markup.escape(textContent));
        client.sendOutput(output);
    }
}
