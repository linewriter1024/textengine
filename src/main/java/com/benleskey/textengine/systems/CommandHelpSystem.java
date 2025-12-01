package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CommandHelpSystem maintains a registry of command help entries with a
 * standardized syntax format. Each command can have multiple syntax lines
 * (for variants) and an optional description.
 *
 * <p>
 * Syntax format:
 * 
 * <pre>
 * required words [optional words] &lt;parameter1&gt; &lt;parameter two&gt; [&lt;optional parameter 3&gt;] [optional word with &lt;parameter&gt;]
 * </pre>
 *
 * <p>
 * The command name is extracted from the first word(s) of the syntax line.
 * This is used for command completion and help lookup.
 */
public class CommandHelpSystem extends SingletonGameSystem {

    /**
     * A registered help entry for a command.
     * Can have multiple syntax lines for different variants.
     */
    public record HelpEntry(List<String> syntaxLines, String description, String commandToken) {
        /**
         * Get the full help text (all syntax lines + description if present).
         */
        public String getFullHelp() {
            StringBuilder sb = new StringBuilder();
            for (String syntax : syntaxLines) {
                sb.append(syntax).append("\n");
            }
            if (description != null && !description.isEmpty()) {
                sb.append(description);
            } else {
                // Remove trailing newline if no description
                if (!sb.isEmpty()) {
                    sb.setLength(sb.length() - 1);
                }
            }
            return sb.toString();
        }

        /**
         * Get all syntax lines formatted for display.
         */
        public String getSyntaxDisplay() {
            return String.join("\n", syntaxLines);
        }
    }

    /**
     * Result of a help lookup, indicating whether it was an exact match.
     */
    public record HelpMatch(HelpEntry entry, boolean exactMatch) {
    }

    /**
     * Map of command token (lowercase first word) to help entry.
     */
    private final Map<String, HelpEntry> helpEntries = new LinkedHashMap<>();

    /**
     * Pattern to match the command token at the beginning of a syntax line.
     * Matches all contiguous required words (not in brackets or angle brackets).
     */
    private static final Pattern COMMAND_TOKEN_PATTERN = Pattern.compile("^([a-zA-Z0-9:_-]+(?:\\s+[a-zA-Z0-9:_-]+)*)");

    public CommandHelpSystem(Game game) {
        super(game);
    }

    /**
     * Register help for a command with a single syntax line.
     *
     * @param syntax      The syntax line, e.g., "take &lt;item&gt;" or "go [to]
     *                    &lt;place&gt;"
     * @param description Optional additional description (can include markup)
     */
    public void registerHelp(String syntax, String description) {
        registerHelp(List.of(syntax), description);
    }

    /**
     * Register help for a command with multiple syntax lines (for variants).
     *
     * @param syntaxLines The syntax lines for different variants
     * @param description Optional additional description (can include markup)
     */
    public void registerHelp(List<String> syntaxLines, String description) {
        Objects.requireNonNull(syntaxLines, "syntaxLines cannot be null");
        if (syntaxLines.isEmpty()) {
            throw new IllegalArgumentException("syntaxLines cannot be empty");
        }

        // Extract command token from first syntax line
        String commandToken = extractCommandToken(syntaxLines.get(0));
        if (commandToken == null || commandToken.isEmpty()) {
            throw new IllegalArgumentException("Could not extract command token from syntax: " + syntaxLines.get(0));
        }

        // Get just the first word for the key
        String firstWord = commandToken.split("\\s+")[0].toLowerCase();

        // Check if we already have an entry for this command - if so, merge
        HelpEntry existing = helpEntries.get(firstWord);
        if (existing != null) {
            List<String> mergedSyntax = new ArrayList<>(existing.syntaxLines());
            mergedSyntax.addAll(syntaxLines);
            String mergedDesc = existing.description();
            if (description != null && !description.isEmpty()) {
                mergedDesc = (mergedDesc != null && !mergedDesc.isEmpty())
                        ? mergedDesc + "\n" + description
                        : description;
            }
            helpEntries.put(firstWord, new HelpEntry(mergedSyntax, mergedDesc, commandToken));
        } else {
            helpEntries.put(firstWord, new HelpEntry(new ArrayList<>(syntaxLines), description, commandToken));
        }
    }

