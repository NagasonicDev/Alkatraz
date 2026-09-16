package me.nagasonic.alkatraz.mobs.goals;

import me.nagasonic.alkatraz.Alkatraz;
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
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CastSpellGoalTest {

    private static final double CAST_RANGE = 14.0;

    private void runWithStaticMocks(Consumer<StaticMocks> test) {
        try (MockedStatic<MagicEntities> spellStatic = mockStatic(MagicEntities.class);
             MockedStatic<WandUtils> wandStatic = mockStatic(WandUtils.class);
             MockedStatic<Bukkit> bukkitStatic = mockStatic(Bukkit.class);
             MockedStatic<Alkatraz> alkatrazStatic = mockStatic(Alkatraz.class)) {

            PluginManager pm = mock(PluginManager.class);
            bukkitStatic.when(Bukkit::getPluginManager).thenReturn(pm);

            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkitStatic.when(Bukkit::getScheduler).thenReturn(scheduler);

            alkatrazStatic.when(Alkatraz::getInstance).thenReturn(mock(Alkatraz.class));

            StaticMocks mocks = new StaticMocks(spellStatic, wandStatic, bukkitStatic, alkatrazStatic, pm, scheduler);
            test.accept(mocks);
        }
    }

    private record StaticMocks(
            MockedStatic<MagicEntities> spellStatic,
            MockedStatic<WandUtils> wandStatic,
            MockedStatic<Bukkit> bukkitStatic,
            MockedStatic<Alkatraz> alkatrazStatic,
            PluginManager pm,
            BukkitScheduler scheduler
    ) {}

    private MobBrainContext mockContext(boolean hasTarget) {
        MobBrainContext ctx = mock(MobBrainContext.class);
        if (hasTarget) {
            LivingEntity target = mock(LivingEntity.class);
            when(target.isDead()).thenReturn(false);
            when(ctx.getTarget()).thenReturn(target);
            when(ctx.distanceSq(any())).thenReturn(0.0);
            when(ctx.hasLineOfSight(any())).thenReturn(true);
            when(ctx.self()).thenReturn(mock(Mob.class));
        }
        return ctx;
    }

    private Spell mockSpell(boolean castable) {
        Spell spell = mock(Spell.class);
        when(spell.canMobCast(any())).thenReturn(castable);
        when(spell.circleAction(any(), any())).thenReturn(42);
        when(spell.getFullCastTime(any(), anyDouble())).thenReturn(1.0f);
        when(spell.getCastTime()).thenReturn(10.0);
        doNothing().when(spell).mobCast(any(), any());
        return spell;
    }

    private CastSpellGoal buildGoal(int cooldownTicks) {
        return new CastSpellGoal(new SpellCastConfig(0.0, 0.0, CAST_RANGE, cooldownTicks));
    }

    private void driveWindUp(StaticMocks m, CastSpellGoal goal, MobBrainContext ctx, int ticks) {
        for (int i = 0; i < ticks; i++) {
            goal.tick(ctx);
        }
    }

    @Test
    void flagsContainsLook() {
        CastSpellGoal goal = buildGoal(0);
        assertEquals(EnumSet.of(GoalFlag.LOOK), goal.flags());
    }

    @Test
    void canStartNullTargetFalse() {
        runWithStaticMocks(m -> {
            MobBrainContext ctx = mock(MobBrainContext.class);
            when(ctx.getTarget()).thenReturn(null);
            CastSpellGoal goal = buildGoal(0);
            assertFalse(goal.canStart(ctx));
        });
    }

    @Test
    void canStartDeadTargetFalse() {
        runWithStaticMocks(m -> {
            MobBrainContext ctx = mockContext(true);
            when(ctx.getTarget().isDead()).thenReturn(true);
            CastSpellGoal goal = buildGoal(0);
            assertFalse(goal.canStart(ctx));
        });
    }

    @Test
    void canStartOutOfRangeFalse() {
        runWithStaticMocks(m -> {
            MobBrainContext ctx = mockContext(true);
            when(ctx.distanceSq(any())).thenReturn(400.0);
            CastSpellGoal goal = buildGoal(0);
            assertFalse(goal.canStart(ctx));
        });
    }

    @Test
    void canStartNoLineOfSightFalse() {
        runWithStaticMocks(m -> {
            MobBrainContext ctx = mockContext(true);
            when(ctx.distanceSq(any())).thenReturn(100.0);
            when(ctx.hasLineOfSight(any())).thenReturn(false);
            CastSpellGoal goal = buildGoal(0);
            assertFalse(goal.canStart(ctx));
        });
    }

    @Test
    void canStartEmptySpellsFalse() {
        runWithStaticMocks(m -> {
            MobBrainContext ctx = mockContext(true);
            m.spellStatic.when(() -> MagicEntities.getSpells(any())).thenReturn(List.of());
            CastSpellGoal goal = buildGoal(0);
            assertFalse(goal.canStart(ctx));
        });
    }

    @Test
    void canStartAllSpellsUncastableFalse() {
        runWithStaticMocks(m -> {
            MobBrainContext ctx = mockContext(true);
            Spell uncastable = mockSpell(false);
            m.spellStatic.when(() -> MagicEntities.getSpells(any())).thenReturn(List.of(uncastable));
            CastSpellGoal goal = buildGoal(0);
            assertFalse(goal.canStart(ctx));
        });
    }

    @Test
    void canStartPicksCastableSpellAndReturnsTrue() {
        runWithStaticMocks(m -> {
            MobBrainContext ctx = mockContext(true);
            Spell bad = mockSpell(false);
            Spell good = mockSpell(true);
            m.spellStatic.when(() -> MagicEntities.getSpells(any())).thenReturn(List.of(bad, good));
            CastSpellGoal goal = buildGoal(0);
            assertTrue(goal.canStart(ctx));
            verify(good, atLeastOnce()).canMobCast(any());
        });
    }

    @Test
    void cooldownGatesNextStart() {
        runWithStaticMocks(m -> {
            MobBrainContext ctx = mockContext(true);
            Spell good = mockSpell(true);
            m.spellStatic.when(() -> MagicEntities.getSpells(any())).thenReturn(List.of(good));
            CastSpellGoal goal = buildGoal(3);

            assertTrue(goal.canStart(ctx));
            driveWindUp(m, goal, ctx, 15);
            verify(m.scheduler).runTaskLater(any(), any(Runnable.class), anyLong());

            assertFalse(goal.canStart(ctx));
            assertFalse(goal.canStart(ctx));
            assertFalse(goal.canStart(ctx));
            assertTrue(goal.canStart(ctx));
        });
    }

    @Test
    void windUpReleasesAtTickFifteen() {
        runWithStaticMocks(m -> {
            MobBrainContext ctx = mockContext(true);
            Spell good = mockSpell(true);
            m.spellStatic.when(() -> MagicEntities.getSpells(any())).thenReturn(List.of(good));
            CastSpellGoal goal = buildGoal(0);
            assertTrue(goal.canStart(ctx));

            for (int i = 0; i < 14; i++) {
                goal.tick(ctx);
            }
            verify(m.scheduler, never()).runTaskLater(any(), any(Runnable.class), anyLong());

            goal.tick(ctx);
            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(m.scheduler).runTaskLater(any(), captor.capture(), anyLong());

            Runnable callback = captor.getValue();
            callback.run();
            verify(m.scheduler).cancelTask(42);
            verify(good).mobCast(any(), isNull());
        });
    }

    @Test
    void canceledPreparationSkipsCast() {
        runWithStaticMocks(m -> {
            MobBrainContext ctx = mockContext(true);
            Spell good = mockSpell(true);
            m.spellStatic.when(() -> MagicEntities.getSpells(any())).thenReturn(List.of(good));
            CastSpellGoal goal = buildGoal(0);

            doAnswer(inv -> {
                SpellPrepareEvent e = inv.getArgument(0);
                e.setCancelled(true);
                return null;
            }).when(m.pm).callEvent(any());

            assertTrue(goal.canStart(ctx));
            driveWindUp(m, goal, ctx, 15);
            verify(m.scheduler, never()).runTaskLater(any(), any(Runnable.class), anyLong());
        });
    }

    @Test
    void stopClearsSelection() {
        runWithStaticMocks(m -> {
            MobBrainContext ctx = mockContext(true);
            Spell good = mockSpell(true);
            m.spellStatic.when(() -> MagicEntities.getSpells(any())).thenReturn(List.of(good));
            CastSpellGoal goal = buildGoal(0);

            assertTrue(goal.canStart(ctx));
            goal.stop(ctx);

            m.spellStatic.when(() -> MagicEntities.getSpells(any())).thenReturn(List.of());
            assertFalse(goal.canStart(ctx));
        });
    }

    @Test
    void wandPathInvokesIsWandAndUsesMainHand() {
        runWithStaticMocks(m -> {
            MobBrainContext ctx = mockContext(true);
            Spell good = mockSpell(true);
            m.spellStatic.when(() -> MagicEntities.getSpells(any())).thenReturn(List.of(good));

            ItemStack wand = mock(ItemStack.class);
            when(wand.getType()).thenReturn(Material.DIAMOND_SWORD);
            when(ctx.getMainHandItem()).thenReturn(wand);
            m.wandStatic.when(() -> WandUtils.isWand(any())).thenReturn(true);

            CastSpellGoal goal = buildGoal(0);
            assertTrue(goal.canStart(ctx));
            driveWindUp(m, goal, ctx, 15);

            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(m.scheduler).runTaskLater(any(), captor.capture(), anyLong());
            captor.getValue().run();
            verify(good).mobCast(any(), eq(wand));
        });
    }

    @Test
    void airItemTreatsAsNoWand() {
        runWithStaticMocks(m -> {
            MobBrainContext ctx = mockContext(true);
            Spell good = mockSpell(true);
            m.spellStatic.when(() -> MagicEntities.getSpells(any())).thenReturn(List.of(good));

            ItemStack airItem = mock(ItemStack.class);
            when(airItem.getType()).thenReturn(Material.AIR);
            when(ctx.getMainHandItem()).thenReturn(airItem);
            m.wandStatic.when(() -> WandUtils.isWand(any())).thenReturn(true);

            CastSpellGoal goal = buildGoal(0);
            assertTrue(goal.canStart(ctx));
            driveWindUp(m, goal, ctx, 15);

            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(m.scheduler).runTaskLater(any(), captor.capture(), anyLong());
            captor.getValue().run();
            verify(good).mobCast(any(), isNull());
        });
    }
}
