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
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class Swift extends Spell {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `AIR` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(234, 246, 255);

    /** The AIR palette, resolved once so a per-tick dash trail allocates no colour objects. */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.AIR);

    /**
     * Dash trail geometry. The dash runs for {@link #DASH_TRAIL_FRAMES} frames every
     * {@link #DASH_TRAIL_PERIOD} ticks, and each frame draws one {@link SpellVfx#beam} from
     * where the caster was last frame to where they are now: a bright {@code core} spine along
     * the actual path travelled, tapering to nothing at the leading end.
     */
    private static final int DASH_TRAIL_FRAMES = 8;
    private static final long DASH_TRAIL_PERIOD = 5L;
    private static final int DASH_TRAIL_STEPS = 6;
    private static final double DASH_TRAIL_START_WIDTH = 0.6;
    private static final double DASH_TRAIL_END_WIDTH = 0.1;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public Swift(String type) {
        super(type);
    }
    private double strength;
    private static final double MAX_DASH_SPEED = 4.0;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/swift.yml");
        Alkatraz.getInstance().saveConfig("spells/swift_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/swift.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        strength = spellConfig.getDouble("dash_strength");
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            double wandPower = getWandPower(wand, p);
            double dashMultiplier = (Double) getOption("dash_style").getSelectedValue(p).getValue();
            double speed = wandPower * strength * dashMultiplier;
            if (speed > MAX_DASH_SPEED) speed = MAX_DASH_SPEED;
            Vector velocity = p.getEyeLocation().getDirection().normalize().multiply(speed);
            if (velocity.getY() > 0.5) velocity.setY(0.5);
            p.setVelocity(velocity);
            new BukkitRunnable() {
                int iterations = 0;
                Location previous = p.getLocation().clone();

                @Override
                public void run() {
                    if (iterations >= DASH_TRAIL_FRAMES) {
                        cancel();
                        return;
                    }
                    drawDashTrail(p, previous);
                    previous = p.getLocation().clone();
                    iterations++;
                }
            }.runTaskTimer(Alkatraz.getInstance(), 0L, DASH_TRAIL_PERIOD);
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (!caster.isDead()){
            double wandPower = getWandPowerOrDefault(wand);
            double speed = wandPower * strength;
            if (speed > MAX_DASH_SPEED) speed = MAX_DASH_SPEED;
            Vector velocity = caster.getEyeLocation().getDirection().normalize().multiply(speed);
            if (velocity.getY() > 0.5) velocity.setY(0.5);
            caster.setVelocity(velocity);
            new BukkitRunnable() {
                int iterations = 0;
                Location previous = caster.getLocation().clone();

                @Override
                public void run() {
                    if (iterations >= DASH_TRAIL_FRAMES) {
                        cancel();
                        return;
                    }
                    drawDashTrail(caster, previous);
                    previous = caster.getLocation().clone();
                    iterations++;
                }
            }.runTaskTimer(Alkatraz.getInstance(), 0L, DASH_TRAIL_PERIOD);
        }
    }

    /**
     * Draws one frame of the dash trail: a {@link SpellVfx#beam} from the caster's position on the
     * previous frame to their position now, plus the pale {@code core} burst that marks where the
     * dash is right now.
     *
     * <p>The beam is what makes a dash read as a dash. The old implementation spawned a static puff
     * at the caster's feet ten times a frame, which looked identical whether the caster had moved
     * a block or had not moved at all, because it never referenced where they had been.
     *
     * @param caster   the dashing entity
     * @param previous where the caster stood on the previous frame; read, never mutated
     */
    private void drawDashTrail(LivingEntity caster, Location previous) {
        Location current = caster.getLocation();
        SpellVfx.beam(CAST_COLOR, caster, previous, current, DASH_TRAIL_STEPS,
                DASH_TRAIL_START_WIDTH, DASH_TRAIL_END_WIDTH);
        ParticleCaster.spawn(caster, current.getWorld(), Utils.CLOUD, current, 6, 0.3, 0.3, 0.3, 0.1);
        ParticleCaster.spawn(caster, current.getWorld(), Utils.DUST, current, 0, PALETTE.coreDust());
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 2.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.swift.book_name"))
                .addCustomLoreLine(lang().get("spells.swift.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 2))
                .build();
    }

    @Override
    public boolean canMobCast(Mob m) {
        double dif = m.getHealth() /  m.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        if (dif <= 0.3){
            return true;
        }
        return false;
    }
}
