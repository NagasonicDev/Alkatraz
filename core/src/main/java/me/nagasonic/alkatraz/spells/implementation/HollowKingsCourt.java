package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.SummonLimits;
import me.nagasonic.alkatraz.spells.util.SustainEffect;
import me.nagasonic.alkatraz.spells.util.TerrainEditor;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 9 DARK SUSTAIN: a 30-second shadow court of free-roaming spectral knights.
 * The knights hunt every non-player mob inside the court; players are never targeted,
 * slowed, moved or damaged.
 */
public class HollowKingsCourt extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.DARK);
    private static final int STRIKE_INTERVAL = 50;
    private static final int TICK_INTERVAL = 10;

    private static final Map<UUID, Court> LIVE = new ConcurrentHashMap<>();

    private int radius;
    private int knights;
    private int durationSeconds;
    private double strikeDamage;

    private static final class Court {
        final UUID owner;
        final World world;
        final Location center;
        final List<Vex> knights = new ArrayList<>();
        final Set<UUID> marked = ConcurrentHashMap.newKeySet();
        long expiresAt;
        int elapsedTicks;

        Court(UUID owner, World world, Location center) {
            this.owner = owner;
            this.world = world;
            this.center = center;
        }
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public HollowKingsCourt(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/hollow_kings_court_options.yml");
        Alkatraz.getInstance().save("spells/hollow_kings_court.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/hollow_kings_court.yml").get();
        loadCommonConfig(spellConfig);
        this.radius = spellConfig.getInt("radius", 18);
        this.knights = spellConfig.getInt("knights", 4);
        this.durationSeconds = spellConfig.getInt("duration", 30);
        this.strikeDamage = spellConfig.getDouble("strike_damage", 5.0);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead() || !p.isOnline()) {
            cancelCast(p);
            return;
        }

        int reach = Math.max(4, (int) Math.round(
                getModifiedStat(p, "court_radius", option(p, "radius", radius))));
        int count = Math.max(1, (int) Math.round(
                getModifiedStat(p, "court_knights", option(p, "knights", knights))));
        Court court = new Court(p.getUniqueId(), p.getWorld(), p.getLocation().clone());
        court.expiresAt = System.currentTimeMillis() + Math.max(1, durationSeconds) * 1000L;
        UUID ownerId = court.owner;

        SustainEffect.Toggle toggle = SustainEffect.toggle(p, 0.0, this,
                (caster, elapsedTicks) -> tick(court, reach, elapsedTicks),
                caster -> settle(ownerId));
        if (toggle == SustainEffect.Toggle.BLOCKED) {
            Utils.sendActionBar(p, lang().get("spells.sustain.blocked"));
            cancelCast(p);
            return;
        }
        // A second cast closes the court; the teardown already released every knight.
        if (toggle == SustainEffect.Toggle.CLOSED) return;

        LIVE.put(ownerId, court);
        // The framework wind-up has completed before castAction runs; no local channel.
        spawnKnights(court, count);
    }

    /** The charge is done: the spectral knights rise onto the court circle. */
    private void spawnKnights(Court court, int count) {
        Player p = Bukkit.getPlayer(court.owner);
        if (p == null || !p.isOnline()) {
            settle(court.owner);
            return;
        }
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 / count) * i;
            Location spawn = court.center.clone().add(Math.cos(angle) * 2.5, 1.0, Math.sin(angle) * 2.5);
            Vex knight = p.getWorld().spawn(spawn, Vex.class, vex -> {
                vex.setAI(true);
                vex.setInvulnerable(true);
                vex.setSilent(true);
                vex.setPersistent(false);
                vex.setRemoveWhenFarAway(false);
                vex.setCollidable(false);
            });
            if (!SummonLimits.register(p, knight, count)) {
                knight.remove();
                continue;
            }
            court.knights.add(knight);
        }
        Utils.sendActionBar(p, lang().get("spells.hollowkingscourt.opened")
                .replace("{seconds}", String.valueOf(durationSeconds)));
    }

    /** Sustain body: runs every tick, so the visual work stays on the original 10-tick beat. */
    private boolean tick(Court court, int reach, int elapsedTicks) {
        Player owner = Bukkit.getPlayer(court.owner);
        if (owner == null) return false;
        if (System.currentTimeMillis() >= court.expiresAt) return false;
        // Gone knights leave the list rather than stranding roam() on a dead target.
        court.knights.removeIf(knight -> knight == null || !knight.isValid());
        if (court.knights.isEmpty()) return true;
        if (elapsedTicks % TICK_INTERVAL != 0) return true;
        for (Vex knight : court.knights) {
            roam(court, reach, knight);
        }

        double phase = (System.currentTimeMillis() % 6000L) / 6000.0 * Math.PI * 2;

        SpellVfx.ring(PALETTE.core(), owner, court.center.clone().add(0, 0.2, 0), reach, 48, phase, 0.0);
        if (Math.random() < 0.5) {
            ParticleCaster.spawn(owner, court.world, Utils.PORTAL,
                    court.center.clone().add((Math.random() - 0.5) * reach,
                            0.5 + Math.random() * 3.0, (Math.random() - 0.5) * reach),
                    2, 0.4, 0.4, 0.4, 0.02);
        }

        for (Entity nearby : court.world.getNearbyEntities(court.center, reach, reach, reach)) {
            if (!(nearby instanceof LivingEntity mob) || mob.isDead()) continue;
            if (court.knights.contains(mob)) continue;
            // The knights never slow, glow, retarget, damage or move any player,
            // including the caster.
            if (mob instanceof Player) continue;
            if (court.marked.add(mob.getUniqueId())) {
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 0, false, false));
                mob.setGlowing(true);
            }
            Vex knight = court.knights.get(Math.floorMod(mob.getUniqueId().hashCode(), court.knights.size()));
            if (knight != null && knight.isValid()) {
                if (mob instanceof Mob creature
                        && (creature.getTarget() == null || creature.getTarget() instanceof Player)) {
                    creature.setTarget(knight);
                }
                if (court.elapsedTicks % STRIKE_INTERVAL == 0) {
                    me.nagasonic.alkatraz.spells.util.SpellDamageUtil.damageWithSpell(mob, strikeDamage, owner, null, this);
                    mob.setFireTicks(0);
                    if (damageBlocks(owner)) {
                        TerrainEditor.destroySphere(owner, knight.getLocation(), 2.5, false, 8);
                    }
                }
            }
        }
        court.elapsedTicks += TICK_INTERVAL;
        return true;
    }

    /**
     * One 10-tick beat of knight roaming. The knights pathfind with AI on; these guards
     * keep them inside the court: a target that left the court or died is re-acquired,
     * and a knight that is idle inside the court is parked back on the circle with a
     * fresh target, so it cannot be stranded or stuck.
     */
    private void roam(Court court, int reach, Vex knight) {
        LivingEntity target = knight.getTarget();
        if (target == null || target.isDead() || court.center.distance(target.getLocation()) > reach) {
            // Target gone or outside the court: pathfind to the nearest court mob.
            knight.setTarget(nearestCourtMob(court, reach));
            return;
        }
        if (isIdle(knight)) {
            // Idle inside the court: park on the circle and chase a fresh target.
            double angle = Math.random() * Math.PI * 2;
            knight.teleport(court.center.clone().add(Math.cos(angle) * 3.0, 1.5, Math.sin(angle) * 3.0));
            knight.setTarget(nearestCourtMob(court, reach));
        }
    }

    /** A knight is idle when it has stopped moving and is not already at its target. */
    private boolean isIdle(Vex knight) {
        LivingEntity target = knight.getTarget();
        if (target == null || target.isDead() || !target.isValid()) return true;
        if (knight.getLocation().distance(target.getLocation()) < 1.0) return false;
        return knight.getVelocity().lengthSquared() < 0.02;
    }

    /** The nearest non-player mob inside the court, excluding the knights. */
    private LivingEntity nearestCourtMob(Court court, int reach) {
        LivingEntity best = null;
        double bestDistance = reach * reach;
        for (Entity nearby : court.world.getNearbyEntities(court.center, reach, reach, reach)) {
            if (!(nearby instanceof LivingEntity mob) || mob.isDead() || !mob.isValid()) continue;
            if (court.knights.contains(mob)) continue;
            if (mob instanceof Player) continue;
            double distance = court.center.distance(mob.getLocation());
            if (distance > bestDistance) continue;
            best = mob;
            bestDistance = distance;
        }
        if (best == null) {
            // Nothing to hunt: the knights fall back to the owner as their anchor.
            best = Bukkit.getPlayer(court.owner);
        }
        return best;
    }

    /** Whether knights smash terrain where they strike a mob. */
    private boolean damageBlocks(Player p) {
        SpellOption option = getOption("damage_blocks");
        if (option == null) return true;
        Object value = option.getSelectedValue(p).getValue();
        return value instanceof Boolean b ? b : true;
    }

    /** Idempotent: removes every knight, clears the marks and cancels the domain. */
    private static void settle(UUID ownerId) {
        Court court = LIVE.remove(ownerId);
        if (court == null) return;
        for (Vex knight : court.knights) {
            if (knight != null && knight.isValid()) knight.remove();
        }
        court.knights.clear();
        for (UUID id : court.marked) {
            var entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) entity.setGlowing(false);
        }
        court.marked.clear();
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
    }

    @Override
    public void cancelTasks() {
        for (UUID id : new ArrayList<>(LIVE.keySet())) {
            settle(id);
        }
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        SpellVfx.ring(palette.deep(), caster, origin.clone().add(0, 0.15, 0), scale * 3.0, 36, -frame * 2, 0.0);
        SpellVfx.orbit(palette.core(), caster, origin.clone().add(0, 1.2, 0),
                scale * 1.2, scale * 2.2, 4, frame * 0.14);
    }

    @Override
    public void drawAftermath(LivingEntity caster, Location origin, int tick, int totalTicks,
                              ElementPalette.Palette palette) {
        double progress = totalTicks <= 0 ? 1.0 : (double) tick / totalTicks;
        if (tick % 6 == 0) {
            SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.25, 0),
                    3.0 + progress * 12.0, 40, tick * 3, 0.1);
        }
        if (tick % 9 == 0) {
            SpellVfx.column(palette.deep(), caster, origin, 4.0 + progress * 3.0, 1.2, 4, 6, 0.5);
        }
    }

    /** Numeric option value, falling back when the option file is absent. */
    private double option(Player p, String id, double fallback) {
        if (getOption(id) == null) return fallback;
        Object value = getOption(id).getSelectedValue(p).getValue();
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.hollowkingscourt.book_name"))
                .addCustomLoreLine(lang().get("spells.hollowkingscourt.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}

