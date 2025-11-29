package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;

import java.util.*;
import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.ToString;

/**
 * NameGenerationSystem
 *
 * Generates unique, style-aware names and persists them to ensure
 * uniqueness. Name styles are registered by plugins; if no style is
 * registered for a requested name, the system will fall back to a
 * built-in default syllable set.
 *
 * Behavior:
 * - Names are generated using style syllables and a small/short bias.
 * - Names are persisted in the 'unique_name' groupless properties table
 * via an atomic insert-if-absent to guarantee uniqueness.
 * - When many collisions occur for short names, the generator grows names
 * by appending additional style syllables (TOK_SYLLABLES) to increase
 * uniqueness. No numeric or base36 fallbacks are used.
 */
public class NameGenerationSystem extends SingletonGameSystem implements OnSystemInitialize {

    private final GrouplessPropertiesSubSystem<String, Long> names;
    private final Map<String, NameStyle> styles;
    public static final String TOK_ROOTS = "roots";
    public static final String TOK_SUFFIXES = "suffixes";
    public static final String TOK_SYLLABLES = "syllables";

    public NameGenerationSystem(Game game) {
        super(game);

        names = game.registerSystem(new GrouplessPropertiesSubSystem<>(game, "unique_name",
                PropertiesSubSystem.stringHandler(), PropertiesSubSystem.longHandler()));
        styles = new HashMap<>();

    }

    @Override
    public void onSystemInitialize() {
    }

