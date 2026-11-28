package me.nagasonic.alkatraz.spells.domains;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Random;

/** EARTH: rough ground, pillars and periodic seismic slams. */
public final class EarthDomainStyle implements DomainStyle {

    @Override
    public Material transform(Domain domain, int ring, Random random) {
        List<Material> palette = domain.palette();
        return palette.get(random.nextInt(palette.size()));
    }

    @Override
    public void onClaim(Domain domain, DomainTile tile, DomainManager manager) {
        if (domain.distanceTo(tile.coord()) % 4 != 0) return;
        org.bukkit.block.Block above = domain.world().getBlockAt(
                tile.coord().x(), tile.coord().y() + 1, tile.coord().z());
        if (above.getType() == Material.AIR && domain.random().nextDouble() < 0.35) {
            domain.claimExtra(above, Material.STONE, manager);
        }
    }

    @Override
    public void tick(Domain domain, DomainManager manager, int tick) {
        if (tick % 30 == 0) {
            SpellVfx.field(DomainEffects.color(Element.EARTH), domain.caster(), domain.center(),
                    domain.radius(), 1.8, 18, 8, tick * 0.05);
            manager.seismicSlam(domain);
        }
    }

    @Override
    public void affect(Domain domain, LivingEntity target, double factor) {
        DomainEffects.add(target, PotionEffectType.SLOW, (int) (50 * factor), 0);
    }
}
