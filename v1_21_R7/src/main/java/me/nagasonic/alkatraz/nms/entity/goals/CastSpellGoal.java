package me.nagasonic.alkatraz.nms.entity.goals;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.items.wands.Wand;
import me.nagasonic.alkatraz.nms.entity.MagicEntity;
import me.nagasonic.alkatraz.spells.Spell;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_21_R7.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_21_R7.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_21_R7.entity.CraftMob;
import org.bukkit.craftbukkit.v1_21_R7.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class CastSpellGoal extends Goal {

    private final Mob         mob;
    private final MagicEntity magic;
    private final double      castRange;
    private final int         globalCooldownTicks;

    private int   cooldownTimer = 0;
    private int   windUpTimer   = 0;
    private Spell selectedSpell = null;

    private static final int WIND_UP_TICKS = 15;

    public CastSpellGoal(Mob mob, MagicEntity magic, double castRange, int globalCooldownTicks) {
        this.mob                 = mob;
        this.magic               = magic;
        this.castRange           = castRange;
        this.globalCooldownTicks = globalCooldownTicks;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (mob.distanceTo(target) > castRange) return false;
        if (!mob.hasLineOfSight(target)) return false;
        selectedSpell = selectSpell(magic.getSpells());
        return selectedSpell != null;
    }

    private Spell selectSpell(List<Spell> spells) {
        List<Spell> s = new ArrayList<>(spells);
        if (s.isEmpty()) return null;
        Spell spell = s.get((int) (Math.random() * s.size()));
        if (spell.canMobCast((org.bukkit.entity.Mob) mob.getBukkitEntity())){
            return spell;
        }else{
            s.remove(spell);
            return selectSpell(s);
        }
    }

    /**
     * Keeps the goal alive while cooling down OR winding up.
     * The goal only exits when the cooldown expires AND the target is lost/dead.
     * This is what allows the cycle to repeat — the goal never releases control
     * mid-cooldown, so it doesn't need to re-run canUse() to cast again.
     */
    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (mob.distanceTo(target) > castRange * 1.2) return false;
        // Stay active while winding up or waiting out the cooldown
        return cooldownTimer > 0 || windUpTimer <= WIND_UP_TICKS;
    }

    @Override
    public void start() {
        windUpTimer   = 0;
        cooldownTimer = 0;
    }

    @Override
    public void stop() {
        selectedSpell = null;
        windUpTimer   = 0;
        cooldownTimer = 0;
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();

        // Face target throughout
        mob.getLookControl().setLookAt(target, 30.0f, mob.getMaxHeadXRot());
        if (cooldownTimer > 0) {
            cooldownTimer--;
            if (cooldownTimer == 0) {
                windUpTimer   = 0;
            }
            return;
        }

        windUpTimer++;
        if (windUpTimer == WIND_UP_TICKS) {
            selectedSpell = selectSpell(magic.getSpells());
            if (selectedSpell == null) return;
            fireCast();
            cooldownTimer = globalCooldownTicks;
        }
    }

    // -------------------------------------------------------------------------
    // Cast
    // -------------------------------------------------------------------------

    private void fireCast() {
        if (!(mob.getBukkitEntity() instanceof org.bukkit.entity.Mob bukkitMob)) return;
        ItemStack item = CraftItemStack.asBukkitCopy(mob.getItemInHand(InteractionHand.MAIN_HAND));
        org.bukkit.inventory.ItemStack wand;
        if (item.getType().equals(Material.AIR)){
            wand = null;
        }else{
            if (Wand.isWand(item)) wand = item;
            else {
                wand = null;
            }
        }
        SpellPrepareEvent event = new SpellPrepareEvent(bukkitMob, selectedSpell, wand);
        int circleTaskId = selectedSpell.circleAction(bukkitMob, event);
        float baseCastTime = selectedSpell.getFullCastTime(wand, selectedSpell.getCastTime());
        long finalCastTime = (long) (baseCastTime * 20);

        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {

                Bukkit.getServer().getScheduler().cancelTask(circleTaskId);
                selectedSpell.mobCastAction(bukkitMob, wand);
                selectedSpell = null;
        }, finalCastTime);
    }
}