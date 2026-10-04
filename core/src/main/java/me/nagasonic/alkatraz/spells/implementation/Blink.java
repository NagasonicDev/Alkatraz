package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class Blink extends Spell {

    /**
     * Blink is element NONE, so the pop and the cast circle draw in neutral grey
     * rather than in the violet this spell used to hardcode.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.NONE);

    /** The pop at each end of a blink; pre-built so a blink allocates no colour objects. */
    private static final Particle.DustOptions POP_DUST = PALETTE.coreDust();

    /** Steps in the origin-to-destination beam, and its half-width at each end. */
    private static final int BEAM_STEPS = 12;
    private static final double BEAM_START_WIDTH = 0.2;
    private static final double BEAM_END_WIDTH = 0.6;

    private double blinkDistance;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public Blink(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/blink_options.yml");
        Alkatraz.getInstance().save("spells/blink.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/blink.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.blinkDistance = spellConfig.getDouble("blink_distance");
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;


        double distance = (Double) getOption("blink_distance").getSelectedValue(caster).getValue();

        Vector direction = caster.getEyeLocation().getDirection().normalize();
        Location start = caster.getEyeLocation();
        Location target = findTeleportLocation(start, direction, distance);

        if (target == null) {
            Utils.sendActionBar(caster, lang().get("spells.blink.no_valid_location"));
            cancelCast(caster);
            return;
        }

        if (!Protection.teleport(caster, target)) {
            Utils.sendActionBar(caster, lang().get("spells.blink.no_valid_location"));
            cancelCast(caster);
            return;
        }

        spawnBlinkBeam(caster, start, target);
        spawnTeleportParticles(caster, start);
        caster.teleport(target);
        spawnTeleportParticles(caster, target);

        caster.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead()) return;


        if (caster.getTarget() == null) return;

        Vector direction = Utils.safeNormalize(caster.getTarget().getLocation().toVector()
                .subtract(caster.getLocation().toVector()));
        Location start = caster.getLocation();
        Location target = findTeleportLocation(start, direction, blinkDistance);

        if (target == null) return;

        if (!Protection.teleport(caster instanceof Player ? (Player) caster : null, target)) {
            return;
        }

        spawnBlinkBeam(caster, start, target);
        spawnTeleportParticles(caster, start);
        caster.teleport(target);
        spawnTeleportParticles(caster, target);

        caster.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
    }

    private Location findTeleportLocation(Location start, Vector direction, double maxDistance) {
        Location prev = start.clone();
        boolean moved = false;

        for (double d = 0.5; d <= maxDistance; d += 0.5) {
            Location current = start.clone().add(direction.clone().multiply(d));
            Block block = current.getBlock();

            if (block.getType().isSolid()) {
                if (moved) {
                    prev.setYaw(start.getYaw());
                    prev.setPitch(start.getPitch());
                    return prev;
                }
                return null;
            }

            prev = current;
            moved = true;
        }

        Location end = start.clone().add(direction.clone().multiply(maxDistance));
        end.setYaw(start.getYaw());
        end.setPitch(start.getPitch());
        return end;
    }

    private void spawnTeleportParticles(LivingEntity caster, Location loc) {
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.PORTAL, loc, 40, 0.5, 0.5, 0.5, 0.5);
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc, 15, 0.3, 0.3, 0.3, 0,
                POP_DUST);
    }

    /**
     * The thread a blink leaves behind: a tapered line from where the caster was to
     * where they ended up, thin at the origin and wide at the arrival so the eye is
     * pulled to the destination. Shared geometry and palette with every other beam
     * in the plugin.
     */
    private void spawnBlinkBeam(LivingEntity caster, Location origin, Location target) {
        SpellVfx.beam(this, caster, origin, target, BEAM_STEPS, BEAM_START_WIDTH, BEAM_END_WIDTH);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.blink.book_name"))
                .addCustomLoreLine(lang().get("spells.blink.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 4))
                .build();
    }
}
