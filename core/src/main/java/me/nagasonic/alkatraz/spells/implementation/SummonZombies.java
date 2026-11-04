package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.SummonSpawnLocation;
import me.nagasonic.alkatraz.util.Utils;
import me.nagasonic.alkatraz.api.mobs.MagicAiService;
import org.bukkit.Color;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class SummonZombies extends AttackSpell implements Listener {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `DARK` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(106, 13, 173);

    /**
     * The DARK palette. This spell's cast circle used to be a hand-built
     * {@code magicCircle} tinted {@code Color.fromRGB(50, 100, 50)} - green on a DARK spell,
     * the second-worst colour contradiction in the roster. It is now driven entirely by the
     * element palette, so nothing here names a colour at all.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.DARK);

    /** Radius of the ground ring drawn where each zombie breaks the surface, in blocks. */
    private static final double SUMMON_RING_RADIUS = 0.7;
    /** Points on that ground ring. */
    private static final int SUMMON_RING_POINTS = 16;
    /** Downward offset of the shadow ring under it, in blocks; zero would skip the shadow. */
    private static final double SUMMON_RING_SHADOW = 0.15;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private int zombieCount;
    private int zombieDuration;
    private double zombiePower;
    private double summonRange;

    private final Map<UUID, List<UUID>> summonedZombies = new HashMap<>();

    public SummonZombies(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/summon_zombies_options.yml");
        Alkatraz.getInstance().save("spells/summon_zombies.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/summon_zombies.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.zombieCount = spellConfig.getInt("zombie_count");
        this.zombieDuration = spellConfig.getInt("zombie_duration");
        this.zombiePower = spellConfig.getDouble("zombie_power");
        this.summonRange = spellConfig.getDouble("summon_range");
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;

        int count = (int) getModifiedStat(caster, "zombie_count",
                ((Number) getOption("zombie_count").getSelectedValue(caster).getValue()).intValue());
        double power = getPower(caster, zombiePower);

        List<UUID> zombieIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Location spawnLoc = SummonSpawnLocation.resolve(caster);
            if (spawnLoc == null) continue;

            if (!Protection.spawnMob(caster, spawnLoc)) continue;

            Zombie zombie = (Zombie) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ZOMBIE);
            zombie.setAdult();
            zombie.setCustomName(ChatColor.DARK_RED + caster.getName() + "'s Zombie");
            zombie.setCustomNameVisible(false);
            zombie.setRemoveWhenFarAway(false);
            Objects.requireNonNull(zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(20.0);
            zombie.setHealth(20.0);
            zombie.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(power);

            NBT.modifyPersistentData(zombie, nbt -> {
                nbt.setString("summoner_uuid", caster.getUniqueId().toString());
                nbt.setBoolean("summoned_zombie", true);
            });
            MagicAiService aiService = MagicAiService.getInstance();
            if (aiService != null) {
                aiService.setBrain(zombie, "summoned_zombie");
            }
            zombieIds.add(zombie.getUniqueId());

            spawnSummonParticles(caster, spawnLoc);
        }

        if (zombieIds.isEmpty()) {
            cancelCast(caster);
            return;
        }

        summonedZombies.put(caster.getUniqueId(), zombieIds);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID id : zombieIds) {
                    Entity e = Bukkit.getEntity(id);
                    if (e != null) despawnZombie(caster, e);
                }
                summonedZombies.remove(caster.getUniqueId());
            }
        }.runTaskLater(Alkatraz.getInstance(), zombieDuration * 20L);

        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.8f, 0.6f);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (caster.isDead()) return;

        List<UUID> zombieIds = new ArrayList<>();

        for (int i = 0; i < zombieCount; i++) {
            Location spawnLoc = SummonSpawnLocation.resolve(caster);
            if (spawnLoc == null) continue;

            if (!Protection.spawnMob(caster, spawnLoc)) continue;

            Zombie zombie = (Zombie) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ZOMBIE);
            zombie.setAdult();
            zombie.setCustomNameVisible(false);
            zombie.setRemoveWhenFarAway(false);
            Objects.requireNonNull(zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(20.0);
            zombie.setHealth(20.0);
            zombie.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(zombiePower);

            NBT.modifyPersistentData(zombie, nbt -> {
                nbt.setString("summoner_uuid", caster.getUniqueId().toString());
                nbt.setBoolean("summoned_zombie", true);
            });
            MagicAiService aiService = MagicAiService.getInstance();
            if (aiService != null) {
                aiService.setBrain(zombie, "summoned_zombie");
            }
            zombieIds.add(zombie.getUniqueId());
            spawnSummonParticles(caster, spawnLoc);
        }

        summonedZombies.put(caster.getUniqueId(), zombieIds);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID id : zombieIds) {
                    Entity e = Bukkit.getEntity(id);
                    if (e != null) despawnZombie(caster, e);
                }
                summonedZombies.remove(caster.getUniqueId());
            }
        }.runTaskLater(Alkatraz.getInstance(), zombieDuration * 20L);
    }

    private void spawnSummonParticles(LivingEntity caster, Location loc) {
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.WITCH, loc, 20, 0.5, 0.5, 0.5, 0);
        // The ground ring is what makes a summoned zombie read as rising out of the earth rather
        // than simply appearing. Its colours come from the element palette via SpellVfx.ring, and
        // the cluster inside it is the palette's own core shade - the same two-part burst
        // SkeletalKnights draws, so the two summon spells read as one family.
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.DUST, loc, 20, 0.3, 0.3, 0.3, 0,
                PALETTE.coreDust());
        SpellVfx.ring(CAST_COLOR, caster, loc, SUMMON_RING_RADIUS, SUMMON_RING_POINTS, 0.0,
                SUMMON_RING_SHADOW);
        ParticleCaster.spawn(caster, loc.getWorld(), Utils.LARGE_SMOKE, loc, 10, 0.3, 0.3, 0.3, 0.05);
    }

    private void despawnZombie(LivingEntity caster, Entity e) {
        if (!e.isDead()) {
            ParticleCaster.spawn(caster, e.getWorld(), Utils.LARGE_SMOKE, e.getLocation(), 15, 0.3, 0.3, 0.3, 0.05);
            ParticleCaster.spawn(caster, e.getWorld(), Utils.WITCH, e.getLocation(), 10, 0.3, 0.3, 0.3, 0);
            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ZOMBIE_DEATH, 0.6f, 0.8f);
            e.remove();
        }
    }

    @EventHandler
    private void onZombieDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Zombie zombie)) return;
        SummonCombatGuard.SummonTag tag = SummonCombatGuard.readSummonTag(zombie, "summoned_zombie");
        if (tag == null || !tag.flagged() || tag.summonerUuid() == null) return;

        if (e.getEntity() instanceof Player p) {
            UUID summonerUUID = UUID.fromString(tag.summonerUuid());
            if (p.getUniqueId().equals(summonerUUID)) {
                e.setCancelled(true);
            }
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
            if (lt instanceof Zombie zombie) {
                SummonCombatGuard.SummonTag tag = SummonCombatGuard.readSummonTag(zombie, "summoned_zombie");
                if (tag != null && tag.summonerUuid() != null && tag.summonerUuid().equals(p.getUniqueId().toString())) return;
            }
            player = p;
            target = lt;
        }

        if (player == null || target == null) return;
        if (SummonCombatGuard.isSummon(target)) return;

        List<UUID> zombieIds = summonedZombies.get(player.getUniqueId());
        if (zombieIds == null || zombieIds.isEmpty()) return;

        for (UUID id : zombieIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof Zombie zombie && !zombie.isDead()) {
                zombie.setTarget(target);
            }
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {}

    @Override
    public void onCountered(Location location) {}

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.summonzombies.book_name"))
                .addCustomLoreLine(lang().get("spells.summonzombies.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 4))
                .build();
    }
}
