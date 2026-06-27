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
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

public class GoalBuilder {
    private GoalBuilder() {}

    /**
     * Clears all existing goals on {@code mob} and re-registers them from
     * {@code brain}. Also registers the standard player/hurt-by target selectors.
     *
     * @param mob    the NMS mob whose goal selectors will be populated
     * @param magic  the {@link MagicEntity} side of the same mob (may be the
     *               same object if the mob implements both interfaces)
     * @param brain  declarative description of the mob's desired behaviour
     */
    public static void apply(Mob mob, MagicEntity magic, MobBrain brain) {
        mob.goalSelector.removeAllGoals(g -> true);
        mob.targetSelector.removeAllGoals(g -> true);

        int p = 1;

        // -- movement / survival --------------------------------------------------

        if (brain.canSwim()) {
            mob.goalSelector.addGoal(p++, new FloatGoal(mob));
        }

        // -- spellcasting ---------------------------------------------------------

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

        // -- melee ----------------------------------------------------------------

        if (brain.meleeAttack() && mob instanceof Zombie zombie) {
            mob.goalSelector.addGoal(p++, new ZombieAttackGoal(zombie, 1.0D, false));
        }

        // -- passive movement / look ----------------------------------------------

        if (brain.randomStroll()) {
            if (mob instanceof PathfinderMob pmob) {
                mob.goalSelector.addGoal(p++, new WaterAvoidingRandomStrollGoal(pmob, 0.8D));
            }
        }

        mob.goalSelector.addGoal(p++, new LookAtPlayerGoal(mob, Player.class, brain.lookAtPlayerRange()));
        mob.goalSelector.addGoal(p,   new RandomLookAroundGoal(mob));

        // -- targeting ------------------------------------------------------------

        if (mob instanceof PathfinderMob pmob) {
            mob.targetSelector.addGoal(1, new HurtByTargetGoal(pmob));
        }
        mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, true));
    }
}
