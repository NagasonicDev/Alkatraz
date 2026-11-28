package me.nagasonic.alkatraz.spells.domains;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.hooks.Protection;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.util.SpellDamageUtil;
import me.nagasonic.alkatraz.spells.util.SpellTargeting;
import me.nagasonic.alkatraz.util.MaterialCompat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Single main-thread owner of all active domains. */
public final class DomainManager implements Listener {

    public static final String CONFIG = "spells/domains.yml";

    private static final DomainManager INSTANCE = new DomainManager();

    private final List<Domain> domains = new ArrayList<>();
    private final Map<UUID, Domain> byCaster = new HashMap<>();
    private final Map<UUID, List<Fireball>> fireballs = new HashMap<>();
    private final Map<Element, DomainStyle> styles = new EnumMap<>(Element.class);
    private final Map<Element, List<Material>> palettes = new EnumMap<>(Element.class);
    private final Map<Element, Double> damage = new EnumMap<>(Element.class);
    private final Map<Element, Double> playerCap = new EnumMap<>(Element.class);

    private JavaPlugin plugin;
    private BukkitTask task;
    private DomainBlockGuard blockGuard;
    private boolean registered;

    private int expandTicksPerRing = 2;
    private int holdTicks = 100;
    private int recedeTicksPerRing = 2;
    private int damageInterval = 20;
    private int borderBand = 3;
    private int maxTiles = 6400;
    private int fireBudget = 512;
    private double borderFactor = 0.4;
    private double tieEpsilon = 0.35;
    private double fireSpreadChance = 0.04;

    private DomainManager() {
        styles.put(Element.FIRE, new FireDomainStyle());
        styles.put(Element.WATER, new WaterDomainStyle());
        styles.put(Element.EARTH, new EarthDomainStyle());
        styles.put(Element.AIR, new AirDomainStyle());
        styles.put(Element.DARK, new DarkDomainStyle());
        styles.put(Element.LIGHT, new LightDomainStyle());
    }

    public static DomainManager getInstance() {
        return INSTANCE;
    }

    public void init(JavaPlugin plugin) {
        this.plugin = plugin;
        ConfigManager.getConfig(CONFIG).saveDefaultConfig();
        ConfigManager.reloadConfig(CONFIG);
        reloadConfig();
        if (!registered) {
            PluginManager pm = Bukkit.getPluginManager();
            pm.registerEvents(this, plugin);
            blockGuard = new DomainBlockGuard(this);
            pm.registerEvents(blockGuard, plugin);
            registered = true;
        }
        startTask();
    }

    public void shutdown() {
        forceEndAll();
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (registered) {
            org.bukkit.event.HandlerList.unregisterAll(this);
            if (blockGuard != null) org.bukkit.event.HandlerList.unregisterAll(blockGuard);
            registered = false;
        }
    }

    /** Called by reload before configs are re-read: instant restore, no stale tiles. */
    public void reload() {
        forceEndAll();
        ConfigManager.reloadConfig(CONFIG);
        reloadConfig();
    }

    public void reloadConfig() {
        YamlConfiguration config = ConfigManager.getConfig(CONFIG).get();
        expandTicksPerRing = config.getInt("global.expand_ticks_per_ring", expandTicksPerRing);
        holdTicks = config.getInt("global.hold_ticks", holdTicks);
        recedeTicksPerRing = config.getInt("global.recede_ticks_per_ring", recedeTicksPerRing);
        damageInterval = config.getInt("global.damage_interval", damageInterval);
        borderBand = config.getInt("global.border_band", borderBand);
        maxTiles = config.getInt("global.max_tiles", maxTiles);
        fireBudget = config.getInt("global.fire_budget", fireBudget);
        borderFactor = config.getDouble("global.border_factor", borderFactor);
        tieEpsilon = config.getDouble("global.tie_epsilon", tieEpsilon);
        fireSpreadChance = config.getDouble("global.fire_spread_chance", fireSpreadChance);
        for (Element element : Element.values()) {
            if (element == Element.NONE) continue;
            String path = "elements." + element.name().toLowerCase();
            List<String> names = config.getStringList(path + ".palette");
            List<Material> materials = new ArrayList<>();
            for (String name : names) {
                Material material = MaterialCompat.resolve(name);
                if (material != null) materials.add(material);
            }
            if (materials.isEmpty()) materials = defaultPalette(element);
            palettes.put(element, materials);
            damage.put(element, config.getDouble(path + ".damage", 2.0));
            playerCap.put(element, config.getDouble(path + ".pvp_damage_cap", 6.0));
        }
    }

