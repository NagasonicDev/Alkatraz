package me.nagasonic.alkatraz.progression;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.items.magic.attribute.StatPointsAttributeSource;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One-time player-visible notice for the affinity curve change.
 *
 * <p>The curve moved from {@code 10 x points} to {@code 10 x points^0.65}, so every
 * existing player's per-element affinity silently fell on next login: a 100-affinity
 * fire mage became 45, a damage multiplier of x2.00 against a neutral target
 * dropping to x1.45. Nothing was spent or refunded, and nothing in-game says so.
 *
 * <p><b>What the numbers mean.</b> The line reports the CURVE's contribution only -
 * {@code points x per_point} before, {@code points x per_point ^ exponent} after.
 * Research and equipment bonuses are added on top of that by the wave-1 T3 delta
 * reconcile and are completely unchanged by this curve, so they are deliberately not
 * in either number. The footer's lang key says so in as many words.
 *
 * <p><b>Idempotency.</b> The flag is a persisted boolean on the MagicProfile, in the
 * same bool map as FirstJoinTutorial's {@code tutorialSeen}. It is NOT a transient
 * field: MagicProfile's spellModifiers, castingSpell, castingWand, currentCircleTaskId
 * and completionTaskId are all transient and rebuilt at login, so a flag stored there
 * would reset every session and the notice would fire forever.
 *
 * <p><b>Who does not see it.</b> A player with zero invested stat points in every
 * element has nothing to be told, and a player whose exponent is set back to 1.0 sees
 * no change at all. Both are handled by computeChanges returning an empty list, and
 * both set the flag anyway so the check runs at most once per player.
 */
public final class AffinityCurveMigrationNotice implements Listener {

    /**
     * Persisted on MagicProfile via setBool, alongside "tutorialSeen". Changing this
     * string re-notifies every player who has already been notified.
     */
    public static final String SEEN_FLAG = "affinityCurveNoticeSeen";

    /** One line of the notice: which element, and what its affinity became. */
    public record Change(String elementName, int oldValue, int newValue) {
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
        if (profile == null) return;
        if (profile.getBool(SEEN_FLAG)) return;

        // Set it FIRST and unconditionally. Whether or not there is anything to say,
        // this player has now been through the check exactly once.
        profile.setBool(SEEN_FLAG, true);

        Map<Element, Integer> invested = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            if (element == Element.NONE) continue;
            invested.put(element, profile.getPoints(element));
        }

        double perPoint = ((Number) Configs.AFFINITY_PER_POINT.get()).doubleValue();
        double exponent = ((Number) Configs.AFFINITY_EXPONENT.get()).doubleValue();

        List<Change> changes = computeChanges(invested, perPoint, exponent);
        if (changes.isEmpty()) return;

        LangManager lang = Alkatraz.getLangManager();
        player.sendMessage(lang.get("migration.affinity_curve.header"));
        player.sendMessage(lang.get("migration.affinity_curve.intro", "player", player.getName()));
        for (Change change : changes) {
            player.sendMessage(lang.get("migration.affinity_curve.line",
                    "element", change.elementName(),
                    "old", String.valueOf(change.oldValue()),
                    "new", String.valueOf(change.newValue())));
        }
        player.sendMessage(lang.get("migration.affinity_curve.footer"));

        // The single new [Alkatraz] WARN line this plan produces. Its FIXED PREFIX is
        // the string allowlisted in .github/workflows/test-plugin.yml:265 - the
        // per-player detail is appended AFTER the prefix on purpose, so however many
        // elements changed, sort -u plus grep -v collapse it to one excluded pattern.
        // Changing the prefix without changing the workflow breaks CI.
        Alkatraz.logWarning("Affinity curve migration notice shown to " + player.getName() + ": "
                + changes.stream()
                .map(change -> change.elementName() + " " + change.oldValue() + "->" + change.newValue())
                .collect(Collectors.joining(", ")));
    }

    /**
     * The elements whose curve contribution changed. Pure, so the notice's arithmetic
     * is testable without a server.
     *
     * <p>Skips any element with no invested points, and any element where both curves
     * round to the same integer - one point is worth 10 under either curve, and
     * "10 -&gt; 10" is noise, not news.
     */
    static List<Change> computeChanges(Map<Element, Integer> invested, double perPoint, double exponent) {
        List<Change> changes = new ArrayList<>();
        if (invested == null) return changes;
        for (Element element : Element.values()) {
            if (element == Element.NONE) continue;
            int points = invested.getOrDefault(element, 0);
            if (points <= 0) continue;
            int oldValue = (int) Math.round(points * perPoint);
            int newValue = (int) Math.round(
                    StatPointsAttributeSource.affinityFromPoints(points, perPoint, exponent));
            if (oldValue == newValue) continue;
            changes.add(new Change(element.getColorlessName(), oldValue, newValue));
        }
        return changes;
    }
}
