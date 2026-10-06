package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.api.mobs.MagicAiService;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.vfx.ChannelRegistry;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.spells.vfx.WindUp;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 6th circle summoning spell. Channels a 6-second {@link WindUp} - a floor circle and
 * hex glyphs in this spell's DARK palette - and moving or taking damage interrupts
 * the summon through the shared {@link ChannelRegistry}. On completion, 3
 * iron-armored skeleton knights mounted on skeleton horses are spawned around the
 * caster. Each knight fights whatever the caster attacks, cannot harm its summoner,
 * and despawns after {@code knight_duration} seconds.
 */
public class SkeletalKnights extends AttackSpell implements Listener {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `DARK` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(106, 13, 173);

    /**
     * The DARK palette. This spell used to carry eleven byte-identical aqua/blue colour
     * constants shared with {@code Warp}, which contradicted {@code element: DARK} and made
     * the two spells pixel-for-pixel the same ritual. Three shades now do the whole job:
     * {@code core} for the leading ring and the outer ellipse, {@code accent} for the inner
     * ring, {@code deep} for the glyph markers and spokes.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.DARK);

    /** Floor ring radius of the wind-up ritual, in blocks, before the shrink factor. */
    private static final double FLOOR_RADIUS = 2.2;
    /** Points per floor ring; matches the original hand-rolled floor circle. */
    private static final int FLOOR_RING_POINTS = 24;
    /** Glyph markers around the floor ring; six makes the hex the original drew. */
    private static final int FLOOR_GLYPHS = 6;

    /** Outer ring radius of the cast circle, in blocks. */
    private static final double CIRCLE_RADIUS = 0.8;
    /** Points on the cast circle's outer ring; matches the original 28-point ellipse. */
    private static final int CIRCLE_RING_POINTS = 28;
    /** Glyph markers on the cast circle; six makes the hex the original drew. */
    private static final int CIRCLE_GLYPHS = 6;

    /** Repaint period used when {@code circle_ticks} is missing or not a number. */
    private static final long DEFAULT_CIRCLE_TICKS = 10L;

    private final Map<UUID, List<UUID>> summonedKnights = new HashMap<>();

    private SkeletalKnightsConfig config;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    /**
     * The shared cast-circle repaint period, read from {@code circle_ticks}. Clamped to at
     * least one tick because Bukkit rejects a zero or negative period.
     */
    private static long circleTicks() {
        Object ticks = Configs.CIRCLE_TICKS.get();
        long period = (ticks instanceof Number) ? ((Number) ticks).longValue() : DEFAULT_CIRCLE_TICKS;
        return Math.max(1L, period);
    }