    public int expandTicksPerRing() {
        return Math.max(1, expandTicksPerRing);
    }

    public int holdTicks() {
        return Math.max(0, holdTicks);
    }

    public int recedeTicksPerRing() {
        return Math.max(1, recedeTicksPerRing);
    }

    public int activeCount() {
        return domains.size();
    }

    public List<Domain> domainsView() {
        return domains;
    }

    public Domain activeDomain(UUID casterId) {
        return byCaster.get(casterId);
    }

    /** Starts (or replaces) the caster's domain. Must be called on the main thread. */
    public Domain begin(Spell spell, ItemStack wand, LivingEntity caster, int radius) {
        if (spell == null || caster == null) return null;
        forceEnd(caster.getUniqueId());
        int actualRadius = Math.max(1, Math.min(radius, configRadius()));
        DomainStyle style = styles.get(spell.getElement());
        List<Material> palette = palettes.get(spell.getElement());
        if (style == null || palette == null || palette.isEmpty()) return null;
        Domain domain = new Domain(spell, wand, caster.getUniqueId(), caster.getWorld(),
                caster.getLocation(), actualRadius, style, palette);
        domains.add(domain);
        byCaster.put(caster.getUniqueId(), domain);
        fireballs.put(caster.getUniqueId(), new ArrayList<>());
        return domain;
    }

    private int configRadius() {
        if (plugin == null) return maxTiles;
        return ConfigManager.getConfig(CONFIG).get().getInt("global.max_radius", 32);
    }

    /** Forces any domain owned by this entity to end immediately. */
    public void forceEnd(UUID casterId) {
        Domain domain = byCaster.remove(casterId);
        if (domain == null) return;
        removeFireballs(casterId);
        domains.remove(domain);
        domain.forceEnd();
    }

    public void forceEndAll() {
        for (UUID id : new ArrayList<>(byCaster.keySet())) forceEnd(id);
        domains.clear();
        byCaster.clear();
    }

    /** Ends every domain created by one spell, used by spell cleanup on reload. */
    public void forceEndSpell(String spellId) {
        for (UUID id : new ArrayList<>(byCaster.keySet())) {
            Domain domain = byCaster.get(id);
            if (domain != null && domain.spell() != null && spellId.equals(domain.spell().getId())) forceEnd(id);
        }
    }