    /**
     * Generate a new, unique name in the given style. If the style is not
     * registered the system falls back to its default syllables. The method
     * writes the generated name to the DB to ensure uniqueness and will
     * increase the generated candidate's length by appending TOK_SYLLABLES when
     * concise names repeatedly collide (no numeric fallbacks).
     *
     * @param style  style identifier registered via {@link #registerStyle}
     * @param random caller-provided RNG (seeded for determinism when desired)
     * @return a unique name
     */
    public synchronized String generateName(String style, Random random) {

        NameStyle nameStyle = styles.get(style);
        if (nameStyle == null) {
            throw new InternalException("No name style registered for: " + style);
        }
        Map<String, List<String>> tokens = nameStyle.getTokens();

        int attempts = 0;
        int maxAttemptsBeforeGrowing = 200;
        while (true) {
            attempts++;

            String candidate;

            // Tokens map passed via NameStyle
            Map<String, List<String>> tokensLocal = tokens;

            // Build shared token lists used in both candidate generation and later growth
            List<String> syllables = null; // used for building syllable-style names and for adding filler
            List<String> roots = null;
            List<String> suffixes = null;
            List<String> styleSyllables = null; // filler syllables token for ROOT_SUFFIX

            if (nameStyle.getType() == NameStyleType.ROOT_SUFFIX) {
                // Use root + suffix model
                if (tokensLocal == null) {
                    throw new InternalException("ROOT_SUFFIX style must include tokens for style '" + style + "'");
                }
                roots = tokensLocal.get(TOK_ROOTS);
                suffixes = tokensLocal.get(TOK_SUFFIXES);
                if (roots == null || roots.isEmpty()) {
                    throw new InternalException("ROOT_SUFFIX style must define '" + TOK_ROOTS + "' tokens");
                }
                if (suffixes == null || suffixes.isEmpty()) {
                    throw new InternalException("ROOT_SUFFIX style must define '" + TOK_SUFFIXES + "' tokens");
                }

                styleSyllables = (tokensLocal != null) ? tokensLocal.get(TOK_SYLLABLES) : null;
                candidate = buildRootSuffixCandidate(roots, suffixes, styleSyllables, random, attempts);
                // Prepare syllables for fallback encoding; prefer TOK_SYLLABLES if provided
                syllables = (tokensLocal != null) ? tokensLocal.get(TOK_SYLLABLES) : null;
                if (syllables == null || syllables.isEmpty()) {
                    // Fail early: we expect TOK_SYLLABLES to be provided for all styles
                    throw new InternalException("Style '" + style + "' has no syllables registered");
                }
            } else {
                int syllableCount = chooseSyllableCount(random, attempts);

                StringBuilder sb = new StringBuilder();
                // Look up syllables tokens for default SYLLABLE style
                syllables = (tokensLocal != null) ? tokensLocal.get(TOK_SYLLABLES) : null;
                if (syllables == null || syllables.isEmpty()) {
                    throw new InternalException("Style '" + style + "' has no syllables registered");
                }
                for (int i = 0; i < syllableCount; i++) {
                    String s = syllables.get(random.nextInt(syllables.size()));
                    if (i == 0) {

                        sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
                    } else {
                        sb.append(s);
                    }
                }

                candidate = sb.toString();
            }

            long id = game.getNewGlobalId();
            if (names.insertIfAbsent(candidate, id)) {
                return candidate;
            } else {
                if (attempts > maxAttemptsBeforeGrowing) {
                    // Grow names only by using available TOK_SYLLABLES tokens (no numeric / base36
                    // fallbacks)
                    long id2 = game.getNewGlobalId();
                    String alt;
                    if (nameStyle.getType() == NameStyleType.ROOT_SUFFIX) {
                        // Create a longer variant or force additional filler syllables
                        alt = buildRootSuffixCandidate(roots, suffixes, styleSyllables, random, attempts);
                        // Append extra syllables based on attempts to ensure uniqueness and growth
                        int extra = 1 + (attempts - maxAttemptsBeforeGrowing) / 100;
                        for (int i = 0; i < extra; i++) {
                            String filler = syllables.get(random.nextInt(syllables.size()));
                            if (alt.length() > 0 && filler.length() > 0
                                    && alt.charAt(alt.length() - 1) == filler.charAt(0)) {
                                alt = alt + filler.substring(1);
                            } else {
                                alt = alt + filler;
                            }
                        }
                    } else {
                        // For SYLLABLE style: simply create a bigger syllable count than usual
                        int syllableCount = chooseSyllableCount(random, attempts) + 1
                                + (attempts - maxAttemptsBeforeGrowing) / 100;
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < syllableCount; i++) {
                            String s = syllables.get(random.nextInt(syllables.size()));
                            if (i == 0) {
                                sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
                            } else {
                                sb.append(s);
                            }
                        }
                        alt = sb.toString();
                    }
                    if (names.insertIfAbsent(alt, id2)) {
                        return alt;
                    } else {
                        // Continue and try again with further growth
                        continue;
                    }
                }
                continue;
            }
        }
    }

    private int chooseSyllableCount(Random random, int attempts) {

        int min = 1;
        int max = 6; // default preference upper bound
        if (attempts < 50) {
            int pick = random.nextInt(100);
            if (pick < 20)
                return 1;
            if (pick < 80)
                return 2;
            return 3;
        } else if (attempts < 200) {
            int pick = random.nextInt(100);
            if (pick < 10)
                return 1;
            if (pick < 70)
                return 2;
            if (pick < 95)
                return 3;
            return 4;
        } else {

            // Base 5 plus growth of 1 syllable per 100 attempts
            int growth = 5 + attempts / 100;
            // Respect style's max when possible
            int upper = Math.max(max, 2 + growth);
            int lower = Math.max(1, min);
            return lower + random.nextInt(Math.max(1, upper - lower + 1));
        }
    }

    /**
     * Build a short, realistic root+suffix style name with some variance.
     */
    private String buildRootSuffixCandidate(List<String> roots, List<String> suffixes, List<String> styleSyllables,
            Random random, int attempts) {
        // Prefer short tokens by default (length <= 4)
        List<String> shortRoots = new ArrayList<>();
        for (String r : roots)
            if (r.length() <= 4)
                shortRoots.add(r);
        List<String> shortSuffixes = new ArrayList<>();
        for (String s : suffixes)
            if (s.length() <= 4)
                shortSuffixes.add(s);

        // Determine variant: 1=root only, 2=root+suffix, 3=root+suffix+root
        int variantPick;
        if (attempts < 50) {
            int pick = random.nextInt(100);
            if (pick < 70)
                variantPick = 2;
            else if (pick < 90)
                variantPick = 1;
            else
                variantPick = 3;
        } else if (attempts < 200) {
            int pick = random.nextInt(100);
            if (pick < 60)
                variantPick = 2;
            else if (pick < 80)
                variantPick = 1;
            else
                variantPick = 3;
        } else {
            variantPick = 2 + random.nextInt(3);
        }

        // Helper selection is inline below

        StringBuilder sb = new StringBuilder();

        if (variantPick == 1) {
            List<String> pickListRoots = (!shortRoots.isEmpty() && random.nextInt(100) < 80) ? shortRoots : roots;
            String root = pickListRoots.get(random.nextInt(pickListRoots.size()));
            sb.append(Character.toUpperCase(root.charAt(0))).append(root.substring(1));
            if (styleSyllables != null && !styleSyllables.isEmpty()) {
                int fill = random.nextInt(100);
                if (attempts < 50) {
                    if (fill < 8) {
                        String filler = styleSyllables.get(random.nextInt(styleSyllables.size()));
                        if (sb.charAt(sb.length() - 1) == filler.charAt(0))
                            sb.append(filler.substring(1));
                        else
                            sb.append(filler);
                    }
                } else if (attempts < 200) {
                    if (fill < 12) {
                        String filler = styleSyllables.get(random.nextInt(styleSyllables.size()));
                        if (sb.charAt(sb.length() - 1) == filler.charAt(0))
                            sb.append(filler.substring(1));
                        else
                            sb.append(filler);
                    }
                } else {
                    if (fill < 20) {
                        String filler = styleSyllables.get(random.nextInt(styleSyllables.size()));
                        if (sb.charAt(sb.length() - 1) == filler.charAt(0))
                            sb.append(filler.substring(1));
                        else
                            sb.append(filler);
                    }
                }
            }
        } else if (variantPick == 2) {
            List<String> pickListRoots = (!shortRoots.isEmpty() && random.nextInt(100) < 80) ? shortRoots : roots;
            List<String> pickListSuffixes = (!shortSuffixes.isEmpty() && random.nextInt(100) < 80) ? shortSuffixes
                    : suffixes;
            String root = pickListRoots.get(random.nextInt(pickListRoots.size()));
            String suffix = pickListSuffixes.get(random.nextInt(pickListSuffixes.size()));
            sb.append(Character.toUpperCase(root.charAt(0))).append(root.substring(1));
            // avoid duplicated characters when joining tokens
            if (root.length() > 0 && suffix.length() > 0 && root.charAt(root.length() - 1) == suffix.charAt(0)) {
                sb.append(suffix.substring(1));
            } else {
                sb.append(suffix);
            }
        } else {
            // Longer variant: root + root + suffix
            List<String> pickListRoots = (!shortRoots.isEmpty() && random.nextInt(100) < 80) ? shortRoots : roots;
            List<String> pickListSuffixes = (!shortSuffixes.isEmpty() && random.nextInt(100) < 80) ? shortSuffixes
                    : suffixes;
            String r1 = pickListRoots.get(random.nextInt(pickListRoots.size()));
            String r2;
            if (pickListRoots.size() == 1) {
                r2 = r1;
            } else {
                do {
                    r2 = pickListRoots.get(random.nextInt(pickListRoots.size()));
                } while (r2.equals(r1));
            }
            String s = pickListSuffixes.get(random.nextInt(pickListSuffixes.size()));
            sb.append(Character.toUpperCase(r1.charAt(0))).append(r1.substring(1));
            // append r2 without duplicated characters
            if (r1.length() > 0 && r2.length() > 0 && r1.charAt(r1.length() - 1) == r2.charAt(0)) {
                sb.append(r2.substring(1));
            } else {
                sb.append(r2);
            }
            // append optional filler syllables between r2 and suffix, then suffix
            if (styleSyllables != null && !styleSyllables.isEmpty()) {
                int fillPick = random.nextInt(100);
                int numFill = 0;
                if (attempts < 50) {
                    if (fillPick < 10)
                        numFill = 1;
                } else if (attempts < 200) {
                    if (fillPick < 15)
                        numFill = 1;
                } else {
                    if (fillPick < 30)
                        numFill = 1;
                    else if (fillPick < 35)
                        numFill = 2;
                }
                for (int f = 0; f < numFill; f++) {
                    String filler = styleSyllables.get(random.nextInt(styleSyllables.size()));
                    if (sb.length() > 0 && filler.length() > 0 && sb.charAt(sb.length() - 1) == filler.charAt(0)) {
                        sb.append(filler.substring(1));
                    } else {
                        sb.append(filler);
                    }
                }
            }
            // append suffix, avoid duplicate char at join
            if (r2.length() > 0 && s.length() > 0 && r2.charAt(r2.length() - 1) == s.charAt(0)) {
                sb.append(s.substring(1));
            } else {
                sb.append(s);
            }

        }

        return sb.toString();
    }

    // NOTE: No numeric fallback encodings (base36 or compact numeric-to-syllable)
    // are provided.
    // Growth and uniqueness are achieved by expanding names using provided
    // TOK_SYLLABLES only.

    public synchronized void registerStyle(String name, NameStyle style) {
        this.log.log("Registering name style '%s' %s", name, style);
        styles.put(name, style);
    }

    public synchronized Optional<NameStyle> getStyle(String style) {
        return Optional.ofNullable(styles.get(style));
    }

    public synchronized Set<String> getRegisteredStyles() {
        return new HashSet<>(styles.keySet());
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @ToString
    public static class NameStyle {
        private final NameStyleType type;
        private final Map<String, List<String>> tokens;

        // Intentionally empty constructor: prefer the builder
        public NameStyle() {
            this(null, null);
        }
    }

    public enum NameStyleType {
        SYLLABLE,
        ROOT_SUFFIX
    }
}
