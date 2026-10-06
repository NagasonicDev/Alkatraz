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
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
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
 * Heal - restores hitpoints to the caster, or to the player they are looking at.
 *
 * <h2>Visuals</h2>
 * The payoff is a {@link SpellVfx#column} rising on whoever was healed: a tall, fast,
 * {@code core}-led pillar that finishes in about three quarters of a second.
 * {@code lesser_heal} is the same builder with a short, slow, sparsely-populated column
 * over a dark {@code deep} base ring, so a player holding both can tell them apart
 * mid-cast rather than only by the number.
 */
public class Heal extends Spell {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `LIGHT` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(255, 255, 135);

    /**
     * Full column height in blocks. {@code lesser_heal} uses roughly half this, which is
     * the single most legible difference between the two heals.
     */
    private static final double COLUMN_HEIGHT = 3.2D;

    /** Column radius in blocks. */
    private static final double COLUMN_RADIUS = 0.95D;

    /** Vertical slices the finished column is made of. */
    private static final int COLUMN_LAYERS = 15;

    /** Particles per slice. */
    private static final int COLUMN_POINTS = 18;

    /**
     * Rotation applied between consecutive slices, in radians - a ninth of a turn per
     * layer, so the stack of rings visibly winds up as it rises.
     */
    private static final double COLUMN_PHASE_STEP = (Math.PI * 2.0D) / 9.0D;

    /** Ticks over which the column reveals itself. The full heal pops; the lesser one crawls. */
    private static final int COLUMN_REVEAL_TICKS = 15;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public Heal(String type){
        super(type);
    }
    private double baseHeal;
    private double maxHeal;


    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/heal.yml");
        Alkatraz.getInstance().saveConfig("spells/heal_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/heal.yml").get();

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
                // for by any budget. Heal is a UTILITY spell, not a damage instance,
                // so it does not go through SpellAttackContext; it calls
                // getModifiedStat directly with the RAW base.
                double wandPower = getWandPower(wand, p);
                double heal = getModifiedStat(p, "heal", baseHeal * wandPower);
                if (heal > maxHeal){
                    heal = maxHeal;
                }
                if (p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() < p.getHealth() + heal){
                    p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                }else p.setHealth(p.getHealth() + heal);
                drawHealColumn(p);
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
                drawHealColumn(target);
            }
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        double wandp = getWandPowerOrDefault(wand);
        double heal = (baseHeal * wandp) * (1 + Utils.getEntityAffinity(Element.LIGHT, caster) / 100);
        if (heal > maxHeal){
            heal = maxHeal;
        }
        if (caster.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() < caster.getHealth() + heal){
            caster.setHealth(caster.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        }else caster.setHealth(caster.getHealth() + heal);
        drawHealColumn(caster);
    }

    /**
     * Reveals the healing column over {@link #COLUMN_REVEAL_TICKS} ticks on the healed
     * subject.
     *
     * <p>The builder redraws the whole revealed prefix each tick rather than emitting a
     * growing suffix, which is what lets the column be re-anchored to a moving subject
     * for free. Dust is transient, so the overlap costs nothing visually.
     *
     * @param subject the entity that was healed; the column follows it
     */
    private void drawHealColumn(LivingEntity subject) {
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
                }
                elapsed++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.heal.book_name"))
                .addLoreLine(lang().get("spells.heal.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 3))
                .build();
    }

    @Override
    public boolean canMobCast(Mob mob){
        return mob.getHealth() < mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
    }
}
