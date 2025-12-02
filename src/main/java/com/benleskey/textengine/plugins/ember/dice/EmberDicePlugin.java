package com.benleskey.textengine.plugins.ember.dice;

import java.util.Random;

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

/**
 * EmberDicePlugin provides dice rolling for the Ember game.
 * 
 * Includes the debug:emberroll command with pool rolls using
 * Ember's default thresholds
 */
public class EmberDicePlugin extends Plugin implements OnPluginInitialize {

        public static final String DEBUG_EMBER_ROLL = "debug:emberroll";
        public static final String DEBUG_EMBER_SIM = "debug:embersim";
        public static final String M_POOL_SIZE = "pool_size";
        public static final String M_DICE = "dice";
        public static final String M_SUCCESSES = "successes";
        public static final String M_EXPLOSIONS = "explosions";
        public static final String M_DIFFICULTY = "difficulty";
        public static final String M_SAMPLES = "samples";
        public static final String M_SUCCESS = "success";
        public static final String M_DELTA = "delta";
        public static final String M_MIN_DELTA = "min_delta";
        public static final String M_MAX_DELTA = "max_delta";
        public static final String M_POOL_SIZE_A = "pool_size_a";
        public static final String M_POOL_SIZE_B = "pool_size_b";
        public static final String M_MODE = "mode"; // "simple" or "contested"

        private DiceSystem diceSystem;

        public EmberDicePlugin(Game game) {
                super(game);
        }

