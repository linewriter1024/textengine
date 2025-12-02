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
 * Includes the debug:embersim command for contested rolls using
 * Ember's default thresholds
 */
public class EmberDicePlugin extends Plugin implements OnPluginInitialize {

        public static final String DEBUG_EMBER_SIM = "debug:embersim";
        public static final String M_POOL_SIZE_A = "pool_size_a";
        public static final String M_POOL_SIZE_B = "pool_size_b";
        public static final String M_SAMPLES = "samples";
        public static final String M_MIN_DELTA = "min_delta";
        public static final String M_MAX_DELTA = "max_delta";
        public static final String M_THRESHOLD_DECREASE_A = "threshold_decrease_a";
        public static final String M_EXPLOSION_THRESHOLD_DECREASE_A = "explosion_threshold_decrease_a";
        public static final String M_THRESHOLD_DECREASE_B = "threshold_decrease_b";
        public static final String M_EXPLOSION_THRESHOLD_DECREASE_B = "explosion_threshold_decrease_b";

        private DiceSystem diceSystem;

        public EmberDicePlugin(Game game) {
                super(game);
        }

        @Override
        public void onPluginInitialize() {
                diceSystem = game.getSystem(DiceSystem.class);

                // Register help
                CommandHelpSystem helpSystem = game.getSystem(CommandHelpSystem.class);
                helpSystem.registerHelp("debug:embersim <poolA> <poolB> [samples]",
                                "Simulate contested rolls: poolA vs poolB. Returns delta distribution (poolA - poolB).");
                helpSystem.registerHelp(
                                "debug:embersim <poolA> <thresholdDecreaseA> <explosionDecreaseA> <poolB> <thresholdDecreaseB> <explosionDecreaseB> [samples]",
                                "Simulate contested rolls with custom Ember offsets. Returns delta distribution (poolA - poolB).");

                // Register ember simulation command with support for both syntaxes
                game.registerCommand(new Command(DEBUG_EMBER_SIM, this::handleEmberSim,
                                new CommandVariant("ember_sim_contested_samples_offsets",
                                                "^debug:embersim\\s+(\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s+(\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s+(\\d+)\\s*$",
                                                args -> {
                                                        int poolSizeA = Integer.parseInt(args.group(1));
                                                        int thresholdDecreaseA = Integer.parseInt(args.group(2));
                                                        int explosionDecreaseA = Integer.parseInt(args.group(3));
                                                        int poolSizeB = Integer.parseInt(args.group(4));
                                                        int thresholdDecreaseB = Integer.parseInt(args.group(5));
                                                        int explosionDecreaseB = Integer.parseInt(args.group(6));
                                                        int samples = Integer.parseInt(args.group(7));
                                                        return CommandInput.makeNone()
                                                                        .put(M_POOL_SIZE_A, poolSizeA)
                                                                        .put(M_THRESHOLD_DECREASE_A, thresholdDecreaseA)
                                                                        .put(M_EXPLOSION_THRESHOLD_DECREASE_A,
                                                                                        explosionDecreaseA)
                                                                        .put(M_POOL_SIZE_B, poolSizeB)
                                                                        .put(M_THRESHOLD_DECREASE_B, thresholdDecreaseB)
                                                                        .put(M_EXPLOSION_THRESHOLD_DECREASE_B,
                                                                                        explosionDecreaseB)
                                                                        .put(M_SAMPLES, samples)
                                                                        .put("mode", "contested_offsets");
                                                }),
                                new CommandVariant("ember_sim_contested_offsets",
                                                "^debug:embersim\\s+(\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s+(\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s*$",
                                                args -> {
                                                        int poolSizeA = Integer.parseInt(args.group(1));
                                                        int thresholdDecreaseA = Integer.parseInt(args.group(2));
                                                        int explosionDecreaseA = Integer.parseInt(args.group(3));
                                                        int poolSizeB = Integer.parseInt(args.group(4));
                                                        int thresholdDecreaseB = Integer.parseInt(args.group(5));
                                                        int explosionDecreaseB = Integer.parseInt(args.group(6));
                                                        int samples = 1000000;
                                                        return CommandInput.makeNone()
                                                                        .put(M_POOL_SIZE_A, poolSizeA)
                                                                        .put(M_THRESHOLD_DECREASE_A, thresholdDecreaseA)
                                                                        .put(M_EXPLOSION_THRESHOLD_DECREASE_A,
                                                                                        explosionDecreaseA)
                                                                        .put(M_POOL_SIZE_B, poolSizeB)
                                                                        .put(M_THRESHOLD_DECREASE_B, thresholdDecreaseB)
                                                                        .put(M_EXPLOSION_THRESHOLD_DECREASE_B,
                                                                                        explosionDecreaseB)
                                                                        .put(M_SAMPLES, samples)
                                                                        .put("mode", "contested_offsets");
                                                }),
                                new CommandVariant("ember_sim_contested_samples",
                                                "^debug:embersim\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*$",
                                                args -> {
                                                        int poolSizeA = Integer.parseInt(args.group(1));
                                                        int poolSizeB = Integer.parseInt(args.group(2));
                                                        int samples = Integer.parseInt(args.group(3));
                                                        return CommandInput.makeNone()
                                                                        .put(M_POOL_SIZE_A, poolSizeA)
                                                                        .put(M_THRESHOLD_DECREASE_A, 0)
                                                                        .put(M_EXPLOSION_THRESHOLD_DECREASE_A, 0)
                                                                        .put(M_POOL_SIZE_B, poolSizeB)
                                                                        .put(M_THRESHOLD_DECREASE_B, 0)
                                                                        .put(M_EXPLOSION_THRESHOLD_DECREASE_B, 0)
                                                                        .put(M_SAMPLES, samples)
                                                                        .put("mode", "contested");
                                                }),
                                new CommandVariant("ember_sim_contested",
                                                "^debug:embersim\\s+(\\d+)\\s+(\\d+)\\s*$",
                                                args -> {
                                                        int poolSizeA = Integer.parseInt(args.group(1));
                                                        int poolSizeB = Integer.parseInt(args.group(2));
                                                        int samples = 1000000;
                                                        return CommandInput.makeNone()
                                                                        .put(M_POOL_SIZE_A, poolSizeA)
                                                                        .put(M_THRESHOLD_DECREASE_A, 0)
                                                                        .put(M_EXPLOSION_THRESHOLD_DECREASE_A, 0)
                                                                        .put(M_POOL_SIZE_B, poolSizeB)
                                                                        .put(M_THRESHOLD_DECREASE_B, 0)
                                                                        .put(M_EXPLOSION_THRESHOLD_DECREASE_B, 0)
                                                                        .put(M_SAMPLES, samples)
                                                                        .put("mode", "contested");
                                                })));
        }

