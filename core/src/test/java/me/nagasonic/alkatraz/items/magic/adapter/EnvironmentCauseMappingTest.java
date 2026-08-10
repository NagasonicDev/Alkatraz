package me.nagasonic.alkatraz.items.magic.adapter;

import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnvironmentCauseMappingTest {

    @Test
    void lightningMapsToOnLightning() {
        assertEquals("on_lightning", EnvironmentTriggerListener.triggerForCause(EntityDamageEvent.DamageCause.LIGHTNING));
    }

    @Test
    void explosionMapsToOnExplosionDamage() {
        assertEquals("on_explosion_damage", EnvironmentTriggerListener.triggerForCause(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION));
        assertEquals("on_explosion_damage", EnvironmentTriggerListener.triggerForCause(EntityDamageEvent.DamageCause.BLOCK_EXPLOSION));
    }

    @Test
    void fireCausesMapToOnFireDamage() {
        assertEquals("on_fire_damage", EnvironmentTriggerListener.triggerForCause(EntityDamageEvent.DamageCause.FIRE));
        assertEquals("on_fire_damage", EnvironmentTriggerListener.triggerForCause(EntityDamageEvent.DamageCause.FIRE_TICK));
        assertEquals("on_fire_damage", EnvironmentTriggerListener.triggerForCause(EntityDamageEvent.DamageCause.LAVA));
    }

    @Test
    void drowningMapsToOnDrown() {
        assertEquals("on_drown", EnvironmentTriggerListener.triggerForCause(EntityDamageEvent.DamageCause.DROWNING));
    }

    @Test
    void freezingMapsToOnFreeze() {
        assertEquals("on_freeze", EnvironmentTriggerListener.triggerForCause(EntityDamageEvent.DamageCause.FREEZE));
    }

    @Test
    void fallMapsToOnFallDamage() {
        assertEquals("on_fall_damage", EnvironmentTriggerListener.triggerForCause(EntityDamageEvent.DamageCause.FALL));
    }

    @Test
    void voidMapsToOnVoid() {
        assertEquals("on_void", EnvironmentTriggerListener.triggerForCause(EntityDamageEvent.DamageCause.VOID));
    }

    @Test
    void starvationMapsToOnStarve() {
        assertEquals("on_starve", EnvironmentTriggerListener.triggerForCause(EntityDamageEvent.DamageCause.STARVATION));
    }

    @Test
    void suffocationMapsToOnSuffocate() {
        assertEquals("on_suffocate", EnvironmentTriggerListener.triggerForCause(EntityDamageEvent.DamageCause.SUFFOCATION));
    }

    @Test
    void unknownCauseMapsToNull() {
        assertNull(EnvironmentTriggerListener.triggerForCause(EntityDamageEvent.DamageCause.CONTACT));
        assertNull(EnvironmentTriggerListener.triggerForCause(EntityDamageEvent.DamageCause.MAGIC));
    }
}
