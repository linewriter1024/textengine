package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;

import java.util.*;
import java.util.function.Supplier;

/**
 * CommandCompletionSystem maintains a registry of completion suggestions for
 * command words and arguments. Top-level command completions are derived from
 * the CommandHelpSystem based on registered help syntax lines.
 */
public class CommandCompletionSystem extends SingletonGameSystem implements OnSystemInitialize {

    /**
     * command token -> arg index -> list of suppliers (dynamic or constant).
     */
    private final Map<String, Map<Integer, List<Supplier<Collection<String>>>>> completions = new HashMap<>();

    /**
     * regex (search) -> arg index -> list of suppliers. Used when a plugin wants
     * to register completions against a pattern that matches some portion of
     * the input (e.g. a variant parameter), rather than the first word.
     */
    private final Map<java.util.regex.Pattern, Map<Integer, List<Supplier<Collection<String>>>>> regexCompletions = new HashMap<>();

    public CommandCompletionSystem(Game game) {
        super(game);
    }

    @Override
    public void onSystemInitialize() {
        // Top-level completions are now derived from CommandHelpSystem
        // No automatic regex parsing - plugins must register help explicitly
    }

    /**
     * Register suggestions for a given command token and argument index. This is
     * additive; previous suggestions are preserved unless replaced via
     * setCompletionsForCommand.
     */
    public void registerCompletion(String commandToken, int argIndex, Collection<String> suggestions) {
        registerCompletion(commandToken, argIndex, () -> suggestions);
    }

    public void registerCompletion(String commandToken, int argIndex,
            Supplier<Collection<String>> suggestionsSupplier) {
        Objects.requireNonNull(commandToken);
        Objects.requireNonNull(suggestionsSupplier);
        String cmd = commandToken.toLowerCase();
        Map<Integer, List<Supplier<Collection<String>>>> m = completions.computeIfAbsent(cmd, k -> new HashMap<>());
        List<Supplier<Collection<String>>> existing = m.getOrDefault(argIndex, new ArrayList<>());

        existing.add(suggestionsSupplier);
        m.put(argIndex, existing);
        if (argIndex != 0) {
            m.computeIfAbsent(0, k -> new ArrayList<>()).add(() -> List.of(cmd));
        }
    }

    /**
     * Register multiple argument suppliers for a command token.
     */
    public void registerCompletionsFromSuppliers(String commandToken,
            Map<Integer, Supplier<Collection<String>>> argSuppliers) {
        for (Map.Entry<Integer, Supplier<Collection<String>>> e : argSuppliers.entrySet()) {
            registerCompletion(commandToken, e.getKey(), e.getValue());
        }
    }

    public void registerCompletions(String commandToken, Map<Integer, Collection<String>> argSuggestions) {
        for (Map.Entry<Integer, Collection<String>> e : argSuggestions.entrySet()) {
            registerCompletion(commandToken, e.getKey(), e.getValue());
        }
    }

    public void registerCompletionsForCommandToken(String commandToken, Supplier<Collection<String>> topLevel,
            Map<Integer, Supplier<Collection<String>>> argSuppliers) {
        if (topLevel != null) {
            registerCompletion(commandToken, 0, topLevel);
        }
        if (argSuppliers != null) {
            registerCompletionsFromSuppliers(commandToken, argSuppliers);
        }
    }

    /**
     * Register suggestions for a given regex (used as a search pattern) and
     * argument index.
     * NOTE: the provided regex will be used as a search (Pattern.find()) against
     * the entire
     * input line, not a strict full-line match. This allows plugins to register
     * patterns that
     * match only a portion of the input (e.g. a specific argument position) rather
     * than
     * copy the full command variant regex.
     */
    public void registerCompletionForRegex(String regex, int argIndex,
            Supplier<Collection<String>> suggestionsSupplier) {
        Objects.requireNonNull(regex);
        Objects.requireNonNull(suggestionsSupplier);
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
        Map<Integer, List<Supplier<Collection<String>>>> m = regexCompletions.computeIfAbsent(p, k -> new HashMap<>());
        List<Supplier<Collection<String>>> existing = m.getOrDefault(argIndex, new ArrayList<>());
        existing.add(suggestionsSupplier);
        m.put(argIndex, existing);
    }

