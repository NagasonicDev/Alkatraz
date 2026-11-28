package me.nagasonic.alkatraz.spells.domains;

import me.nagasonic.alkatraz.api.Element;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Random;

/** FIRE: nether ground, raining fireballs and a tracked, clamped firestorm. */
public final class FireDomainStyle implements DomainStyle {

    @Override
    public Material transform(Domain domain, int ring, Random random) {
        List<Material> palette = domain.palette();
        return palette.get(random.nextInt(palette.size()));
    }

    @Override
    public void onClaim(Domain domain, DomainTile tile, DomainManager manager) {
        if (domain.random().nextDouble() > 0.12) return;
        Block above = domain.world().getBlockAt(tile.coord().x(), tile.coord().y() + 1, tile.coord().z());
        if (above.getType() == Material.AIR && domain.claimExtra(above, Material.FIRE, manager)) {
            domain.addFire(new DomainGeometry.Tile(
                    above.getX(), above.getY(), above.getZ()));
        }
    }

    @Override
    public void tick(Domain domain, DomainManager manager, int tick) {
        if (tick % 12 == 0) manager.rainFireballs(domain);
        if (tick % 20 == 0) manager.spreadFire(domain);
        if (tick % 30 == 0) {
            me.nagasonic.alkatraz.spells.vfx.SpellVfx.field(
                    DomainEffects.color(Element.FIRE), domain.caster(), domain.center(),
                    domain.radius(), 3.0, 18, 10, tick * 0.12);
        }
    }

    @Override
    public void affect(Domain domain, LivingEntity target, double factor) {
        target.setFireTicks(Math.max(target.getFireTicks(), (int) (60 * factor)));
    }
}