    public SkeletalKnights(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/skeletal_knights.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/skeletal_knights.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = SkeletalKnightsConfig.fromConfig(spellConfig.getValues(false));
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        if (profile == null) return;
        if (!Protection.spawnMob(caster, caster.getLocation())) {
            cancelCast(caster);
            return;
        }
        UUID uuid = caster.getUniqueId();
        if (ChannelRegistry.isChanneling(uuid)) return;

        // The 6-second channel is a declared paid-over: SpellBudgets.isPaidOverWindUp(6, 120)
        // is true, so this spell is allowed to run far past the circle-6 wind-up contract. It
        // is deliberately NOT shortened to WindUp.standardTicks; waystone_recall only makes
        // sense as a skip for something.
        int windUpTicks = config.windUpTicks(profile.getSpellMastery(this), getMaxMastery());
        WindUp windUp = WindUp.begin(this, caster, windUpTicks);

        // ChannelRegistry owns interruption (move / damage / quit / shutdown); this class owns
        // the repeating task. The task id is captured in a holder so the canceller handed to the
        // registry stays effectively final.
        final int[] taskId = new int[1];
        ChannelRegistry.register(uuid, windUp, () -> {
            if (taskId[0] != 0) Bukkit.getScheduler().cancelTask(taskId[0]);
        });

        taskId[0] = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= windUpTicks) {
                    cancel();
                    ChannelRegistry.unregister(uuid);
                    completeSummon(caster);
                    return;
                }
                if (caster.isDead() || !caster.isOnline()) {
                    cancel();
                    ChannelRegistry.unregister(uuid);
                    return;
                }
                windUp.tick();
                animate(caster, ticks, windUpTicks);
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L).getTaskId();
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Player-only spell.
    }

    private void completeSummon(Player caster) {
        if (caster.isDead() || !caster.isOnline()) return;

        int count = config.knightCount();
        double power = getPower(caster, config.knightPower());
        List<UUID> ids = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Location spawnLoc = findSpawnLocation(caster.getLocation());
            if (spawnLoc == null) continue;
            if (!Protection.spawnMob(caster, spawnLoc)) continue;

            SkeletonHorse horse = (SkeletonHorse) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.SKELETON_HORSE);
            horse.setTamed(true);
            horse.setOwner(caster);
            horse.setAdult();
            horse.setRemoveWhenFarAway(false);
            horse.setCustomNameVisible(false);

            Skeleton knight = (Skeleton) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.SKELETON);
            knight.setCustomName(ChatColor.DARK_RED + caster.getName() + "'s Knight");
            knight.setCustomNameVisible(false);
            knight.setRemoveWhenFarAway(false);
            Objects.requireNonNull(knight.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(20.0);
            knight.setHealth(20.0);
            Objects.requireNonNull(knight.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)).setBaseValue(power);

            ItemStack sword = new ItemStack(Material.IRON_SWORD);
            ItemMeta swordMeta = sword.getItemMeta();
            if (swordMeta != null) {
                swordMeta.setUnbreakable(true);
                sword.setItemMeta(swordMeta);
            }
            knight.getEquipment().setItemInMainHand(sword);
            knight.getEquipment().setItemInOffHand(null);
            knight.getEquipment().setHelmet(armor(Material.IRON_HELMET));
            knight.getEquipment().setChestplate(armor(Material.IRON_CHESTPLATE));
            knight.getEquipment().setLeggings(armor(Material.IRON_LEGGINGS));
            knight.getEquipment().setBoots(armor(Material.IRON_BOOTS));

            NBT.modifyPersistentData(horse, nbt -> {
                nbt.setString("summoner_uuid", caster.getUniqueId().toString());
                nbt.setBoolean("summoned_mount", true);
            });
            MagicAiService.getInstance().setBrain(horse, "summoned_horse");
            NBT.modifyPersistentData(knight, nbt -> {
                nbt.setString("summoner_uuid", caster.getUniqueId().toString());
                nbt.setBoolean("summoned_knight", true);
            });
            MagicAiService.getInstance().setBrain(knight, "summoned_knight");

            horse.addPassenger(knight);
            ids.add(knight.getUniqueId());
            ids.add(horse.getUniqueId());
            spawnSummonBurst(caster, spawnLoc.add(0, 1, 0));
        }

        if (ids.isEmpty()) {
            cancelCast(caster);
            return;
        }

        summonedKnights.put(caster.getUniqueId(), ids);
        startDespawnRunnable(caster, ids);
        spawnArrivalExpand(caster, caster.getLocation(), caster.getEyeLocation().getYaw());
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_SKELETON_HORSE_AMBIENT, 1.0f, 0.8f);
    }

    private static ItemStack armor(Material material) {
        ItemStack piece = new ItemStack(material);
        ItemMeta meta = piece.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            piece.setItemMeta(meta);
        }
        return piece;
    }

    private void startDespawnRunnable(Player caster, List<UUID> ids) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID id : ids) {
                    Entity e = Bukkit.getEntity(id);
                    if (e != null) despawnKnight(caster, e);
                }
                summonedKnights.remove(caster.getUniqueId());
            }
        }.runTaskLater(Alkatraz.getInstance(), config.knightDuration() * 20L);
    }

    private Location findSpawnLocation(Location center) {
        World world = center.getWorld();
        double range = config.summonRange();
        for (int i = 0; i < 10; i++) {
            double x = center.getX() + (Math.random() - 0.5) * range * 2;
            double z = center.getZ() + (Math.random() - 0.5) * range * 2;
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            int y = world.getHighestBlockYAt(bx, bz) + 1;
            if (world.getBlockAt(bx, y, bz).getType() == Material.AIR && world.getBlockAt(bx, y - 1, bz).getType().isSolid()) {
                return new Location(world, x, y, z);
            }
        }
        return null;
    }

    private void spawnSummonBurst(LivingEntity caster, Location loc) {
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.PORTAL, loc, 40, 0.5, 0.5, 0.5, 0.05);
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc, 20, 0.3, 0.3, 0.3, 0,
                PALETTE.coreDust());
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.LARGE_SMOKE, loc, 10, 0.3, 0.3, 0.3, 0.05);
    }

    private void despawnKnight(LivingEntity caster, Entity e) {
        if (e.isDead()) {
            e.remove();
            return;
        }
        ParticleCaster.spawn(caster, e.getWorld(), Utils.LARGE_SMOKE, e.getLocation(), 15, 0.3, 0.3, 0.3, 0.05);
        ParticleCaster.spawn(caster, e.getWorld(), Utils.WITCH, e.getLocation(), 10, 0.3, 0.3, 0.3, 0);
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_SKELETON_DEATH, 0.6f, 0.8f);
        e.remove();
    }

    // ================= Channel animation =================

    private void animate(Player caster, int ticks, int windUpTicks) {
        int actSwitch = Math.max(1, windUpTicks / 2);
        int remaining = windUpTicks - ticks;
        double shrink = remaining <= 10 ? Math.max(0.15, remaining / 10.0) : 1.0;
        double fullProgress = (double) ticks / windUpTicks;
        long circleTicks = circleTicks();

        Location feet = caster.getLocation();
        double floorR = Math.min(FLOOR_RADIUS, 0.8 + 1.4 * (ticks / (double) actSwitch)) * shrink;
        // The floor circle and the hex glyphs are one builder now: counter-rotating rings with
        // glyph markers, coloured from the DARK palette rather than from hardcoded aqua.
        SpellVfx.channelGlyphs(CAST_COLOR, caster, feet, floorR, FLOOR_RING_POINTS, FLOOR_GLYPHS,
                Math.toRadians(ticks * 6));

        if (ticks >= actSwitch) {
            Location eye = caster.getEyeLocation();
            Location center = eye.clone().add(eye.getDirection().multiply(1.8));
            float yaw = eye.getYaw();
            double p2 = (double) (ticks - actSwitch) / (windUpTicks - actSwitch);
            double hw = (1.2 + 1.2 * p2) * shrink;
            double halfH = hw * 1.1;
            World world = center.getWorld();
            double yawRad = Math.toRadians(yaw);
            double leftX = Math.cos(yawRad);
            double leftZ = Math.sin(yawRad);
            Location scratch = center.clone();
            final int outerPoints = 28;
            final int innerPoints = 20;

            for (int i = 0; i < outerPoints; i++) {
                ellipsePoint(center, leftX, leftZ, hw, halfH, outerPoints, (i + ticks) % outerPoints, scratch);
                ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, PALETTE.coreDust());
            }
            int innerShift = Math.floorMod(-ticks * 2, innerPoints);
            for (int i = 0; i < innerPoints; i++) {
                int index = i + innerShift;
                if (index >= innerPoints) index -= innerPoints;
                ellipsePoint(center, leftX, leftZ, hw * 0.6, halfH * 0.6, innerPoints, index, scratch);
                ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, PALETTE.accentDust());
            }
            for (int s = 0; s < 6; s++) {
                ellipsePoint(center, leftX, leftZ, hw, halfH, outerPoints, (s * outerPoints) / 6, scratch);
                double frac = 1.0 - ((ticks + s) % 10) / 10.0;
                double tipX = (scratch.getX() - center.getX()) * frac;
                double tipY = (scratch.getY() - center.getY()) * frac;
                double tipZ = (scratch.getZ() - center.getZ()) * frac;
                scratch.setX(center.getX() + tipX);
                scratch.setY(center.getY() + tipY);
                scratch.setZ(center.getZ() + tipZ);
                ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, PALETTE.deepDust());
            }
        }

        if (ticks % circleTicks == 0) {
            float pitch = 0.6f + (float) fullProgress * 0.6f;
            caster.getWorld().playSound(caster.getEyeLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, pitch);
        }
    }

    private static void ellipsePoint(Location center, double leftX, double leftZ,
                                     double halfWidth, double halfHeight, int points, int index,
                                     Location scratch) {
        double angle = 2 * Math.PI * index / points;
        double spanX = Math.cos(angle) * halfWidth;
        double spanY = Math.sin(angle) * halfHeight;
        scratch.setX(center.getX() + leftX * spanX);
        scratch.setY(center.getY() + spanY);
        scratch.setZ(center.getZ() + leftZ * spanX);
    }

    private void spawnArrivalExpand(Player caster, Location destination, float yaw) {
        World world = destination.getWorld();
        if (world == null || !world.isChunkLoaded(destination.getBlockX() >> 4, destination.getBlockZ() >> 4)) return;
        Location center = destination.clone().add(0, 1.1, 0);
        double yawRad = Math.toRadians(yaw);
        double leftX = Math.cos(yawRad);
        double leftZ = Math.sin(yawRad);
        new BukkitRunnable() {
            int expand = 0;
            final Location scratch = center.clone();

            @Override
            public void run() {
                if (expand >= 10) {
                    cancel();
                    return;
                }
                double hw = 0.6 + expand * 0.18;
                for (int i = 0; i < 36; i++) {
                    ellipsePoint(center, leftX, leftZ, hw, hw * 1.1, 36, i, scratch);
                    ParticleCaster.spawn(caster, world, Utils.DUST, scratch, 0, PALETTE.coreDust());
                }
                expand++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @EventHandler
    private void onKnightDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Skeleton skeleton)) return;
        SummonCombatGuard.SummonTag tag = SummonCombatGuard.readSummonTag(skeleton, "summoned_knight");
        if (tag == null || !tag.flagged()) return;
        String summonerId = tag.summonerUuid();
        if (summonerId == null) return;
        try {
            if (e.getEntity() instanceof Player p && p.getUniqueId().equals(UUID.fromString(summonerId))) {
                e.setCancelled(true);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    @EventHandler
    private void onPlayerCombat(EntityDamageByEntityEvent e) {
        Player player = null;
        LivingEntity target = null;

        if (e.getDamager() instanceof Player p && e.getEntity() instanceof LivingEntity lt) {
            player = p;
            target = lt;
        } else if (e.getEntity() instanceof Player p && e.getDamager() instanceof LivingEntity lt) {
            if (lt instanceof Skeleton skeleton) {
                SummonCombatGuard.SummonTag tag = SummonCombatGuard.readSummonTag(skeleton, "summoned_knight");
                if (tag != null && tag.summonerUuid() != null && tag.summonerUuid().equals(p.getUniqueId().toString())) return;
            }
            player = p;
            target = lt;
        }

        if (player == null || target == null) return;
        if (SummonCombatGuard.isSummon(target)) return;
        List<UUID> ids = summonedKnights.get(player.getUniqueId());
        if (ids == null || ids.isEmpty()) return;

        for (UUID id : ids) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof Mob mob && !mob.isDead()) {
                mob.setTarget(target);
            }
        }
    }

    /**
     * The cast circle. This used to be a hand-rolled 28-point <i>vertical</i> ellipse in front
     * of the caster's eyes, byte-identical to {@code Warp}'s and aqua on a DARK spell. It is now
     * the shared {@link SpellVfx#channelGlyphs} floor ritual at the caster's feet, so the two
     * spells share one code path and one element-driven palette.
     *
     * <p>The vertical orientation is the one part of the old picture that could not be kept -
     * see the C-DARK migration report. The caster's location is read fresh every repaint, never
     * cached, and a cancelled prepare stops the task instead of only skipping a frame.
     */
    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (e.isCancelled()) {
                    cancel();
                    return;
                }
                SpellVfx.channelGlyphs(CAST_COLOR, caster, caster.getLocation(),
                        CIRCLE_RADIUS, CIRCLE_RING_POINTS, CIRCLE_GLYPHS,
                        Math.toRadians(ticks * 6));
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, circleTicks()).getTaskId();
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {}

    @Override
    public void onCountered(Location location) {}

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.skeletalknights.book_name"))
                .addCustomLoreLine(lang().get("spells.skeletalknights.lore1"))
                .addCustomLoreLine(lang().get("spells.skeletalknights.lore2"))
                .addRequirement(new NumberStatRequirement<>("circleLevel", 6))
                .build();
    }
}
