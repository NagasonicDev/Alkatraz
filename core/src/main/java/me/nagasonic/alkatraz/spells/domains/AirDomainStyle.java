package me.nagasonic.alkatraz.spells.domains;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Random;

/** AIR: updrafts and deflection without griefing terrain. */
public final class AirDomainStyle implements DomainStyle {

    @Override
    public Material transform(Domain domain, int ring, Random random) {
        List<Material> palette = domain.palette();
        return palette.get(random.nextInt(palette.size()));
    }

    @Override
    public void tick(Domain domain, DomainManager manager, int tick) {
        if (tick % 24 != 0) return;
        SpellVfx.field(DomainEffects.color(Element.AIR), domain.caster(), domain.center(),
                domain.radius(), 4.0, 18, 10, tick * 0.15);
    }

    @Override
    public void affect(Domain domain, LivingEntity target, double factor) {
        DomainEffects.add(target, PotionEffectType.LEVITATION, (int) (30 * factor), 0);
        DomainEffects.push(target, domain.center(), 0.35 * factor);
    }

    @Override
    public void support(Domain domain, LivingEntity ally, double factor) {
        DomainEffects.add(ally, PotionEffectType.SLOW_FALLING, (int) (80 * factor), 0);
        DomainEffects.add(ally, PotionEffectType.SPEED, (int) (60 * factor), 0);
    }
}
