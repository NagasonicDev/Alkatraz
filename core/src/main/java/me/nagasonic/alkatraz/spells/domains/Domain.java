package me.nagasonic.alkatraz.spells.domains;

import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.api.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** A single active radial domain owned by one caster in one world. */
public final class Domain {

    private final UUID id = UUID.randomUUID();
    private final Spell spell;
    private final ItemStack wand;
    private final UUID casterId;
    private final String worldName;
    private final DomainGeometry.Tile origin;
    private final int radius;
    private final DomainStyle style;
    private final List<Material> palette;
    private final List<DomainTile> tiles = new ArrayList<>();
    private final Set<DomainGeometry.Tile> claimed = new HashSet<>();
    private final Set<DomainGeometry.Tile> border = new HashSet<>();
    private final Set<DomainGeometry.Tile> fire = new HashSet<>();
    private final Random random = new Random();

    private DomainPhase phase = DomainPhase.EXPANDING;
    private int ring = 1;
    private int phaseTick;
    private int tickCount;
    private int recedeIndex;

    public Domain(Spell spell, ItemStack wand, UUID casterId, World world, Location origin,
                  int radius, DomainStyle style, List<Material> palette) {
        this.spell = spell;
        this.wand = wand;
        this.casterId = casterId;
        this.worldName = world.getName();
        this.origin = new DomainGeometry.Tile(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
        this.radius = radius;
        this.style = style;
        this.palette = palette;
    }

    public UUID id() {
        return id;
    }

    public Spell spell() {
        return spell;
    }

    public ItemStack wand() {
        return wand;
    }

    public Element element() {
        return spell.getElement();
    }

    public UUID casterId() {
        return casterId;
    }

    public String worldName() {
        return worldName;
    }

    public World world() {
        return org.bukkit.Bukkit.getWorld(worldName);
    }

    public LivingEntity caster() {
        return org.bukkit.Bukkit.getEntity(casterId) instanceof LivingEntity living ? living : null;
    }

    public Location center() {
        return new Location(world(), origin.x() + 0.5, origin.y(), origin.z() + 0.5);
    }

    public DomainGeometry.Tile originTile() {
        return origin;
    }

    public int radius() {
        return radius;
    }

    public DomainStyle style() {
        return style;
    }

    public List<Material> palette() {
        return palette;
    }

    public Random random() {
        return random;
    }

    public DomainPhase phase() {
        return phase;
    }

    public int tickCount() {
        return tickCount;
    }

    public List<DomainTile> tiles() {
        return tiles;
    }

    public Set<DomainGeometry.Tile> claimed() {
        return claimed;
    }

    public Set<DomainGeometry.Tile> fire() {
        return fire;
    }

    public boolean owns(DomainGeometry.Tile tile) {
        return claimed.contains(tile) && !border.contains(tile);
    }

    /** Marks a contested tile as neutral so neither domain transforms or damages it. */
    public void addBorder(DomainGeometry.Tile tile) {
        claimed.add(tile);
        border.add(tile);
    }

    /** Chebyshev distance matches the square wavefront used by {@link DomainGeometry#ring}. */
    public int distanceTo(DomainGeometry.Tile tile) {
        return Math.max(Math.abs(tile.x() - origin.x()), Math.abs(tile.z() - origin.z()));
    }

    public double horizontalDistance(Location location) {
        double dx = location.getX() - origin.x() - 0.5;
        double dz = location.getZ() - origin.z() - 0.5;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public Location locationOf(DomainGeometry.Tile tile) {
        return new Location(world(), tile.x(), tile.y(), tile.z());
    }

    public void addTile(DomainTile tile) {
        tiles.add(tile);
        claimed.add(tile.coord());
    }

    public void addFire(DomainGeometry.Tile tile) {
        fire.add(tile);
    }

    public void removeFire(DomainGeometry.Tile tile) {
        fire.remove(tile);
    }

    /** Removes and restores the most recently claimed tile, for reverse recede. */
    public boolean restoreLast() {
        if (tiles.isEmpty()) return false;
        DomainTile tile = tiles.remove(tiles.size() - 1);
        claimed.remove(tile.coord());
        fire.remove(tile.coord());
        tile.restore();
        return true;
    }

    /** Immediate, complete teardown used by reload, disable, death, quit and world change. */
    public void forceEnd() {
        for (int i = tiles.size() - 1; i >= 0; i--) {
            DomainTile tile = tiles.get(i);
            fire.remove(tile.coord());
            tile.restore();
        }
        tiles.clear();
        claimed.clear();
        fire.clear();
        phase = DomainPhase.END;
    }

    /** Advances one main-thread tick; timing comes from the domain config. */
    public void step(DomainManager manager) {
        tickCount++;
        phaseTick++;
        switch (phase) {
            case EXPANDING -> {
                int period = Math.max(1, manager.expandTicksPerRing());
                if (phaseTick % period == 0) {
                    if (ring > radius) {
                        phase = DomainPhase.HOLDING;
                        phaseTick = 0;
                    } else {
                        manager.expand(this, ring++);
                    }
                }
            }
            case HOLDING -> {
                if (phaseTick >= Math.max(0, manager.holdTicks())) {
                    phase = DomainPhase.RECEDING;
                    phaseTick = 0;
                    recedeIndex = tiles.size() - 1;
                }
            }
            case RECEDING -> {
                int period = Math.max(1, manager.recedeTicksPerRing());
                if (phaseTick % period == 0 && restoreLast()) {
                    recedeIndex--;
                }
                if (tiles.isEmpty()) phase = DomainPhase.END;
            }
            case END -> {
                // Removed by the manager on the same tick.
            }
        }
        style.tick(this, manager, tickCount);
    }

    public int recedeIndex() {
        return recedeIndex;
    }

    /** Convenience used by styles that add temporary blocks above a tile. */
    public boolean claimExtra(Block block, Material material, DomainManager manager) {
        if (block == null || manager == null) return false;
        DomainGeometry.Tile tile = new DomainGeometry.Tile(
                block.getX(), block.getY(), block.getZ());
        if (claimed.contains(tile) || !manager.canClaim(this, tile)) return false;
        DomainTile domainTile = manager.snapshot(this, tile, material);
        if (domainTile != null) addTile(domainTile);
        return domainTile != null;
    }
}
