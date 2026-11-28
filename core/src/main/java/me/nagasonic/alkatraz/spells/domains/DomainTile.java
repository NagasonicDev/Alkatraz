package me.nagasonic.alkatraz.spells.domains;

import org.bukkit.block.BlockState;

import java.util.UUID;

/** One transformed column with its original state and owning caster. */
public final class DomainTile {

    private final DomainGeometry.Tile coord;
    private final BlockState original;
    private final org.bukkit.Material transformed;
    private final UUID owner;

    public DomainTile(DomainGeometry.Tile coord, BlockState original,
                      org.bukkit.Material transformed, UUID owner) {
        this.coord = coord;
        this.original = original;
        this.transformed = transformed;
        this.owner = owner;
    }

    public DomainGeometry.Tile coord() {
        return coord;
    }

    public BlockState original() {
        return original;
    }

    public org.bukkit.Material transformed() {
        return transformed;
    }

    public UUID owner() {
        return owner;
    }

    /** Restores the exact original state without block physics or drops. */
    public void restore() {
        original.update(true, false);
    }
}
