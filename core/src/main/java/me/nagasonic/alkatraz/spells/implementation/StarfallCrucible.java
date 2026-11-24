package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.util.SpellTargeting;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Circle 8 FIRE BURST: comets strike a target area over six seconds. No blocks are broken,
 * nothing is set alight, and there is no updraft: the safe lift belongs to Circle 9.
 */
public class StarfallCrucible extends AttackSpell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.FIRE);
    /** Total window for all comet strikes, in ticks. */
    private static final int STRIKE_WINDOW_TICKS = 120;
    /** How far the caster may aim the strike center, in blocks. */
    private static final double AIM_RANGE = 40.0;
    /** Radius of each comet's impact, in blocks. */
    private static final double IMPACT_RADIUS = 3.5;
    /** Damage a mob takes per comet, before totals are capped. */
    private static final double MOB_COMET_DAMAGE = 8.0;
    /** Damage a player takes per comet, before totals are capped. */
    private static final double PLAYER_COMET_DAMAGE = 3.0;
    /** Maximum total damage one mob may take from a single cast. */
    private static final double MOB_DAMAGE_CAP = 24.0;
    /** Published total PvP damage for one cast. */
    private static final double DEFAULT_PVP_TOTAL = 9.0;

    private static final Map<UUID, List<BukkitTask>> TASKS = new ConcurrentHashMap<>();

    /** Total PvP budget for one cast, from the options YAML. */
    private double pvpTotal = DEFAULT_PVP_TOTAL;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public StarfallCrucible(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/starfall_crucible_options.yml");
        Alkatraz.getInstance().save("spells/starfall_crucible.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/starfall_crucible.yml").get();
        loadCommonConfig(spellConfig);
        pvpTotal = ConfigManager.getConfig("spells/starfall_crucible_options.yml").get()
                .getDouble("pvp_damage_cap", DEFAULT_PVP_TOTAL);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead() || !p.isOnline()) {
            cancelCast(p);
            return;
        }
        World world = p.getWorld();
        Location center = resolveCenter(p);
        if (center == null) {
            cancelCast(p);
            return;
        }

        double radius = Math.max(1.0, option(p, "radius", 22));
        int comets = Math.max(1, (int) Math.round(option(p, "comets", 3)));

        UUID casterId = p.getUniqueId();
        cancelFor(casterId);
        List<BukkitTask> tasks = TASKS.computeIfAbsent(casterId, key -> new ArrayList<>());
        Map<UUID, Double> dealtToMobs = new HashMap<>();
        Map<UUID, Double> dealtToPlayers = new HashMap<>();

        Random random = ThreadLocalRandom.current();
        List<Location> strikePoints = new ArrayList<>(comets);
        for (int i = 0; i < comets; i++) {
            strikePoints.add(randomPoint(world, center, radius, random));
        }

        // Stagger strikes across the 6 s window, always including the final tick.
        for (int i = 0; i < strikePoints.size(); i++) {
            int delay = strikePoints.size() == 1
                    ? 0
                    : (int) Math.round(i * (STRIKE_WINDOW_TICKS / (double) (strikePoints.size() - 1)));
            Location point = strikePoints.get(i);
            BukkitTask strike = new BukkitRunnable() {
                @Override
                public void run() {
                    tasks.remove(this);
                    Player caster = resolveCaster(casterId, world);
                    if (caster == null) return;
                    impact(world, caster, point, dealtToMobs, dealtToPlayers);
                }
            }.runTaskLater(Alkatraz.getInstance(), delay);
            tasks.add(strike);
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Player-only in practice: no consenting/allied group to protect.
    }

    private void impact(World world, Player caster, Location point,
                        Map<UUID, Double> dealtToMobs, Map<UUID, Double> dealtToPlayers) {
        if (!caster.isOnline() || caster.isDead() || caster.getWorld() != world) return;

        SpellVfx.ring(PALETTE.accent(), caster, point.clone().add(0, 0.2, 0), IMPACT_RADIUS, 28, 0, 0.0);
        SpellVfx.column(PALETTE.core(), caster, point.clone().add(0, 0.5, 0), 1.4, 0.9, 3, 8, 1.6);
        ParticleCaster.spawn(caster, world, Utils.LAVA, point.clone().add(0, 0.4, 0), 12);
        ParticleCaster.spawn(caster, world, Utils.FLAME, point.clone().add(0, 1.0, 0), 18);

        double reach = IMPACT_RADIUS + 0.5;
        for (Entity entity : world.getNearbyEntities(point, reach, 3.5, reach)) {
            if (!(entity instanceof LivingEntity target) || target.isDead()) continue;
            if (target instanceof Player player) {
                if (SpellTargeting.isAlly(caster, player)) continue;
                applyCappedDamage(target, caster, PLAYER_COMET_DAMAGE, pvpTotal, dealtToPlayers);
            } else {
                applyCappedDamage(target, caster, MOB_COMET_DAMAGE, MOB_DAMAGE_CAP, dealtToMobs);
            }
        }
    }

    private void applyCappedDamage(LivingEntity target, Player caster, double perHit, double cap,
                                   Map<UUID, Double> dealt) {
        UUID id = target.getUniqueId();
        double soFar = dealt.getOrDefault(id, 0.0);
        if (soFar >= cap - 1e-6) return;
        double amount = Math.min(perHit, cap - soFar);
        if (amount <= 0.0) return;
        me.nagasonic.alkatraz.spells.util.SpellDamageUtil.damageWithSpell(target, amount, caster, null, this);
        dealt.put(id, soFar + amount);
    }

    private Location resolveCenter(Player p) {
        World world = p.getWorld();
        RayTraceResult result = world.rayTraceBlocks(p.getEyeLocation(),
                Utils.safeNormalize(p.getEyeLocation().getDirection()), AIM_RANGE, FluidCollisionMode.NEVER);
        Location base;
        if (result != null && result.getHitPosition() != null) {
            base = result.getHitPosition().toLocation(world);
        } else {
            base = p.getLocation().clone().add(Utils.safeNormalize(p.getLocation().getDirection()).multiply(18.0));
        }
        return groundAt(world, base);
    }

    private Location randomPoint(World world, Location center, double radius, Random random) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dist = Math.sqrt(random.nextDouble()) * radius;
        Location base = center.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        return groundAt(world, base);
    }

    private Location groundAt(World world, Location base) {
        Location probe = base.clone();
        probe.setY(Math.min(probe.getY() + 2.0, world.getMaxHeight() - 2.0));
        Block block = world.getHighestBlockAt(probe);
        Location grounded = block.getLocation().add(0.5, 1.0, 0.5);
        grounded.setY(Math.max(grounded.getY(), world.getMinHeight() + 1.0));
        return grounded;
    }

    /** Returns the caster when still valid to continue timed effects, else null. */
    private Player resolveCaster(UUID casterId, World world) {
        Player caster = Alkatraz.getInstance().getServer().getPlayer(casterId);
        if (caster == null || !caster.isOnline() || caster.isDead()) return null;
        if (caster.getWorld() != world) return null;
        return caster;
    }

    private void cancelFor(UUID casterId) {
        List<BukkitTask> tasks = TASKS.remove(casterId);
        if (tasks == null) return;
        for (BukkitTask task : tasks) {
            if (task != null) {
                task.cancel();
            }
        }
    }

    @Override
    public void cancelTasks() {
        for (UUID id : new ArrayList<>(TASKS.keySet())) {
            cancelFor(id);
        }
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 20) / 20.0;
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.3, 0),
                scale * (1.2 + phase * 1.4), 28, frame * 10, 0.0);
        SpellVfx.column(palette.core(), caster, origin.clone().add(0, 0.5, 0),
                scale * 1.1, 0.8, 3, 8, 1.5);
    }

    @Override
    public void drawAftermath(LivingEntity caster, Location origin, int tick, int totalTicks,
                              ElementPalette.Palette palette) {
        double progress = totalTicks <= 0 ? 1.0 : tick / (double) totalTicks;
        double radius = 1.5 + progress * 4.0;
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.2, 0), radius, 24, tick * 6, 0.0);
        if (tick % 12 == 0) {
            SpellVfx.column(palette.deep(), caster, origin.clone().add(0, 0.4, 0), 1.0, 0.6, 3, 6, 1.0);
        }
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
    }

    @Override
    public void onCountered(Location location) {
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
                .setDisplayName(lang().get("spells.starfallcrucible.book_name"))
                .addCustomLoreLine(lang().get("spells.starfallcrucible.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 8))
                .build();
    }
}