    /**
     * Register help for a command with syntax only.
     *
     * @param syntax The syntax line
     */
    public void registerHelp(String syntax) {
        registerHelp(syntax, null);
    }

    /**
     * Extract the command token from a syntax line.
     * The command token is the first sequence of required words
     * (words not enclosed in [] or <>).
     *
     * @param syntax The syntax line
     * @return The command token (first word or words before optional/parameter)
     */
    private String extractCommandToken(String syntax) {
        if (syntax == null || syntax.isEmpty()) {
            return null;
        }

        String trimmed = syntax.trim();

        // Find the first word or contiguous required words
        Matcher matcher = COMMAND_TOKEN_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            String match = matcher.group(1);
            // Verify that what follows (if anything) is optional or a parameter
            int end = matcher.end();
            if (end >= trimmed.length()) {
                return match;
            }
            // Check if next non-space char is [ or <
            String rest = trimmed.substring(end).stripLeading();
            if (rest.isEmpty() || rest.startsWith("[") || rest.startsWith("<")) {
                return match;
            }
            // Otherwise, just return the first word
            String[] parts = match.split("\\s+");
            return parts[0];
        }

        return null;
    }

    /**
     * Get all registered command tokens for completion.
     *
     * @return Set of command tokens (first words of syntax lines)
     */
    public Set<String> getCommandTokens() {
        Set<String> tokens = new TreeSet<>();
        for (HelpEntry entry : helpEntries.values()) {
            // Just return the first word for completion
            String[] parts = entry.commandToken().split("\\s+");
            tokens.add(parts[0]);
        }
        return tokens;
    }

    /**
     * Get help entry for an exact command token match.
     *
     * @param commandToken The command token to look up
     * @return The help entry, or null if not found
     */
    public HelpEntry getHelp(String commandToken) {
        if (commandToken == null) {
            return null;
        }
        return helpEntries.get(commandToken.toLowerCase());
    }

    /**
     * Find the best matching help entry for a partial command.
     * Uses prefix matching to find the most relevant help.
     *
     * @param input The user input to match against
     * @return A HelpMatch with the entry and whether it was an exact match, or null
     *         if no match
     */
    public HelpMatch findBestMatch(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        String search = input.trim().toLowerCase();
        String[] inputParts = search.split("\\s+");
        String firstWord = inputParts[0];

        // First, try exact match on first word - this means the command is correct but
        // syntax is wrong
        HelpEntry exact = helpEntries.get(firstWord);
        if (exact != null) {
            return new HelpMatch(exact, true);
        }

        // Try starts-with match on command tokens (fuzzy match for typos)
        HelpEntry bestMatch = null;
        int bestPrefixLength = 0;

        for (HelpEntry entry : helpEntries.values()) {
            String token = entry.commandToken().toLowerCase();
            String[] tokenParts = token.split("\\s+");
            String tokenFirstWord = tokenParts[0];

            // Calculate common prefix length between first words
            int prefixLength = commonPrefixLength(firstWord, tokenFirstWord);

            // Only consider it a match if we have at least 2 characters in common
            if (prefixLength >= 2 && prefixLength > bestPrefixLength) {
                bestPrefixLength = prefixLength;
                bestMatch = entry;
            }
        }

        return bestMatch != null ? new HelpMatch(bestMatch, false) : null;
    }

    /**
     * Calculate the length of the common prefix between two strings.
     */
    private int commonPrefixLength(String a, String b) {
        int minLen = Math.min(a.length(), b.length());
        int i = 0;
        while (i < minLen && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }

    /**
     * Get all help entries.
     *
     * @return Collection of all help entries
     */
    public Collection<HelpEntry> getAllHelp() {
        return Collections.unmodifiableCollection(helpEntries.values());
    }
}
