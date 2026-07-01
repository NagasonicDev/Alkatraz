package me.nagasonic.alkatraz.nms.entity.goals;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.items.wands.Wand;
import me.nagasonic.alkatraz.mobs.MagicEntity;
import me.nagasonic.alkatraz.spells.Spell;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class CastSpellGoal extends Goal {

    private final Mob mob;
    private final MagicEntity magic;
    private final double castRange;
    private final int globalCooldownTicks;

    private int cooldownTimer = 0;
    private int windUpTimer = 0;
    private Spell selectedSpell = null;

    private static final int WIND_UP_TICKS = 15;

    public CastSpellGoal(
            Mob mob,
            MagicEntity magic,
            double castRange,
            int globalCooldownTicks
    ) {
        this.mob = mob;
        this.magic = magic;
        this.castRange = castRange;
        this.globalCooldownTicks = globalCooldownTicks;

        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) {
            cooldownTimer--;
            return false;
        }

        LivingEntity target = mob.getTarget();

        if (target == null || !target.isAlive()) return false;
        if (mob.distanceTo(target) > castRange) return false;
        if (!mob.hasLineOfSight(target)) return false;

        selectedSpell = selectSpell(magic.getSpells());

        return selectedSpell != null;
    }

    private Spell selectSpell(List<Spell> spells) {
        List<Spell> available = new ArrayList<>(spells);

        if (available.isEmpty()) {
            return null;
        }

        Spell spell = available.get(
                (int) (Math.random() * available.size())
        );

        if (spell.canMobCast((org.bukkit.entity.Mob) mob.getBukkitEntity())) {
            return spell;
        }

        available.remove(spell);
        return selectSpell(available);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        if (mob.distanceTo(target) > castRange * 1.2D) {
            return false;
        }

        return windUpTimer < WIND_UP_TICKS;
    }

    @Override
    public void start() {
        windUpTimer = 0;
    }

    @Override
    public void stop() {
        selectedSpell = null;
        windUpTimer = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();

        if (target == null) {
            return;
        }

        mob.getLookControl()
                .setLookAt(target, 30.0F, mob.getMaxHeadXRot());

        windUpTimer++;

        if (windUpTimer >= WIND_UP_TICKS) {

            if (selectedSpell == null) {
                selectedSpell = selectSpell(magic.getSpells());
            }

            if (selectedSpell == null) {
                return;
            }

            fireCast();

            cooldownTimer = globalCooldownTicks;
        }
    }

    private void fireCast() {

        if (!(mob.getBukkitEntity() instanceof org.bukkit.entity.Mob bukkitMob)) {
            return;
        }

        final Spell spell = selectedSpell;

        if (spell == null) {
            return;
        }

        ItemStack item = CraftItemStack.asBukkitCopy(
                mob.getItemInHand(InteractionHand.MAIN_HAND)
        );

        ItemStack wand;

        if (item.getType() != Material.AIR && Wand.isWand(item)) {
            wand = item;
        } else {
            wand = null;
        }

        SpellPrepareEvent event =
                new SpellPrepareEvent(bukkitMob, spell, wand);

        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        int circleTaskId =
                spell.circleAction(bukkitMob, event);

        float baseCastTime =
                spell.getFullCastTime(
                        wand,
                        spell.getCastTime()
                );

        long finalCastTime = (long) (baseCastTime * 20);

        Bukkit.getScheduler().runTaskLater(
                Alkatraz.getInstance(),
                () -> {
                    Bukkit.getScheduler().cancelTask(circleTaskId);

                    spell.mobCast(
                            bukkitMob,
                            wand
                    );
                },
                finalCastTime
        );
    }
}