    public void registerCompletionsForRegex(String regex, Supplier<Collection<String>> topLevel,
            Map<Integer, Supplier<Collection<String>>> argSuppliers) {
        if (topLevel != null) {
            registerCompletionForRegex(regex, 0, topLevel);
        }
        if (argSuppliers != null) {
            for (Map.Entry<Integer, Supplier<Collection<String>>> e : argSuppliers.entrySet()) {
                registerCompletionForRegex(regex, e.getKey(), e.getValue());
            }
        }
    }

    public List<String> getCompletions(String commandToken, int argIndex) {
        if (commandToken == null) {
            return List.of();
        }
        Map<Integer, List<Supplier<Collection<String>>>> m = completions.getOrDefault(commandToken.toLowerCase(),
                Map.of());
        List<Supplier<Collection<String>>> suppliers = m.getOrDefault(argIndex, List.of());
        Set<String> results = new LinkedHashSet<>();
        for (Supplier<Collection<String>> s : suppliers) {
            Collection<String> c = s.get();
            if (c != null)
                results.addAll(c);
        }
        return new ArrayList<>(results);
    }

    /**
     * Get completions for a given entire input line and argument index. This will
     * return completions from both token-based registrations (by first word) and
     * regex-based registrations that match the entire line.
     */
    public List<String> getCompletionsForLine(String entireLine, int argIndex) {
        Set<String> results = new LinkedHashSet<>();
        if (entireLine == null) {
            return new ArrayList<>(results);
        }

        // Token-based: derive first word if present
        String trimmed = entireLine.trim();
        if (!trimmed.isEmpty()) {
            String[] words = trimmed.split("\\s+");
            String cmd = words[0];
            results.addAll(getCompletions(cmd, argIndex));
        }

        // Regex-based: match patterns against the whole line; NOTE: we use a partial
        // match
        // (matcher.find()) so plugin regexes may target a single portion of the input
        // line
        // rather than requiring the entire line to match the variant regex.
        for (Map.Entry<java.util.regex.Pattern, Map<Integer, List<Supplier<Collection<String>>>>> e : regexCompletions
                .entrySet()) {
            java.util.regex.Pattern pattern = e.getKey();
            if (pattern.matcher(entireLine).find()) {
                Map<Integer, List<Supplier<Collection<String>>>> m = e.getValue();
                List<Supplier<Collection<String>>> suppliers = m.getOrDefault(argIndex, List.of());
                for (Supplier<Collection<String>> s : suppliers) {
                    Collection<String> c = s.get();
                    if (c != null)
                        results.addAll(c);
                }
            }
        }

        return new ArrayList<>(results);
    }

    public Set<String> getTopLevelTokens() {
        Set<String> tokens = new TreeSet<>();

        // Get tokens from CommandHelpSystem
        CommandHelpSystem helpSystem = game.getSystem(CommandHelpSystem.class);
        tokens.addAll(helpSystem.getCommandTokens());

        // Also include any explicitly registered completions
        for (Map.Entry<String, Map<Integer, List<Supplier<Collection<String>>>>> e : completions.entrySet()) {
            Map<Integer, List<Supplier<Collection<String>>>> argMap = e.getValue();
            List<Supplier<Collection<String>>> topSuppliers = argMap.getOrDefault(0, Collections.emptyList());
            for (Supplier<Collection<String>> s : topSuppliers) {
                Collection<String> coll = s.get();
                if (coll != null)
                    tokens.addAll(coll);
            }
        }
        return tokens;
    }
}