    private void startTask() {
        if (task == null && plugin != null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    private void tick() {
        for (Domain domain : new ArrayList<>(domains)) {
            if (domain.phase() == DomainPhase.END || domain.world() == null) {
                forceEnd(domain.casterId());
                continue;
            }
            domain.step(this);
            if (damageInterval > 0 && domain.tickCount() % damageInterval == 0) {
                applyPressure(domain);
            }
            tickFireballs(domain.casterId());
            if (domain.phase() == DomainPhase.END) forceEnd(domain.casterId());
        }
    }

    private void applyPressure(Domain domain) {
        LivingEntity caster = domain.caster();
        World world = domain.world();
        if (caster == null || world == null) return;
        Location center = domain.center();
        double radius = domain.radius() + 1.0;
        double base = damage.getOrDefault(domain.element(), 2.0);
        double cap = playerCap.getOrDefault(domain.element(), 6.0);
        for (Entity entity : world.getNearbyEntities(center, radius, 8.0, radius)) {
            if (!(entity instanceof LivingEntity target) || target == caster) continue;
            if (SpellTargeting.isAlly(caster, target)) {
                domain.style().support(domain, target, 1.0);
                continue;
            }
            double factor = pressureFactor(domain, target.getLocation());
            if (factor <= 0.0) continue;
            double amount = base * factor;
            if (target instanceof Player) amount = Math.min(amount, cap);
            if (Protection.damage(target, caster, target.getLocation())) {
                SpellDamageUtil.damageWithSpell(target, amount, caster, domain.wand(), domain.spell());
            }
            domain.style().affect(domain, target, factor);
        }
    }

    private double pressureFactor(Domain domain, Location location) {
        double factor = 1.0;
        for (Domain other : domains) {
            if (other == domain || other.phase() == DomainPhase.END) continue;
            if (!other.worldName().equals(domain.worldName())) continue;
            if (!DomainRules.isOpposing(domain.element(), other.element())) continue;
            double distanceToBorder = Math.abs(other.horizontalDistance(location) - other.radius());
            factor = Math.min(factor, DomainRules.dampen(domain.element(), other.element(),
                    distanceToBorder, borderBand, borderFactor));
        }
        return factor;
    }

    /** True when a tile can be claimed and transformed by this domain. */
    public boolean canClaim(Domain domain, DomainGeometry.Tile tile) {
        if (domain.tiles().size() >= maxTiles) return false;
        for (Domain other : domains) {
            if (other == domain || other.phase() == DomainPhase.END) continue;
            if (!other.worldName().equals(domain.worldName())) continue;
            if (other.claimed().contains(tile)) return false;
            DomainGeometry.Claim claim = DomainGeometry.claim(
                    domain.distanceTo(tile), other.distanceTo(tile),
                    domain.radius(), other.radius(), tieEpsilon);
            if (claim == DomainGeometry.Claim.BORDER) {
                domain.addBorder(tile);
                other.addBorder(tile);
                return false;
            }
            if (claim == DomainGeometry.Claim.OTHER) return false;
        }
        return true;
    }

    /** Snapshots, transforms and registers one tile. Adds it to the domain on success. */
    public DomainTile snapshot(Domain domain, DomainGeometry.Tile coord, Material material) {
        World world = domain.world();
        if (world == null || material == null) return null;
        Block block = world.getBlockAt(coord.x(), coord.y(), coord.z());
        LivingEntity caster = domain.caster();
        if (caster != null && !Protection.blockEdit(caster, block.getLocation())) return null;
        BlockState original = block.getState();
        block.setType(material, false);
        DomainTile tile = new DomainTile(coord, original, material, domain.casterId());
        domain.addTile(tile);
        return tile;
    }

    public void expand(Domain domain, int ring) {
        World world = domain.world();
        if (world == null || ring > domain.radius()) return;
        for (DomainGeometry.Tile coord : DomainGeometry.ring(
                domain.originTile().x(), domain.originTile().y(), domain.originTile().z(), ring)) {
            if (!canClaim(domain, coord)) continue;
            Material material = domain.style().transform(domain, ring, domain.random());
            DomainTile tile = snapshot(domain, coord, material);
            if (tile != null) domain.style().onClaim(domain, tile, this);
        }
    }

    /** Rain fireballs across the whole domain; damage is also applied with no mobs present. */
    public void rainFireballs(Domain domain) {
        World world = domain.world();
        if (world == null) return;
        LivingEntity caster = domain.caster();
        if (caster != null && !Protection.spawnMob(caster, domain.center())) return;
        for (int i = 0; i < 3; i++) {
            int dx = between(domain.random(), -domain.radius(), domain.radius());
            int dz = between(domain.random(), -domain.radius(), domain.radius());
            DomainGeometry.Tile ground = new DomainGeometry.Tile(
                    domain.originTile().x() + dx, domain.originTile().y(), domain.originTile().z() + dz);
            if (!domain.owns(ground)) continue;
            Location target = domain.locationOf(ground).add(0.5, 1.0, 0.5);
            Location start = target.clone().add(0.0, 16.0, 0.0);
            Vector direction = target.clone().subtract(start).toVector().normalize();
            Fireball fireball = world.spawn(start, Fireball.class, fb -> {
                fb.setDirection(direction);
                fb.setVelocity(direction.clone().multiply(1.4));
                fb.setIsIncendiary(true);
            });
            fireballs.computeIfAbsent(domain.casterId(), id -> new ArrayList<>()).add(fireball);
        }
    }

    private void tickFireballs(UUID casterId) {
        List<Fireball> list = fireballs.get(casterId);
        if (list == null) return;
        list.removeIf(fireball -> fireball.isDead());
    }

    private void removeFireballs(UUID casterId) {
        List<Fireball> list = fireballs.remove(casterId);
        if (list == null) return;
        for (Fireball fireball : list) if (!fireball.isDead()) fireball.remove();
    }

    /** Spreads real fire only onto tiles already owned by the fire domain. */
    public void spreadFire(Domain domain) {
        World world = domain.world();
        if (world == null || domain.fire().size() >= fireBudget) return;
        LivingEntity caster = domain.caster();
        for (DomainGeometry.Tile tile : new ArrayList<>(domain.fire())) {
            if (domain.fire().size() >= fireBudget) return;
            if (domain.random().nextDouble() > fireSpreadChance) continue;
            int dx = between(domain.random(), -1, 1);
            int dz = between(domain.random(), -1, 1);
            DomainGeometry.Tile ground = new DomainGeometry.Tile(tile.x() + dx, tile.y() - 1, tile.z() + dz);
            if (!domain.owns(ground)) continue;
            DomainGeometry.Tile above = new DomainGeometry.Tile(ground.x(), ground.y() + 1, ground.z());
            if (domain.claimed().contains(above) || !canClaim(domain, above)) continue;
            if (caster != null && !Protection.ignite(caster, domain.locationOf(above))) continue;
            DomainTile extra = snapshot(domain, above, Material.FIRE);
            if (extra != null) domain.addFire(above);
        }
    }

    /** Seismic knock-up used by the earth domain; no terrain destruction. */
    public void seismicSlam(Domain domain) {
        LivingEntity caster = domain.caster();
        World world = domain.world();
        if (caster == null || world == null) return;
        int dx = between(domain.random(), -domain.radius(), domain.radius());
        int dz = between(domain.random(), -domain.radius(), domain.radius());
        DomainGeometry.Tile ground = new DomainGeometry.Tile(
                domain.originTile().x() + dx, domain.originTile().y(), domain.originTile().z() + dz);
        if (!domain.owns(ground)) return;
        Location center = domain.locationOf(ground).add(0.5, 0.0, 0.5);
        for (Entity entity : world.getNearbyEntities(center, 4.0, 4.0, 4.0)) {
            if (!(entity instanceof LivingEntity target) || target == caster) continue;
            if (SpellTargeting.isAlly(caster, target)) continue;
            target.setVelocity(target.getVelocity().add(new Vector(0.0, 0.7, 0.0)));
            domain.style().affect(domain, target, 1.0);
        }
    }

    /** Returns the domain actively owning a block, for the block guard. */
    public Domain ownerOf(Block block) {
        if (block == null) return null;
        DomainGeometry.Tile tile = new DomainGeometry.Tile(block.getX(), block.getY(), block.getZ());
        for (Domain domain : domains) {
            if (domain.worldName().equals(block.getWorld().getName()) && domain.owns(tile)) return domain;
        }
        return null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forceEnd(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        forceEnd(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        forceEnd(event.getPlayer().getUniqueId());
    }

    private static int between(Random random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private static List<Material> defaultPalette(Element element) {
        return switch (element) {
            case FIRE -> materials("NETHERRACK", "SOUL_SAND", "MAGMA_BLOCK", "BLACKSTONE");
            case WATER -> materials("PRISMARINE", "SEA_LANTERN", "SAND", "GRAVEL");
            case EARTH -> materials("STONE", "DIRT", "GRASS_BLOCK", "COBBLESTONE", "MOSS_BLOCK");
            case AIR -> materials("WHITE_WOOL", "QUARTZ_BLOCK", "GLASS");
            case DARK -> materials("SCULK", "DEEPSLATE", "SOUL_SOIL", "OBSIDIAN");
            case LIGHT -> materials("QUARTZ_BLOCK", "GLOWSTONE", "SEA_LANTERN", "WHITE_CONCRETE");
            default -> materials("STONE");
        };
    }

    private static List<Material> materials(String... names) {
        List<Material> result = new ArrayList<>();
        for (String name : names) {
            Material material = MaterialCompat.resolve(name);
            if (material != null) result.add(material);
        }
        return result;
    }
}