        private void handleEmberSim(com.benleskey.textengine.Client client, CommandInput input) {
                String mode = input.get("mode");
                if ("contested".equals(mode) || "contested_offsets".equals(mode)) {
                        handleEmberSimContested(client, input);
                }
        }

        private void handleEmberSimContested(com.benleskey.textengine.Client client, CommandInput input) {
                int poolSizeA = input.get(M_POOL_SIZE_A);
                int poolSizeB = input.get(M_POOL_SIZE_B);
                int thresholdDecreaseA = input.get(M_THRESHOLD_DECREASE_A);
                int explosionThresholdDecreaseA = input.get(M_EXPLOSION_THRESHOLD_DECREASE_A);
                int thresholdDecreaseB = input.get(M_THRESHOLD_DECREASE_B);
                int explosionThresholdDecreaseB = input.get(M_EXPLOSION_THRESHOLD_DECREASE_B);
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

                EmberPoolDiceRoll rollA = EmberPoolDiceRoll.fromOffsets(poolSizeA, thresholdDecreaseA,
                                explosionThresholdDecreaseA);
                EmberPoolDiceRoll rollB = EmberPoolDiceRoll.fromOffsets(poolSizeB, thresholdDecreaseB,
                                explosionThresholdDecreaseB);
                DiceSystem.ContestedPoolDiceRoll contestedRoll = new DiceSystem.ContestedPoolDiceRoll(rollA, rollB);

                // Track deltas: positive = A wins, negative = B wins
                java.util.Map<Integer, Integer> deltaCount = new java.util.TreeMap<>();
                int aWins = 0;
                int bWins = 0;
                int ties = 0;
                int minDelta = Integer.MAX_VALUE;
                int maxDelta = Integer.MIN_VALUE;

                Random random = new Random();

                for (int i = 0; i < samples; i++) {
                        DiceSystem.ContestedPoolDiceResult contestedResult = diceSystem.rollContestedPool(random,
                                        contestedRoll);
                        int delta = contestedResult.getDelta();

                        deltaCount.put(delta, deltaCount.getOrDefault(delta, 0) + 1);
                        if (contestedResult.initiatorWon()) {
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
                                "Contested Simulation: %dd%d (A) vs %dd%d (B) sampled over %d rolls:\nA wins (including ties): %d (%.3f%%)\nB wins: %d (%.3f%%)\n\nDelta distribution (A successes - B successes):\n%s\nRange: [%d, %d]",
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
