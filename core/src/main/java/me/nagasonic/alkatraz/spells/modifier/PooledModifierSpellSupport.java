package me.nagasonic.alkatraz.spells.modifier;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared cast / expiry logic for Buff and Debuff spells.
 */
public final class PooledModifierSpellSupport {

    private PooledModifierSpellSupport() {}

    public static LivingEntity resolveBuffTarget(Player caster, int range) {
        if (caster.isSneaking()) {
            var entity = caster.getTargetEntity(range);
            if (entity instanceof Player target
                    && target.isValid()
                    && !target.isDead()
                    && !target.getUniqueId().equals(caster.getUniqueId())) {
                return target;
            }
            return null;
        }
        return caster;
    }

    public static LivingEntity resolveDebuffTarget(Player caster, int range) {
        var entity = caster.getTargetEntity(range);
        if (entity instanceof LivingEntity living
                && living.isValid()
                && !living.isDead()
                && !living.getUniqueId().equals(caster.getUniqueId())) {
            return living;
        }
        return null;
    }

    public static LivingEntity resolveMobDebuffTarget(Mob caster, double range) {
        LivingEntity living = caster.getTarget();
        if (living.isValid() && !living.isDead()) {
            return living;
        }
        return caster.getNearbyEntities(range, range, range).stream()
                .filter(e -> e instanceof LivingEntity entity
                        && !entity.getUniqueId().equals(caster.getUniqueId())
                        && entity.isValid()
                        && !entity.isDead())
                .map(e -> (LivingEntity) e)
                .findFirst()
                .orElse(null);
    }

    public static boolean applyConfiguredModifiers(Player caster,
                                                   LivingEntity target,
                                                   Spell spell,
                                                   String groupId,
                                                   int durationSeconds,
                                                   Map<UUID, List<AppliedModifier>> activeByTarget,
                                                   Color particleColor,
                                                   String casterMessage,
                                                   String targetMessage,
                                                   String expiredMessage) {
        List<AppliedModifier> modifiers =
                CastModifierCollector.fromGroup(spell, caster, groupId);

        if (modifiers.isEmpty()) {
            Utils.sendActionBar(caster, "&cNo effects configured! Open the spell options menu.");
            return false;
        }

        clearActiveModifiers(target, spell, activeByTarget);

        int durationTicks = durationSeconds * 20;
        for (AppliedModifier modifier : modifiers) {
            if (modifier instanceof PotionAppliedModifier potion) {
                potion.setDurationTicks(durationTicks);
            }
            modifier.apply(target, spell);
        }

        activeByTarget.put(target.getUniqueId(), new ArrayList<>(modifiers));

        new ModifierExpiry(target, spell, activeByTarget, expiredMessage)
                .runTaskLater(Alkatraz.getInstance(), durationTicks);

        spawnCastParticles(target, particleColor);
        float pitch = "debuff".equals(spell.getId()) ? 0.7f : 1.2f;
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, pitch);

        Utils.sendActionBar(caster, ColorFormat.format(casterMessage));
        if (target instanceof Player targetPlayer
                && !targetPlayer.getUniqueId().equals(caster.getUniqueId())) {
            Utils.sendActionBar(targetPlayer, ColorFormat.format(targetMessage));
        }

        return true;
    }

    public static void clearActiveModifiers(LivingEntity target,
                                            Spell spell,
                                            Map<UUID, List<AppliedModifier>> activeByTarget) {
        List<AppliedModifier> previous = activeByTarget.remove(target.getUniqueId());
        if (previous == null) return;
        for (AppliedModifier modifier : previous) {
            modifier.remove(target, spell);
        }
    }

    public static void spawnCastParticles(LivingEntity entity, Color color) {
        var loc = entity.getLocation().add(0, 1, 0);

        new BukkitRunnable() {
            double angle = 0;
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= 20 || !entity.isValid()) {
                    cancel();
                    return;
                }
                for (int i = 0; i < 3; i++) {
                    double a = angle + (Math.PI * 2 / 3) * i;
                    double x = Math.cos(a) * 0.8;
                    double z = Math.sin(a) * 0.8;
                    double y = ticks * 0.1;
                    var pLoc = loc.clone().add(x, y, z);
                    pLoc.getWorld().spawnParticle(Utils.DUST, pLoc, 0,
                            new Particle.DustOptions(color, 0.6F));
                    pLoc.getWorld().spawnParticle(Particle.END_ROD, pLoc, 1, 0, 0, 0, 0.02);
                }
                angle += 0.4;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private static class ModifierExpiry extends BukkitRunnable {
        private final LivingEntity target;
        private final Spell spell;
        private final Map<UUID, List<AppliedModifier>> activeByTarget;
        private final String expiredMessage;

        ModifierExpiry(LivingEntity target,
                       Spell spell,
                       Map<UUID, List<AppliedModifier>> activeByTarget,
                       String expiredMessage) {
            this.target = target;
            this.spell = spell;
            this.activeByTarget = activeByTarget;
            this.expiredMessage = expiredMessage;
        }

        @Override
        public void run() {
            if (!target.isValid()) {
                activeByTarget.remove(target.getUniqueId());
                return;
            }

            List<AppliedModifier> active = activeByTarget.remove(target.getUniqueId());
            if (active != null) {
                for (AppliedModifier modifier : active) {
                    modifier.remove(target, spell);
                }
            }

            if (target instanceof Player player && player.isOnline()) {
                Utils.sendActionBar(player, ColorFormat.format(expiredMessage));
            }
        }
    }
}
