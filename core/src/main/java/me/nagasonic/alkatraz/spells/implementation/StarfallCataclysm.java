package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.util.SpellMarks;
import me.nagasonic.alkatraz.spells.util.SpellTargeting;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Circle 9 FIRE BURST: star-fragments fall across a wide area, shatter the ground and
 * scorch everything below. Each fragment hits every living entity near its impact point
 * (players take 40%), and the impact craters unprotected terrain unless the caster turns
 * {@code damage_blocks} off.
 */
public class StarfallCataclysm extends AttackSpell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.FIRE);
    /** Radius of each fragment's impact. */
    private static final double BLAST_RADIUS = 8.0;
    /** Ticks between staggered fragments. */
    private static final int STAGGER_TICKS = 12;
    /** Published total PvP damage for one cast, split across the allowed hits. */
    private static final double DEFAULT_PVP_TOTAL = 12.0;
    /** Safe updraft duration after the last fragment, in ticks. */
    private static final int UPDRAFT_TICKS = 160;
    /** Safe updraft tick interval. */
    private static final int UPDRAFT_INTERVAL = 4;
    /** Radius of the crater a fragment tears. */
    private static final double CRATER_RADIUS = 3.5;
    /** Crater budget in blocks. */
    private static final int CRATER_BUDGET = 10;
    /** Hard ceiling on hits one target may take from a single cast. */
    private static final int HIT_CAP = 3;

    /** Hits one target may take, from the spell's {@code hit_count}. */
    private int hitCap = HIT_CAP;
    /** Total PvP budget for one cast, from the options YAML. */
    private double pvpTotal = DEFAULT_PVP_TOTAL;

    private static final Map<UUID, List<BukkitTask>> TASKS = new ConcurrentHashMap<>();

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public StarfallCataclysm(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/starfall_cataclysm_options.yml");
        Alkatraz.getInstance().save("spells/starfall_cataclysm.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/starfall_cataclysm.yml").get();
        loadCommonConfig(spellConfig);
        hitCap = Math.min(HIT_CAP, Math.max(1, spellConfig.getInt("hit_count", HIT_CAP)));
        pvpTotal = ConfigManager.getConfig("spells/starfall_cataclysm_options.yml").get()
                .getDouble("pvp_damage_cap", DEFAULT_PVP_TOTAL);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead() || !p.isOnline()) {
            cancelCast(p);
            return;
        }
        Location center = p.getLocation().clone();
        World world = p.getWorld();

        int fragments = Math.max(1, (int) Math.round(option(p, "fragments", 3)));
        double radius = Math.max(1.0, option(p, "radius", 32));

        UUID casterId = p.getUniqueId();
        cancelFor(casterId);
        List<BukkitTask> tasks = TASKS.computeIfAbsent(casterId, key -> new ArrayList<>());
        Map<UUID, Integer> castHits = new HashMap<>();
        Set<UUID> cappedTargets = new HashSet<>();

        // The strike radius is telegraphed by drawCastMotif during the framework
        // wind-up. The exact fragment marks are laid down here as the payload starts.
        List<Location> targets = new ArrayList<>(fragments);
        for (int i = 0; i < fragments; i++) {
            Location target = randomPoint(world, center, radius);
            targets.add(target);
            SpellMarks.mark(p, target, PALETTE, lang().get("spells.starfallcataclysm.mark"));
        }

        // The framework wind-up has completed before castAction runs; schedule the
        // staggered fragments directly with no local channel.
        for (int i = 0; i < targets.size(); i++) {
            Location target = targets.get(i);
            Player caster = Bukkit.getPlayer(casterId);
            BukkitTask strike = new BukkitRunnable() {
                @Override
                public void run() {
                    tasks.remove(this);
                    strike(world, caster, target, castHits, cappedTargets);
                }
            }.runTaskLater(Alkatraz.getInstance(), i * STAGGER_TICKS);
            tasks.add(strike);
        }

        startUpdraft(casterId, world, center, radius,
                (fragments - 1) * STAGGER_TICKS, tasks);
    }

    /** After the fragments land, a safe thermal lifts the caster and their allies clear. */
    private void startUpdraft(UUID casterId, World world, Location center, double radius,
                              int delayTicks, List<BukkitTask> tasks) {
        BukkitTask updraft = new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                Player caster = Bukkit.getPlayer(casterId);
                if (caster == null || !caster.isOnline() || caster.isDead()
                        || caster.getWorld() != world || ticks >= UPDRAFT_TICKS) {
                    tasks.remove(this);
                    cancel();
                    return;
                }
                lift(world, center, radius, caster);
                if (ticks % 10 == 0) {
                    SpellVfx.column(PALETTE.accent(), caster, center.clone().add(0, 0.2, 0),
                            1.2, radius * 0.4, 4, 10, 0.8);
                }
                ticks += 4;
            }
        }.runTaskTimer(Alkatraz.getInstance(), delayTicks, UPDRAFT_INTERVAL);
        tasks.add(updraft);
    }

    /** Lifts only the caster and their allies; enemy players are never moved. */
    private void lift(World world, Location center, double radius, Player caster) {
        double reach = radius + 2.0;
        for (Entity entity : world.getNearbyEntities(center, reach, 6.0, reach)) {
            if (!(entity instanceof LivingEntity target) || target.isDead()) continue;
            Location loc = target.getLocation();
            double dx = loc.getX() - center.getX();
            double dz = loc.getZ() - center.getZ();
            if (dx * dx + dz * dz > radius * radius) continue;
            if (target instanceof Player player && !player.equals(caster)
                    && !SpellTargeting.isAlly(caster, player)) continue;
            Vector velocity = target.getVelocity();
            double liftY = Math.min(0.55, Math.max(velocity.getY(), 0.18));
            target.setVelocity(new Vector(velocity.getX() * 0.85, liftY, velocity.getZ() * 0.85));
            target.setFallDistance(0.0f);
        }
    }

    private void strike(World world, Player caster, Location target,
                        Map<UUID, Integer> hits, Set<UUID> cappedTargets) {
        SpellMarks.mark(caster, target, PALETTE, lang().get("spells.starfallcataclysm.mark"));
        // The attack's own spectacle: an expanding nova plus a falling column.
        SpellVfx.nova(PALETTE.core(), caster, target, BLAST_RADIUS, 48);
        SpellVfx.column(PALETTE.accent(), caster, target, 6.0, 1.4, 4, 6, 0.4);
        // Magma splash, then the deeper mantle glow and column that separate this from circle 8.
        ParticleCaster.spawn(caster, world, Utils.LAVA, target.clone().add(0, 0.05, 0),
                3, 0.5, 0.4, 0.5, 0.0);
        SpellVfx.ring(PALETTE.deep(), caster, target.clone().add(0, 0.05, 0),
                BLAST_RADIUS * 1.6, 24, 0, 0.1);
        SpellVfx.column(PALETTE.core(), caster, target.clone().add(0, 0.1, 0),
                7.0, 2.0, 5, 10, 0.5);
        for (int i = 0; i < 10; i++) {
            ParticleCaster.spawn(caster, world, Utils.LAVA, target.clone().add(
                    (Math.random() - 0.5) * 4, Math.random() * 2, (Math.random() - 0.5) * 4),
                    2, 0.3, 0.3, 0.3, 0.05);
        }

        for (Entity entity : world.getNearbyEntities(target, BLAST_RADIUS, BLAST_RADIUS, BLAST_RADIUS)) {
            if (!(entity instanceof LivingEntity living) || living.equals(caster) || living.isDead()) continue;
            UUID id = living.getUniqueId();
            if (cappedTargets.contains(id)) continue;
            int used = hits.getOrDefault(id, 0);
            if (used >= hitCap) continue;
            hits.put(id, used + 1);
            if (used + 1 >= hitCap) cappedTargets.add(id);
            double base = getBasePower();
            damageArea(caster, living, base, pvpTotal / Math.max(1, hitCap), null);
            living.setFireTicks(50);
        }

        if (damageBlocks(caster)) {
            TerrainEditor.destroySphere(caster, target, CRATER_RADIUS, false, CRATER_BUDGET);
        }
    }

    /** Whether fragments crater the ground. */
    private boolean damageBlocks(Player p) {
        SpellOption option = getOption("damage_blocks");
        if (option == null) return true;
        Object value = option.getSelectedValue(p).getValue();
        return value instanceof Boolean b ? b : true;
    }

    private Location randomPoint(World world, Location center, double radius) {
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double distance = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * radius;
        Location point = center.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
        point.setY(world.getHighestBlockYAt(point) + 1.0);
        return point;
    }

    private static void cancelFor(UUID casterId) {
        List<BukkitTask> tasks = TASKS.remove(casterId);
        if (tasks == null) return;
        for (BukkitTask task : tasks) {
            if (task != null) task.cancel();
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
        // Reveal the marked strike radius during the framework wind-up. The old
        // code marked fragment points only at impact, leaving no time to dodge.
        if (caster instanceof Player player && frame % 3 == 0) {
            double radius = option(player, "radius", 32);
            SpellVfx.ring(palette.deep(), caster, origin.clone().add(0, 0.12, 0),
                    radius, 24, frame * 4, 0.0);
        }
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

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Player-only in practice: no consenting/allied group to crater safely.
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
                .setDisplayName(lang().get("spells.starfallcataclysm.book_name"))
                .addCustomLoreLine(lang().get("spells.starfallcataclysm.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}