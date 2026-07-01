package me.nagasonic.alkatraz.nms.entity;

import me.nagasonic.alkatraz.mobs.MagicEntity;
import me.nagasonic.alkatraz.mobs.MobBrain;
import me.nagasonic.alkatraz.mobs.SpellCastConfig;
import me.nagasonic.alkatraz.nms.entity.goals.CastSpellGoal;
import me.nagasonic.alkatraz.nms.entity.goals.KeepSpellRangeGoal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

public class GoalBuilder {
    private GoalBuilder() {}

    public static void apply(Mob mob, MagicEntity magic, MobBrain brain) {
        mob.goalSelector.removeAllGoals(g -> true);
        mob.targetSelector.removeAllGoals(g -> true);

        int p = 1;

        if (brain.canSwim()) {
            mob.goalSelector.addGoal(p++, new FloatGoal(mob));
        }

        SpellCastConfig sc = brain.spellCast();
        if (sc != null) {
            if (sc.hasRangeKeeping()) {
                mob.goalSelector.addGoal(p++, new KeepSpellRangeGoal(
                        mob,
                        sc.minCastDist(),
                        sc.maxCastDist(),
                        1.1D
                ));
            }
            mob.goalSelector.addGoal(p++, new CastSpellGoal(
                    mob,
                    magic,
                    sc.castRange(),
                    sc.cooldownTicks()
            ));
        }

        if (brain.meleeAttack() && mob instanceof PathfinderMob pm) {
            mob.goalSelector.addGoal(p++, new MeleeAttackGoal(pm, 1.0D, false));
        }

        if (brain.randomStroll()) {
            if (mob instanceof PathfinderMob pmob) {
                mob.goalSelector.addGoal(p++, new WaterAvoidingRandomStrollGoal(pmob, 0.8D));
            }
        }

        mob.goalSelector.addGoal(p++, new LookAtPlayerGoal(mob, Player.class, brain.lookAtPlayerRange()));
        mob.goalSelector.addGoal(p,   new RandomLookAroundGoal(mob));

        if (mob instanceof PathfinderMob pmob) {
            mob.targetSelector.addGoal(1, new HurtByTargetGoal(pmob));
        }
        mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, true));
    }
}
