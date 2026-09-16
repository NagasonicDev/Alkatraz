package me.nagasonic.alkatraz.mobs.goals;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.mobs.Goal;
import me.nagasonic.alkatraz.api.mobs.GoalFlag;
import me.nagasonic.alkatraz.api.mobs.MobBrainContext;
import me.nagasonic.alkatraz.api.mobs.SpellCastConfig;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.mobs.MagicEntities;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.WandUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CastSpellGoal implements Goal {

    private static final int WIND_UP_TICKS = 15;

    private final double castRange;
    private final int globalCooldownTicks;
    private int cooldownTimer = 0;
    private int windUpTimer = 0;
    private Spell selectedSpell = null;

    public CastSpellGoal(SpellCastConfig config) {
        this.castRange = config.castRange();
        this.globalCooldownTicks = config.cooldownTicks();
    }

    @Override
    public EnumSet<GoalFlag> flags() {
        return EnumSet.of(GoalFlag.LOOK);
    }

    @Override
    public boolean canStart(MobBrainContext ctx) {
        if (cooldownTimer > 0) {
            cooldownTimer--;
            return false;
        }
        LivingEntity target = ctx.getTarget();
        if (target == null || target.isDead()) return false;
        if (ctx.distanceSq(target) > castRange * castRange) return false;
        if (!ctx.hasLineOfSight(target)) return false;
        selectedSpell = selectSpell(ctx, MagicEntities.getSpells(ctx.self()));
        return selectedSpell != null;
    }

    @Override
    public boolean shouldContinue(MobBrainContext ctx) {
        LivingEntity target = ctx.getTarget();
        if (target == null || target.isDead()) return false;
        double range = castRange * 1.2;
        if (ctx.distanceSq(target) > range * range) return false;
        return windUpTimer < WIND_UP_TICKS;
    }

    @Override
    public void start(MobBrainContext ctx) {
        windUpTimer = 0;
    }

    @Override
    public void stop(MobBrainContext ctx) {
        selectedSpell = null;
        windUpTimer = 0;
    }

    @Override
    public void tick(MobBrainContext ctx) {
        LivingEntity target = ctx.getTarget();
        if (target == null) return;
        ctx.lookAt(target.getEyeLocation());
        windUpTimer++;
        if (windUpTimer >= WIND_UP_TICKS) {
            if (selectedSpell == null) {
                selectedSpell = selectSpell(ctx, MagicEntities.getSpells(ctx.self()));
            }
            if (selectedSpell == null) return;
            fireCast(ctx);
            cooldownTimer = globalCooldownTicks;
        }
    }

    private Spell selectSpell(MobBrainContext ctx, List<Spell> spells) {
        if (spells == null || spells.isEmpty()) return null;
        List<Spell> copy = new ArrayList<>(spells);
        Collections.shuffle(copy, ThreadLocalRandom.current());
        for (Spell spell : copy) {
            if (spell.canMobCast(ctx.self())) return spell;
        }
        return null;
    }

    private void fireCast(MobBrainContext ctx) {
        final Spell spell = selectedSpell;
        if (spell == null) return;
        ItemStack item = ctx.getMainHandItem();
        ItemStack wand = (item != null && item.getType() != Material.AIR && WandUtils.isWand(item)) ? item : null;
        SpellPrepareEvent event = new SpellPrepareEvent(ctx.self(), spell, wand);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        int circleTaskId = spell.circleAction(ctx.self(), event);
        float baseCastTime = spell.getFullCastTime(wand, spell.getCastTime());
        long finalCastTime = (long) (baseCastTime * 20.0f);
        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            Bukkit.getScheduler().cancelTask(circleTaskId);
            spell.mobCast(ctx.self(), wand);
        }, finalCastTime);
    }
}
