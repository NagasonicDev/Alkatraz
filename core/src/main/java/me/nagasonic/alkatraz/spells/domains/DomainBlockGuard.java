package me.nagasonic.alkatraz.spells.domains;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Keeps transformed domain blocks intact while domains are active. */
public final class DomainBlockGuard implements Listener {

    private final DomainManager manager;

    DomainBlockGuard(DomainManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (manager.ownerOf(event.getBlock()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (manager.ownerOf(event.getBlock()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isOwned);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isOwned);
    }

    @EventHandler
    public void onBurn(BlockBurnEvent event) {
        if (isOwned(event.getBlock())) return;
        if (hasActiveFireDomain(event.getBlock())) event.setCancelled(true);
    }

    private boolean isOwned(Block block) {
        return manager.ownerOf(block) != null;
    }

    private boolean hasActiveFireDomain(Block block) {
        for (Domain domain : manager.domainsView()) {
            if (domain.worldName().equals(block.getWorld().getName())
                    && domain.element() == me.nagasonic.alkatraz.api.Element.FIRE
                    && domain.phase() != DomainPhase.END) return true;
        }
        return false;
    }
}
