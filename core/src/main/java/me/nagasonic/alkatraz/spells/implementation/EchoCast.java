package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.PlayerCastEvent;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.StatUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 8 NONE utility: re-cast the last three things you cast, in order, at 60% of each one's
 * own mana cost.
 *
 * <h2>The chain-ultimate archetype</h2>
 * The only reward in the roster that pays for <b>variety</b>. A player who cycles
 * {@code fireball -> flame_lance -> thunderclap} gets all three back for the price of one circle-8
 * cast; a player who has stood still casting {@code barrier} gets one {@code barrier} back and
 * learns the lesson. {@link EchoCastConfig#replayCost(int)} rounds each charge up, and
 * {@link #resolveSequence} de-duplicates, so three casts of the same spell replay it once rather
 * than three times.
 *
 * <h2>Why {@code castAction} and not {@code Spell.cast}</h2>
 * {@code Spell.cast} charges mana again ({@code Spell.java:189}), charges a cooldown again
 * ({@code :233}) and schedules another full cast time ({@code :209}), which would re-enter the
 * casting-state machine the outer {@code echo_cast} cast is already using. Calling
 * {@link Spell#castAction} directly gives one mana charge (computed here), one cooldown, one cast
 * time - and no recursion, because {@code castAction} fires <b>no</b> event, so a replay never
 * writes itself into the history and {@code echo_cast} cannot echo itself.
 *
 * <h2>Where the cast history lives, and why that is a controller decision</h2>
 * There is no cast-history buffer anywhere in the codebase today: {@code PlayerCastEvent} fires
 * for every cast and nothing retains it. This class therefore owns a
 * {@code private static Map<UUID, Deque<String>>}, bounded at {@code history_size}, cleared on quit,
 * and dead with the plugin. It is <b>not persisted</b> and <b>not shared</b>, so a player who
 * relogs mid-rotation comes back to an empty history, and a second spell that later wants the same
 * data cannot have it. Moving it onto {@link MagicProfile} as a {@code stringSetStat("castHistory")}
 * beside {@code stringSetStat("warpPoints")} would fix both - at the cost of a wave-6 edit to a
 * file this task must not touch. That call belongs to the controller, made once.
 *
 * <h2>The damage contract, vacuously</h2>
 * {@code EchoCast} never calls {@code getPower}: it extends bare {@link Spell}, which has no
 * {@code getPower} and no {@code basePower}. Every entity the echo touches is touched by
 * <i>another</i> spell's {@code castAction}, on that spell's own authority, with that spell's own
 * {@code AttackProperties} and that spell's own budget. The echo adds a mana cost and a trigger;
 * it adds no damage. Countering one replay should not silently delete the other two, because they
 * are three separate casts the player paid for.
 */
public class EchoCast extends Spell implements Listener {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.NONE);

    /** The replay glyph; the leading edge, so it takes the core shade. */
    private static final Particle.DustOptions ECHO_DUST = PALETTE.coreDust();
    /** Portal and witch sparkle around the caster's eyes. */
    private static final Particle.DustOptions ECHO_SPARKLE = PALETTE.accentDust();

    /** Hexagon sides; six is the glyph both circle-6 channel spells drew. */
    private static final int GLYPH_SIDES = 6;
    /** Base hexagon radius, in blocks, before the per-replay widening. */
    private static final double GLYPH_BASE_RADIUS = 1.2;
    /** Extra radius per replay index, so successive glyphs are visibly distinct. */
    private static final double GLYPH_RADIUS_STEP = 0.2;
    /** Degrees of rotation added per replay, so the three glyphs never draw the same hexagon. */
    private static final double GLYPH_ROTATION_STEP = 40.0;

    /** The caster's last {@code history_size} spell ids, oldest first. Bounded and cleared on quit. */
    private static final Map<UUID, Deque<String>> castHistory = new ConcurrentHashMap<>();

    private EchoCastConfig config;

    public EchoCast(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        // No options file: the four tuning axes - how many casts are remembered, what fraction is
        // charged, how fast the sequence paces and what it refuses to replay - are all
        // server-authority knobs. A player-side option on any of them is a straight damage and
        // mana multiplier with no defensive counterpart.
        Alkatraz.getInstance().save("spells/echo_cast.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/echo_cast.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = EchoCastConfig.fromConfig(spellConfig.getValues(false));
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    /**
     * Records every completed player cast.
     *
     * <p>MONITOR so it runs after every other plugin's decision on the same event, and the
     * {@code isCancelled} guard so a cast that was going to be called off does not occupy one of
     * three slots. {@code PlayerCastEvent} fires at {@code Spell.java:219}, after mana is charged,
     * the circle has started and focus cancellation has been ruled out - so by the time this
     * writes, the cast has genuinely been paid for.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCastCompleted(PlayerCastEvent e) {
        if (e.isCancelled()) return;
        Spell spell = e.getSpell();
        Player caster = e.getCaster();
        if (spell == null || caster == null) return;
        if (config == null || config.isSkipped(spell.getId())) return;

        Deque<String> history =
                castHistory.computeIfAbsent(caster.getUniqueId(), key -> new ArrayDeque<>());
        history.addLast(spell.getId());
        while (history.size() > config.historySize()) {
            history.removeFirst();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        castHistory.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) {
            cancelCast(caster);
            return;
        }

        Deque<String> history = castHistory.get(caster.getUniqueId());
        List<Spell> sequence = history == null ? List.of() : resolveSequence(history);
        if (sequence.isEmpty()) {
            Utils.sendActionBar(caster, lang().get("spells.echocast.no_history"));
            cancelCast(caster);
            return;
        }

        int totalCost = 0;
        for (Spell spell : sequence) {
            totalCost += config.replayCost(spell.getCost());
        }

        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        if (profile == null || profile.getMana() < totalCost) {
            Utils.sendActionBar(caster, lang().get("spells.echocast.not_enough_mana"));
            cancelCast(caster);
            return;
        }

        // All-or-nothing: the whole sequence is charged before the first replay fires, so a caster
        // who cannot pay for all of it gets an action bar message and a full refund of the 450
        // they already paid, rather than one-and-a-bit replays.
        StatUtils.subMana(caster, totalCost);

        Utils.sendActionBar(caster, lang().get("spells.echocast.replaying",
                "count", String.valueOf(sequence.size())));

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (index >= sequence.size()) {
                    cancel();
                    return;
                }
                if (caster.isDead() || !caster.isOnline()) {
                    cancel();
                    return;
                }
                Spell replayed = sequence.get(index++);
                spawnEchoGlyph(caster, index);
                replayed.castAction(caster, wand);
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, config.replayGapTicks());
    }

    /**
     * Oldest first, de-duplicated, skip-filtered - the rotation replayed the way it was played.
     *
     * <p>The de-duplication is what stops {@code echo_cast} being a triple-tap: three casts of the
     * same spell replay it <b>once</b>. {@code seen} is a {@link LinkedHashSet} so insertion order
     * is the rotation's own order.
     */
    private List<Spell> resolveSequence(Deque<String> history) {
        List<Spell> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        // Snapshot: the runnable below reads the sequence, and a cast made mid-sequence would
        // otherwise mutate the deque underneath it.
        for (String id : new ArrayList<>(history)) {
            if (!seen.add(id)) continue;
            if (config.isSkipped(id)) continue;
            Spell spell = SpellRegistry.getSpell(id);
            if (spell != null && spell != this) {
                out.add(spell);
            }
        }
        return out;
    }

    /** The glyph for one replay: a hexagon at the caster's feet, rotated further each step. */
    private void spawnEchoGlyph(Player caster, int index) {
        Location feet = caster.getLocation();
        Location eye = caster.getEyeLocation();

        List<Location> glyph = ParticleUtils.regularPolygon(
                feet.clone().add(0, 0.1, 0),
                GLYPH_SIDES,
                GLYPH_BASE_RADIUS + GLYPH_RADIUS_STEP * index,
                1,
                eye.getYaw(),
                0.0F,
                (float) (index * GLYPH_ROTATION_STEP));
        for (Location loc : glyph) {
            ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc, 0, ECHO_DUST);
        }

        ParticleCaster.spawn(caster, eye.getWorld(), Utils.PORTAL, eye, 30, 0.3, 0.3, 0.3, 0.05);
        ParticleCaster.spawn(caster, eye.getWorld(), Utils.WITCH, eye, 10, 0.3, 0.3, 0.3, 0.0, ECHO_SPARKLE);
        caster.getWorld().playSound(eye, Sound.ENTITY_EVOKER_AMBIENT, 0.6f, 1.4f);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Echo Cast is player-only - a mob has no MagicProfile and therefore no cast history.
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 2.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.echocast.book_name"))
                .addCustomLoreLine(lang().get("spells.echocast.lore1"))
                .addCustomLoreLine(lang().get("spells.echocast.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 8))
                .build();
    }
}