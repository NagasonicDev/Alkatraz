package me.nagasonic.alkatraz.spells.spellbooks;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.SpellbookConfig;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * Factory class for creating spellbooks.
 *
 * <p><b>How a circle's share of a random book is computed.</b>
 * {@code spellbook.circle_weights.circle_N} is the weight of a CIRCLE, not of a
 * spell. Every spell at circle N therefore receives {@code w(N) / count(N)}, so
 * each circle bucket totals exactly {@code w(N)} and a circle's share of a book is
 * {@code w(N) / sum(w(circles in the book))} - <b>invariant to how many spells
 * exist at that circle</b>.
 *
 * <p>The original code gave every spell at circle N the full {@code w(N)}, which
 * made a bucket total {@code count(N) x w(N)} and turned the share into a function
 * of roster size. Circle 2 holds eleven spells and circle 1 holds five, so a
 * {@code (1,2)} book came out 52.4% circle 2 and 47.6% circle 1 - the ladder ran
 * backwards, purely because circle 2 was bigger.
 *
 * <p>Every method here may return {@code null} rather than an unusable item.
 * {@link RandomSpellbook#use} treats a book with no spells as a dead item that
 * prints {@code spellbook.empty} and is never consumed, so "I could not find a
 * spell" has to be a {@code null} the caller can skip, not a broken ItemStack.
 */
public class SpellbookFactory {

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    // ==========================================
    // REGULAR SPELLBOOKS
    // ==========================================

    public static ItemStack createSpellBook(Spell spell) {
        return spell.getSpellBook();
    }

    // ==========================================
    // RANDOM SPELLBOOKS
    // ==========================================

    public static int getWeight(int circle) {
        return SpellbookConfig.getCircleWeight(circle);
    }

    /**
     * A random book drawn from the given circles.
     *
     * @return the book, or {@code null} if no spell in the whole registry matched.
     *         Callers must skip a null rather than registering an AIR ballast slot.
     */
    public static ItemStack createRandomSpellBook(int... circles) {
        return buildRandom(circles, spell -> true, lang().get("spellbook_factory.random_name"));
    }

    /** An element book with no circle ceiling. Kept for callers that genuinely want the whole column. */
    public static ItemStack createRandomElementSpellBook(Element element) {
        return createRandomElementSpellBook(element, 9);
    }

    /**
     * An element book capped at {@code maxCircle}.
     *
     * <p>The cap is the whole point. A FIRE element book from a blaze used to be
     * able to roll {@code inferno_rite} at circle 9 (600 mana) off a mob that also
     * hands out {@code fire_blast} at circle 2.
     *
     * @param maxCircle highest circle permitted, clamped to 1-9. 9 means uncapped.
     * @return the book, or {@code null} if no spell of that element matched.
     */
    public static ItemStack createRandomElementSpellBook(Element element, int maxCircle) {
        int ceiling = Math.max(1, Math.min(9, maxCircle));
        int[] circles = new int[ceiling];
        for (int c = 1; c <= ceiling; c++) {
            circles[c - 1] = c;
        }
        return buildRandom(circles, spell -> spell.getElement() == element,
                lang().get("spellbook_factory.element_name", "element", element.getName()));
    }

    private static ItemStack buildRandom(int[] requested, Predicate<Spell> keep, String displayName) {
        Map<String, Integer> circleById = new LinkedHashMap<>();
        for (Spell spell : SpellRegistry.getAllSpells().values()) {
            if (spell == null) continue;
            if (!keep.test(spell)) continue;
            circleById.put(spell.getId(), spell.getRequiredCircleLevel());
        }

        Map<Integer, List<String>> buckets = bucketByCircle(circleById, requested);
        if (isEmpty(buckets)) {
            // Nothing matched. Rather than build a spell_count=0 book that prints
            // spellbook.empty on right-click and is never consumed, walk DOWN from
            // the highest requested circle to the first circle that has a spell.
            int[] fallback = fallbackCircles(circleById, requested);
            buckets = bucketByCircle(circleById, fallback);
            Alkatraz.logWarning("SpellbookFactory: circles " + Arrays.toString(requested)
                    + " matched no spell. Fell back to " + Arrays.toString(fallback)
                    + " so the book is usable rather than empty.");
        }

        int candidateCount = countOf(buckets);
        Map<String, Double> weights = circleWeightsFor(buckets, SpellbookFactory::getWeight);
        if (weights.size() < candidateCount) {
            Alkatraz.logWarning("SpellbookFactory: " + (candidateCount - weights.size())
                    + " spell(s) were left out of a book for circles " + Arrays.toString(requested)
                    + " because their circle weight is 0. spellbook.circle_weights.circle_0 is 0 by "
                    + "design, so no circle-0 spell ever rolls.");
        }

        if (weights.isEmpty()) {
            Alkatraz.logWarning("SpellbookFactory: no spell matched circles "
                    + Arrays.toString(requested) + ". Returning no book.");
            return null;
        }
        return new RandomSpellbook(displayName, weights).build();
    }

    // ==========================================
    // PURE WEIGHTING ARITHMETIC
    //
    // Split out from buildRandom so the weighting can be asserted without a live
    // server: every Spell constructor reads ConfigManager and most register events
    // on a PluginManager, so SpellbookFactory is not constructible in a unit test.
    // ==========================================

    /**
     * Groups spell ids into per-circle buckets, keeping only the requested circles.
     * A requested circle with no spells yields an EMPTY bucket rather than being
     * dropped, so a caller can tell "circle 7 was asked for and is empty" from
     * "circle 7 was not asked for".
     *
     * <p>Bucket contents are sorted so two builds of the same book produce identical
     * {@code spell_<i>}/{@code weight_<i>} NBT indices.
     */
    static Map<Integer, List<String>> bucketByCircle(Map<String, Integer> circleBySpellId, int... circles) {
        Map<Integer, List<String>> buckets = new LinkedHashMap<>();
        for (int circle : circles) {
            buckets.put(circle, new ArrayList<>());
        }
        for (Map.Entry<String, Integer> entry : circleBySpellId.entrySet()) {
            List<String> bucket = buckets.get(entry.getValue());
            if (bucket == null) continue;
            bucket.add(entry.getKey());
        }
        for (List<String> bucket : buckets.values()) {
            bucket.sort(String::compareTo);
        }
        return buckets;
    }

    /**
     * Divides each circle's weight by the size of that circle's bucket, so every
     * bucket totals exactly {@code circleWeight(circle)}.
     *
     * <p>A circle whose weight is zero or negative contributes nothing and is
     * skipped. {@code SpellbookConfig.getCircleWeight} returns 0 for any circle
     * outside 1-9 and {@link RandomSpellbook#addSpell} rejects weight &lt;= 0, so
     * without this guard a {@code createRandomSpellBook(0)} or a
     * {@code createRandomElementSpellBook(Element.NONE)} - either of which would
     * pull in circle-0 {@code magic_missile} - threw out of {@code registerAll()}
     * and stopped the plugin enabling.
     *
     * <p>Silent by design: this is pure arithmetic and is asserted directly by
     * {@code SpellbookWeightTest}. The caller does the logging, because logging here
     * would require a live {@code Alkatraz} instance.
     */
    static Map<String, Double> circleWeightsFor(Map<Integer, List<String>> buckets,
                                                IntUnaryOperator circleWeight) {
        Map<String, Double> weights = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<String>> entry : buckets.entrySet()) {
            int circle = entry.getKey();
            List<String> bucket = entry.getValue();
            if (bucket.isEmpty()) continue;
            double circleTotal = circleWeight.applyAsInt(circle);
            if (circleTotal <= 0) continue;
            double perSpell = circleTotal / bucket.size();
            for (String spellId : bucket) {
                weights.put(spellId, perSpell);
            }
        }
        return weights;
    }

    /**
     * The circle set to use when every requested circle is empty.
     *
     * <p>Scans DOWN from the highest requested circle to the first circle that has
     * a spell, then falls back to circle 1. Called only when all requested buckets
     * are empty, so it never rewrites a partially-satisfiable request.
     *
     * <p>This is what makes {@code createRandomSpellBook(7,8,9)} - built by the
     * ender dragon and wither entries - degrade to a circle-6 book on a roster with
     * no circles 7-9, instead of producing an item tagged
     * {@code spellbook_type=random, spell_count=0}.
     */
    static int[] fallbackCircles(Map<String, Integer> circleBySpellId, int... requested) {
        Set<Integer> populated = new HashSet<>(circleBySpellId.values());
        int ceiling = Arrays.stream(requested).max().orElse(9);
        for (int circle = ceiling; circle >= 1; circle--) {
            if (populated.contains(circle)) return new int[]{circle};
        }
        return new int[]{1};
    }

    private static boolean isEmpty(Map<Integer, List<String>> buckets) {
        for (List<String> bucket : buckets.values()) {
            if (!bucket.isEmpty()) return false;
        }
        return true;
    }

    private static int countOf(Map<Integer, List<String>> buckets) {
        int count = 0;
        for (List<String> bucket : buckets.values()) {
            count += bucket.size();
        }
        return count;
    }

    // ==========================================
    // PER-SPELL WEIGHTED BOOKS
    // ==========================================

    /**
     * A random book over an explicit, hand-chosen spell set.
     *
     * <p>Example usage:
     * <pre>
     * createCustomRandomSpellbook("Starter Pack",
     *     Material.BOOK,
     *     "fireball", 60,
     *     "water_pulse", 40
     * )
     * </pre>
     *
     * <p>Weights must be {@link Number}s. The previous implementation cast to
     * {@code Double}, so the {@code 60} in its own Javadoc example threw
     * {@code ClassCastException: Integer cannot be cast to Double} - the example was
     * not runnable code. Accepting {@code Number} makes the documented example work
     * and keeps {@code Long}/{@code Float} weights usable.
     *
     * @return the book, or {@code null} if the spell set was empty.
     */
    public static ItemStack createCustomRandomSpellbook(String name, Material material, Object... spellsAndWeights) {
        if (spellsAndWeights.length % 2 != 0) {
            throw new IllegalArgumentException("Must provide pairs of spell IDs and weights!");
        }

        Map<String, Double> weights = new LinkedHashMap<>();
        for (int i = 0; i < spellsAndWeights.length; i += 2) {
            Object idObject = spellsAndWeights[i];
            Object weightObject = spellsAndWeights[i + 1];
            if (!(idObject instanceof String spellId)) {
                throw new IllegalArgumentException(
                        "Entry " + i + " must be a spell id string, got " + idObject);
            }
            if (!(weightObject instanceof Number weight)) {
                throw new IllegalArgumentException("Weight for '" + spellId + "' must be a Number, got "
                        + weightObject.getClass().getSimpleName() + ". Pass 60, not 60.0d.");
            }
            if (weight.doubleValue() <= 0) {
                throw new IllegalArgumentException(
                        "Weight for '" + spellId + "' must be positive, got " + weight + ".");
            }
            weights.put(spellId, weight.doubleValue());
        }

        if (weights.isEmpty()) return null;
        return new RandomSpellbook("&d" + name, weights).setMaterial(material).build();
    }
}
