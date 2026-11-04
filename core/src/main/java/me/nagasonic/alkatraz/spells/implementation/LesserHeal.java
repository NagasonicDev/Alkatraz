package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * LesserHeal - the cheaper, faster-casting sibling of {@link Heal}.
 *
 * <h2>Why this is not a clone of {@code heal}</h2>
 * The two used to run byte-identical geometry in byte-identical yellow: the same
 * three-turn helix and the same three concentric circles, differing only in the numbers
 * on the heal. Both are now {@link SpellVfx#column}, but deliberately on opposite
 * settings so a player holding both can tell them apart mid-cast:
 * <ul>
 *   <li><b>Height</b> - a little over half of {@code heal}'s, so it reads as a stub
 *       rather than a pillar.</li>
 *   <li><b>Pace</b> - roughly twice as many ticks to reveal, so it creeps where
 *       {@code heal} snaps.</li>
 *   <li><b>Density</b> - fewer slices and far fewer particles per slice.</li>
 *   <li><b>Colour weight</b> - a short column is mostly foot and body, so
 *       {@code deep} and {@code accent} dominate its gradient, and it is anchored by a
 *       dark gold base ring in {@code deep} that {@code heal} has no equivalent of.</li>
 * </ul>
 */
public class LesserHeal extends Spell {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `LIGHT` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(255, 255, 135);

    /** The LIGHT palette, resolved once. */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.LIGHT);

    /**
     * Column height in blocks - just over half of {@code heal}'s 3.2, which is what
     * makes the silhouette read as a different spell rather than a smaller one.
     */
    private static final double COLUMN_HEIGHT = 1.7D;

    /** Column radius in blocks. */
    private static final double COLUMN_RADIUS = 0.7D;

    /** Vertical slices; less than half of {@code heal}'s. */
    private static final int COLUMN_LAYERS = 7;

    /** Particles per slice; less than a third of {@code heal}'s. */
    private static final int COLUMN_POINTS = 6;

    /**
     * Rotation applied between consecutive slices, in radians. A quarter turn per layer
     * against {@code heal}'s ninth, so even a single frame does not look alike.
     */
    private static final double COLUMN_PHASE_STEP = (Math.PI * 2.0D) / 4.0D;

    /** Ticks over which the column reveals itself - roughly double {@code heal}'s. */
    private static final int COLUMN_REVEAL_TICKS = 26;

    /** Radius of the dark base ring that anchors the column. */
    private static final double BASE_RING_RADIUS = 1.1D;

    /** Particles on the base ring. */
    private static final int BASE_RING_POINTS = 16;

    /** Ticks over which the base ring draws itself in. */
    private static final int BASE_RING_TICKS = 10;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public LesserHeal(String type){
        super(type);
    }
    private double baseHeal;
    private double maxHeal;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/lesser_heal.yml");
        Alkatraz.getInstance().saveConfig("spells/lesser_heal_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/lesser_heal.yml").get();

        loadCommonConfig(spellConfig);
        loadOptions();
        baseHeal = spellConfig.getDouble("base_heal");
        maxHeal = spellConfig.getDouble("max_heal");
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            Entity lookedAt = p.isSneaking() ? null
                    : me.nagasonic.alkatraz.util.Utils.getTargetEntity(p, (int) getModifiedStat(p, "target_range", 20));
            if (!(lookedAt instanceof Player)){
                // WAS: double base = (baseHeal * wandPower) *
                //          (1 + affinity(Element.LIGHT) / 100);
                //      double heal = getModifiedStat(p, "heal", base);
                // The affinity was baked into the base BEFORE getModifiedStat, so
                // every option MULTIPLY was applied to an already-affinity-scaled
                // value - the mirror of the getPower double-apply, and not accounted
                // for by any budget. LesserHeal is a UTILITY spell, not a damage
                // instance, so it calls getModifiedStat directly with the RAW base.
                double wandPower = getWandPower(wand, p);
                double heal = getModifiedStat(p, "heal", baseHeal * wandPower);
                if (heal > maxHeal){
                    heal = maxHeal;
                }
                if (p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() < p.getHealth() + heal){
                    p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                }else p.setHealth(p.getHealth() + heal);
                drawLesserColumn(p);
            }else{
                Player target = (Player) lookedAt;
                // Same de-bake as the self-cast path above: RAW base into
                // getModifiedStat, no pre-multiplied light affinity.
                double wandPower = getWandPower(wand, p);
                double heal = getModifiedStat(p, "heal", baseHeal * wandPower);
                if (heal > maxHeal){
                    heal = maxHeal;
                }
                if (target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() < target.getHealth() + heal){
                    target.setHealth(target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                }else target.setHealth(target.getHealth() + heal);
                drawLesserColumn(target);
            }
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        double wandPower = getWandPowerOrDefault(wand);
        double heal = (baseHeal * wandPower) * (1 + Utils.getEntityAffinity(Element.LIGHT, caster) / 100);
        if (heal > maxHeal){
            heal = maxHeal;
        }
        if (caster.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() < caster.getHealth() + heal){
            caster.setHealth(caster.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        }else caster.setHealth(caster.getHealth() + heal);
        drawLesserColumn(caster);
    }

    /**
     * Reveals the lesser heal's column and its dark base ring over
     * {@link #COLUMN_REVEAL_TICKS} ticks on the healed subject.
     *
     * @param subject the entity that was healed; both shapes follow it
     */
    private void drawLesserColumn(LivingEntity subject) {
        ElementPalette.Palette palette = PALETTE;
        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= COLUMN_REVEAL_TICKS || subject.isDead() || !subject.isValid()) {
                    cancel();
                    return;
                }
                Location base = subject.getLocation();
                World world = base.getWorld();
                if (world != null) {
                    int layers = (int) Math.round(COLUMN_LAYERS * (elapsed + 1) / (double) COLUMN_REVEAL_TICKS);
                    SpellVfx.column(CAST_COLOR, subject, base, COLUMN_HEIGHT, COLUMN_RADIUS,
                            layers, COLUMN_POINTS, COLUMN_PHASE_STEP * (elapsed + 1));

                    // The anchor ring the stronger heal does not have, drawn in the
                    // palette's dark gold so it reads as structure rather than as a
                    // second light source.
                    int ringPoints = (int) Math.round(BASE_RING_POINTS
                            * Math.min(1.0D, (elapsed + 1) / (double) BASE_RING_TICKS));
                    if (ringPoints >= 3) {
                        drawBaseRing(subject, world, base, ringPoints, palette);
                    }
                }
                elapsed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    /**
     * One expanding {@code deep} ring on the ground under the column.
     *
     * <p>Built here rather than through {@link SpellVfx#ring} because that builder paints
     * its body in {@code core} and reserves {@code deep} for the shadow ring underneath;
     * the whole point here is that this ring is dark structure.
     *
     * @param subject    the entity the ring belongs to, used for per-player density falloff
     * @param world      the world to emit into
     * @param base       the column's foot, which is the ring's centre
     * @param ringPoints how many particles to draw; at least three
     * @param palette    the LIGHT palette supplying the dust
     */
    private void drawBaseRing(LivingEntity subject, World world, Location base, int ringPoints,
                              ElementPalette.Palette palette) {
        Location scratch = new Location(world, base.getX(), base.getY() + 0.05D, base.getZ());
        for (int i = 0; i < ringPoints; i++) {
            double angle = Math.PI * 2.0D * i / ringPoints;
            scratch.setX(base.getX() + Math.cos(angle) * BASE_RING_RADIUS);
            scratch.setZ(base.getZ() + Math.sin(angle) * BASE_RING_RADIUS);
            ParticleCaster.spawn(subject, world, Utils.DUST, scratch, 0, palette.deepDust());
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.lesserheal.book_name"))
                .addLoreLine(lang().get("spells.lesserheal.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 2))
                .build();
    }

    @Override
    public boolean canMobCast(Mob mob){
        return mob.getHealth() < mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
    }
}
