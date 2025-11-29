package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
// Classes in same package - no explicit import required

import java.util.*;

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
 * - When many collisions occur for short names, a style-based syllable
 * encoding of a monotonic global id is appended, producing an infinite
 * supply of unique, name-like strings.
 */
public class NameGenerationSystem extends SingletonGameSystem implements OnSystemInitialize {

    private final GrouplessPropertiesSubSystem<String, Long> names;
    private final Map<String, NameStyle> styles;

    public NameGenerationSystem(Game game) {
        super(game);

        names = game.registerSystem(new GrouplessPropertiesSubSystem<>(game, "unique_name",
                PropertiesSubSystem.stringHandler(), PropertiesSubSystem.longHandler()));
        styles = new HashMap<>();

    }

    @Override
    public void onSystemInitialize() {
        log.log("NameGenerationSystem initialized");
    }

    /**
     * Generate a new, unique name in the given style. If the style is not
     * registered the system falls back to its default syllables. The method
     * writes the generated name to the DB to ensure uniqueness and will
     * append a syllable-encoded suffix if concise names repeatedly collide.
     *
     * @param style  style identifier registered via {@link #registerStyle}
     * @param random caller-provided RNG (seeded for determinism when desired)
     * @return a unique name
     */
    public synchronized String generateName(String style, Random random) {

        NameStyle nameStyle = styles.get(style);
        List<String> syllables = nameStyle.syllables();

        int attempts = 0;
        int maxAttemptsBeforeGrowing = 200;
        while (true) {
            attempts++;

            int syllableCount = chooseSyllableCount(random, attempts);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < syllableCount; i++) {
                String s = syllables.get(random.nextInt(syllables.size()));
                if (i == 0) {

                    sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
                } else {
                    sb.append(s);
                }
            }

            String candidate = sb.toString();

            long id = game.getNewGlobalId();
            if (names.insertIfAbsent(candidate, id)) {
                log.log("Generated unique name '%s' (id=%d)", candidate, id);
                return candidate;
            } else {

                if (attempts > maxAttemptsBeforeGrowing) {

                    long id2 = game.getNewGlobalId();
                    String suffix = encodeIdToSyllables(id2, syllables);
                    String alt = candidate + suffix;

                    if (names.insertIfAbsent(alt, id2)) {
                        log.log("Generated fallback name '%s' (id=%d)", alt, id2);
                        return alt;
                    } else {

                        continue;
                    }
                }
                continue;
            }
        }
    }

    private int chooseSyllableCount(Random random, int attempts) {

        int min = 1;
        int max = 6; // default preference boundaries; will grow later if needed
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

            // Base 5 plus growth of 1 syllable per 100 attempts, capped at 20 syllables.
            int growth = 5 + attempts / 100;
            // Respect style's max when possible
            int upper = Math.max(max, 2 + growth);
            int lower = Math.max(1, min);
            return lower + random.nextInt(Math.max(1, upper - lower + 1));
        }
    }

    private String encodeIdToSyllables(long id, List<String> syllables) {
        if (id <= 0)
            throw new InternalException(String.format("ID %d must be positive to encode to syllables", id));

        int base = syllables.size();
        List<String> parts = new ArrayList<>();
        while (id > 0) {
            int idx = (int) (id % base);
            parts.add(syllables.get(idx));
            id /= base;
        }
        Collections.reverse(parts);
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append(p);
        }
        return sb.toString();
    }

    public synchronized void registerStyle(String name, List<String> syllables) {
        this.log.log("Registering name style '%s' with %d syllables", name, syllables.size());
        styles.put(name, new NameStyle(new ArrayList<>(syllables)));
    }

    public synchronized Optional<NameStyle> getStyle(String style) {
        return Optional.ofNullable(styles.get(style));
    }

    public synchronized Set<String> getRegisteredStyles() {
        return new HashSet<>(styles.keySet());
    }

    public record NameStyle(List<String> syllables) {
    }
}
