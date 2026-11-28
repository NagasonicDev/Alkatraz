package me.nagasonic.alkatraz.spells.domains;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Random;

/** DARK: withering, blinding pressure and a slow drain; no summoning. */
public final class DarkDomainStyle implements DomainStyle {

    @Override
    public Material transform(Domain domain, int ring, Random random) {
        List<Material> palette = domain.palette();
        return palette.get(random.nextInt(palette.size()));
    }

    @Override
    public void tick(Domain domain, DomainManager manager, int tick) {
        if (tick % 30 != 0) return;
        SpellVfx.field(DomainEffects.color(Element.DARK), domain.caster(), domain.center(),
                domain.radius(), 2.4, 18, 10, tick * 0.1);
    }

    @Override
    public void affect(Domain domain, LivingEntity target, double factor) {
        DomainEffects.add(target, PotionEffectType.WITHER, (int) (60 * factor), 0);
        DomainEffects.add(target, PotionEffectType.BLINDNESS, (int) (40 * factor), 0);
        DomainEffects.add(target, PotionEffectType.SLOW, (int) (60 * factor), 0);
    }
}