        @Override
        public void onPluginInitialize() {
                diceSystem = game.getSystem(DiceSystem.class);

                // Register help
                CommandHelpSystem helpSystem = game.getSystem(CommandHelpSystem.class);
                helpSystem.registerHelp("debug:emberroll <poolSize> <difficulty>",
                                "Roll Ember dice");
                helpSystem.registerHelp("debug:embersim <poolSize> <difficulty> [samples]",
                                "Simulate many Ember dice rolls and return delta distribution.");
                helpSystem.registerHelp("debug:embersim <poolA> v <poolB> [samples]",
                                "Simulate contested rolls: poolA vs poolB. Returns delta distribution (poolA - poolB).");

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

                // Register ember simulation command: "debug:embersim [pool_size] [difficulty]
                // [samples]?"
                game.registerCommand(new Command(DEBUG_EMBER_SIM, this::handleEmberSim,
                                new CommandVariant("ember_sim_contested_samples",
                                                "^debug:embersim\\s+(\\d+)\\s+v\\s+(\\d+)\\s+(\\d+)\\s*$",
                                                args -> {
                                                        int poolSizeA = Integer.parseInt(args.group(1));
                                                        int poolSizeB = Integer.parseInt(args.group(2));
                                                        int samples = Integer.parseInt(args.group(3));
                                                        return CommandInput.makeNone()
                                                                        .put(M_POOL_SIZE_A, poolSizeA)
                                                                        .put(M_POOL_SIZE_B, poolSizeB)
                                                                        .put(M_SAMPLES, samples)
                                                                        .put(M_MODE, "contested");
                                                }),
                                new CommandVariant("ember_sim_contested",
                                                "^debug:embersim\\s+(\\d+)\\s+v\\s+(\\d+)\\s*$",
                                                args -> {
                                                        int poolSizeA = Integer.parseInt(args.group(1));
                                                        int poolSizeB = Integer.parseInt(args.group(2));
                                                        int samples = 1000000;
                                                        return CommandInput.makeNone()
                                                                        .put(M_POOL_SIZE_A, poolSizeA)
                                                                        .put(M_POOL_SIZE_B, poolSizeB)
                                                                        .put(M_SAMPLES, samples)
                                                                        .put(M_MODE, "contested");
                                                }),
                                new CommandVariant("ember_sim", "^debug:embersim\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*$",
                                                args -> {
                                                        int poolSize = Integer.parseInt(args.group(1));
                                                        int difficulty = Integer.parseInt(args.group(2));
                                                        int samples = Integer.parseInt(args.group(3));
                                                        return CommandInput.makeNone()
                                                                        .put(M_POOL_SIZE, poolSize)
                                                                        .put(M_DIFFICULTY, difficulty)
                                                                        .put(M_SAMPLES, samples)
                                                                        .put(M_MODE, "simple");
                                                }),
                                new CommandVariant("ember_sim_default", "^debug:embersim\\s+(\\d+)\\s+(\\d+)\\s*$",
                                                args -> {
                                                        int poolSize = Integer.parseInt(args.group(1));
                                                        int difficulty = Integer.parseInt(args.group(2));
                                                        // default sample size
                                                        int samples = 1000000;
                                                        return CommandInput.makeNone()
                                                                        .put(M_POOL_SIZE, poolSize)
                                                                        .put(M_DIFFICULTY, difficulty)
                                                                        .put(M_SAMPLES, samples)
                                                                        .put(M_MODE, "simple");
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
                boolean success = outcome.isSuccess();
                int delta = outcome.getDelta();

                // Build response
                CommandOutput output = CommandOutput.make(DEBUG_EMBER_ROLL)
                                .put(M_POOL_SIZE, poolSize)
                                .put(M_DIFFICULTY, difficulty)
                                .put(M_DICE, result.getDiceString())
                                .put(M_SUCCESSES, result.successes)
                                .put(M_EXPLOSIONS, result.explosions)
                                .put(M_SUCCESS, success)
                                .put(M_DELTA, delta);

                // Format user-friendly text
                String textContent = String.format(
                                "Ember Roll %dd%d (difficulty %d, threshold %d, explosion at %d):\nDice: %s\nSuccesses: %d%s\n%s (delta: %+d)",
                                poolSize,
                                roll.dieSize,
                                difficulty,
                                roll.threshold,
                                roll.explosionThreshold,
                                result.getDiceString(),
                                result.successes,
                                result.explosions > 0 ? " (including " + result.explosions + " from explosions)" : "",
                                success ? "Success" : "Failure",
                                delta);

                output.text(Markup.escape(textContent));
                client.sendOutput(output);
        }

        private void handleEmberSim(com.benleskey.textengine.Client client, CommandInput input) {
                String mode = input.get(M_MODE);
                if ("contested".equals(mode)) {
                        handleEmberSimContested(client, input);
                } else {
                        handleEmberSimSimple(client, input);
                }
        }

        private void handleEmberSimSimple(com.benleskey.textengine.Client client, CommandInput input) {
                int poolSize = input.get(M_POOL_SIZE);
                int difficulty = input.get(M_DIFFICULTY);
                int samples = input.get(M_SAMPLES);

                if (samples <= 0) {
                        samples = 1000000;
                }
                if (difficulty <= 0) {
                        CommandOutput errorOutput = CommandOutput.make(DEBUG_EMBER_SIM)
                                        .error("invalid_difficulty")
                                        .text(Markup.escape("Error: difficulty must be >= 1"));
                        client.sendOutput(errorOutput);
                        return;
                }
                if (poolSize < 0) {
                        CommandOutput errorOutput = CommandOutput.make(DEBUG_EMBER_SIM)
                                        .error("invalid_pool")
                                        .text(Markup.escape("Error: pool size must be >= 0"));
                        client.sendOutput(errorOutput);
                        return;
                }
                EmberPoolDiceRoll roll = new EmberPoolDiceRoll(poolSize);

                // Track deltas instead of fixed categories
                java.util.Map<Integer, Integer> deltaCount = new java.util.TreeMap<>();
                int successCount = 0;
                int minDelta = Integer.MAX_VALUE;
                int maxDelta = Integer.MIN_VALUE;

                Random random = new Random();

                for (int i = 0; i < samples; i++) {
                        DiceSystem.PoolDiceResult result = diceSystem.rollPool(random, roll);
                        DiceSystem.Outcome outcome = result.getOutcome(difficulty);
                        int delta = outcome.getDelta();

                        deltaCount.put(delta, deltaCount.getOrDefault(delta, 0) + 1);
                        if (outcome.isSuccess()) {
                                successCount++;
                        }
                        minDelta = Math.min(minDelta, delta);
                        maxDelta = Math.max(maxDelta, delta);
                }

                // Build response with all delta values
                CommandOutput output = CommandOutput.make(DEBUG_EMBER_SIM)
                                .put(M_POOL_SIZE, poolSize)
                                .put(M_DIFFICULTY, difficulty)
                                .put(M_SAMPLES, samples)
                                .put(M_SUCCESS, successCount)
                                .put(M_MIN_DELTA, minDelta)
                                .put(M_MAX_DELTA, maxDelta);

                // Add individual delta counts to output with cumulative probabilities
                StringBuilder deltaStr = new StringBuilder();
                int cumulativeCount = 0;
                // Iterate in reverse to accumulate from highest delta downward (P(delta >= x))
                for (int delta = maxDelta; delta >= minDelta; delta--) {
                        int count = deltaCount.getOrDefault(delta, 0);
                        if (count == 0) {
                                continue; // Skip deltas with no samples
                        }
                        cumulativeCount += count;
                        double pct = count / (double) samples * 100.0;
                        double cumulativePct = cumulativeCount / (double) samples * 100.0;
                        output.put("delta_" + delta, count);
                        output.put("cumulative_delta_" + delta, cumulativeCount);
                        if (deltaStr.length() > 0) {
                                deltaStr.append("\n");
                        }
                        deltaStr.append(String.format("  Δ %+d: %d (%.3f%%) | P(Δ ≥ %+d): %.3f%%",
                                        delta, count, pct, delta, cumulativePct));
                }

                String textContent = String.format(
                                "Ember Simulation %dd%d (difficulty %d, threshold %d, explosion at %d) sampled over %d rolls:\nDelta distribution (successes - requirement):\n%s\nRange: [%d, %d]\nSuccess: %d (%.3f%%)",
                                poolSize,
                                roll.dieSize,
                                difficulty,
                                roll.threshold,
                                roll.explosionThreshold,
                                samples,
                                deltaStr.toString(),
                                minDelta,
                                maxDelta, successCount,
                                successCount / (double) samples * 100.0);

                output.text(Markup.escape(textContent));
                client.sendOutput(output);
        }

        private void handleEmberSimContested(com.benleskey.textengine.Client client, CommandInput input) {
                int poolSizeA = input.get(M_POOL_SIZE_A);
                int poolSizeB = input.get(M_POOL_SIZE_B);
                int samples = input.get(M_SAMPLES);

                if (samples <= 0) {
                        samples = 1000000;
                }
                if (poolSizeA < 0 || poolSizeB < 0) {
                        CommandOutput errorOutput = CommandOutput.make(DEBUG_EMBER_SIM)
                                        .error("invalid_pool")
                                        .text(Markup.escape("Error: pool sizes must be >= 0"));
                        client.sendOutput(errorOutput);
                        return;
                }

                EmberPoolDiceRoll rollA = new EmberPoolDiceRoll(poolSizeA);
                EmberPoolDiceRoll rollB = new EmberPoolDiceRoll(poolSizeB);

                // Track deltas: positive = A wins, negative = B wins
                java.util.Map<Integer, Integer> deltaCount = new java.util.TreeMap<>();
                int aWins = 0;
                int bWins = 0;
                int ties = 0;
                int minDelta = Integer.MAX_VALUE;
                int maxDelta = Integer.MIN_VALUE;

                Random random = new Random();

                for (int i = 0; i < samples; i++) {
                        DiceSystem.PoolDiceResult resultA = diceSystem.rollPool(random, rollA);
                        DiceSystem.PoolDiceResult resultB = diceSystem.rollPool(random, rollB);
                        int successesA = resultA.successes;
                        int successesB = resultB.successes;
                        int delta = successesA - successesB;

                        deltaCount.put(delta, deltaCount.getOrDefault(delta, 0) + 1);
                        if (delta >= 0) {
                                // Initiators (A) win ties
                                aWins++;
                        } else {
                                bWins++;
                        }
                        if (delta == 0) {
                                ties++;
                        }
                        minDelta = Math.min(minDelta, delta);
                        maxDelta = Math.max(maxDelta, delta);
                }

                // Build response with all delta values
                CommandOutput output = CommandOutput.make(DEBUG_EMBER_SIM)
                                .put(M_POOL_SIZE_A, poolSizeA)
                                .put(M_POOL_SIZE_B, poolSizeB)
                                .put(M_SAMPLES, samples)
                                .put("a_wins", aWins)
                                .put("b_wins", bWins)
                                .put("ties", ties)
                                .put(M_MIN_DELTA, minDelta)
                                .put(M_MAX_DELTA, maxDelta);

                // Add individual delta counts to output with cumulative probabilities
                StringBuilder deltaStr = new StringBuilder();

                // Build cumulative totals for A: count how many outcomes give A an advantage of
                // at
                // least delta
                // For a given threshold X, cumulative = count of all deltas >= X
                java.util.Map<Integer, Integer> cumulativeA = new java.util.TreeMap<>();
                for (int threshold = minDelta; threshold <= maxDelta; threshold++) {
                        int count = 0;
                        for (int delta = threshold; delta <= maxDelta; delta++) {
                                count += deltaCount.getOrDefault(delta, 0);
                        }
                        cumulativeA.put(threshold, count);
                }

                // Build cumulative totals for B: count how many outcomes give B an advantage of
                // at
                // least X (i.e., delta <= -X)
                java.util.Map<Integer, Integer> cumulativeB = new java.util.TreeMap<>();
                for (int threshold = 1; threshold <= maxDelta; threshold++) {
                        int count = 0;
                        for (int delta = -threshold; delta >= minDelta; delta--) {
                                count += deltaCount.getOrDefault(delta, 0);
                        }
                        cumulativeB.put(threshold, count);
                }

                // Iterate and display each delta
                for (int delta = maxDelta; delta >= minDelta; delta--) {
                        int count = deltaCount.getOrDefault(delta, 0);
                        if (count == 0) {
                                continue;
                        }

                        double pct = count / (double) samples * 100.0;

                        // P(A ≥ delta): probability A wins by at least delta
                        int cumulativeDeltaA = cumulativeA.get(delta);
                        double cumulativePctA = cumulativeDeltaA / (double) samples * 100.0;

                        // P(B ≥ -delta): probability B wins by at least -delta (equivalent to delta ≤
                        // -(-delta))
                        int positiveDelta = Math.abs(delta);
                        int cumulativeDeltaB = cumulativeB.getOrDefault(positiveDelta, 0);
                        double cumulativePctB = cumulativeDeltaB / (double) samples * 100.0;

                        output.put("delta_" + delta, count);
                        output.put("cumulative_delta_" + delta, cumulativeDeltaA);

                        if (deltaStr.length() > 0) {
                                deltaStr.append("\n");
                        }

                        String label = delta >= 0 ? String.format("A +%d", delta)
                                        : String.format("B +%d", -delta);

                        String line;
                        if (delta >= 0) {
                                // For A wins (including ties), show only A's cumulative
                                line = String.format("  %s: %d (%.3f%%) | P(A ≥ %+d): %.3f%%",
                                                label, count, pct, delta, cumulativePctA);
                        } else {
                                // For B wins (delta < 0), show only B's cumulative
                                line = String.format("  %s: %d (%.3f%%) | P(B ≥ %+d): %.3f%%",
                                                label, count, pct, -delta, cumulativePctB);
                        }
                        deltaStr.append(line);
                }

                String textContent = String.format(
                                "Contested Simulation: %dd%d (A) vs %d%d (B) sampled over %d rolls:\nA wins (including ties): %d (%.3f%%)\nB wins: %d (%.3f%%)\n\nDelta distribution (A successes - B successes):\n%s\nRange: [%d, %d]",
                                poolSizeA,
                                rollA.dieSize,
                                poolSizeB,
                                rollB.dieSize,
                                samples,
                                aWins,
                                aWins / (double) samples * 100.0,
                                bWins,
                                bWins / (double) samples * 100.0,
                                deltaStr.toString(),
                                minDelta,
                                maxDelta);

                output.text(Markup.escape(textContent));
                client.sendOutput(output);
        }
}
