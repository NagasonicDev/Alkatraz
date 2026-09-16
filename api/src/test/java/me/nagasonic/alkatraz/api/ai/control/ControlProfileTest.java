package me.nagasonic.alkatraz.api.ai.control;

import me.nagasonic.alkatraz.api.ai.navigation.NavigationKind;
import me.nagasonic.alkatraz.api.ai.navigation.NavigationSpec;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ControlProfileTest {

    @Test
    void fullProfile_roundTrips() {
        ControlProfile p = ControlProfile.of(
                new NavigationSpec.FixedType(NavigationKind.FLYING),
                new MoveControlSpec.Flying(0.5, true),
                new LookControlSpec.BodyRotation(30),
                Optional.of(0.25));
        assertEquals(new NavigationSpec.FixedType(NavigationKind.FLYING), p.navigation().orElseThrow());
        assertEquals(new MoveControlSpec.Flying(0.5, true), p.move().orElseThrow());
        assertEquals(new LookControlSpec.BodyRotation(30), p.look().orElseThrow());
        assertEquals(Optional.of(0.25), p.jumpChance());
    }

    @Test
    void omittedSlots_areEmpty() {
        ControlProfile p = ControlProfile.of(null, null, null, null);
        assertTrue(p.navigation().isEmpty());
        assertTrue(p.move().isEmpty());
        assertTrue(p.look().isEmpty());
        assertTrue(p.jumpChance().isEmpty());
    }

    @Test
    void partialProfile_onlySetsOneSlot() {
        ControlProfile p = ControlProfile.of(null, new MoveControlSpec.Generic(0.4), null, null);
        assertTrue(p.navigation().isEmpty());
        assertEquals(new MoveControlSpec.Generic(0.4), p.move().orElseThrow());
        assertTrue(p.look().isEmpty());
    }

    @Test
    void jumpChance_mustBeBetweenZeroAndOne() {
        assertThrows(IllegalArgumentException.class, () -> ControlProfile.of(null, null, null, Optional.of(1.5)));
        assertThrows(IllegalArgumentException.class, () -> ControlProfile.of(null, null, null, Optional.of(-0.1)));
        assertDoesNotThrow(() -> ControlProfile.of(null, null, null, Optional.of(0.0)));
        assertDoesNotThrow(() -> ControlProfile.of(null, null, null, Optional.of(1.0)));
    }

    @Test
    void moveControlSpec_validation() {
        assertEquals(0.5, new MoveControlSpec.Flying(0.5, false).speed());
        assertTrue(new MoveControlSpec.Flying(0.5, true).canFloat());
        assertThrows(IllegalArgumentException.class, () -> new MoveControlSpec.Flying(-0.1, false));
        assertThrows(IllegalArgumentException.class, () -> new MoveControlSpec.Water(-1.0));
        assertThrows(IllegalArgumentException.class, () -> new MoveControlSpec.Generic(-2.5));
        assertEquals(1.0, new MoveControlSpec.Water(1.0).speed());
    }

    @Test
    void lookControlSpec_validation() {
        assertEquals(30, new LookControlSpec.BodyRotation(30).maxYawChange());
        assertThrows(IllegalArgumentException.class, () -> new LookControlSpec.BodyRotation(-1));
        assertNotNull(new LookControlSpec.Vanilla());
    }

    @Test
    void jumpControlSpec_constantsExist() {
        assertEquals(2, JumpControlSpec.values().length);
        assertNotEquals(JumpControlSpec.RANDOM, JumpControlSpec.NONE);
    }
}
