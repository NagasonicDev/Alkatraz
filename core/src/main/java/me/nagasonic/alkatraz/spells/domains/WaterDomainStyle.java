package me.nagasonic.alkatraz.spells.domains;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Random;

/** WATER: flood/surge that slows, pulls and extinguishes hostiles. */
public final class WaterDomainStyle implements DomainStyle {

    @Override
    public Material transform(Domain domain, int ring, Random random) {
        List<Material> palette = domain.palette();
        return palette.get(random.nextInt(palette.size()));
    }

    @Override
    public void tick(Domain domain, DomainManager manager, int tick) {
        if (tick % 30 != 0) return;
        SpellVfx.field(DomainEffects.color(Element.WATER), domain.caster(), domain.center(),
                domain.radius(), 1.6, 18, 10, tick * 0.08);
    }

    @Override
    public void affect(Domain domain, LivingEntity target, double factor) {
        target.setFireTicks(0);
        DomainEffects.add(target, PotionEffectType.SLOW, (int) (60 * factor), 1);
        DomainEffects.pull(target, domain.center(), 0.24 * factor);
    }

    @Override
    public void support(Domain domain, LivingEntity ally, double factor) {
        ally.setFireTicks(0);
    }
}
