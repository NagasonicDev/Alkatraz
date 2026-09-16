package me.nagasonic.alkatraz.api.ai;

import me.nagasonic.alkatraz.api.ai.control.JumpControlSpec;
import me.nagasonic.alkatraz.api.ai.control.LookControlSpec;
import me.nagasonic.alkatraz.api.ai.control.MoveControlSpec;
import me.nagasonic.alkatraz.api.ai.navigation.NavigationKind;
import me.nagasonic.alkatraz.api.ai.navigation.NavigationSpec;
import me.nagasonic.alkatraz.api.ai.task.ActivitySpec;
import me.nagasonic.alkatraz.api.ai.task.NativeBehaviorSpec;
import me.nagasonic.alkatraz.api.ai.task.Sensor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiSpecFamilyTest {

    @Test
    void everyDeclarativeSpec_isAnAiSpec() {
        assertTrue(new NativeBehaviorSpec.LookAtTargetSink(8, 30) instanceof AiSpec);
        assertTrue(new Sensor.NearestLivingEntities(16) instanceof AiSpec);
        assertTrue(new Sensor.HurtBy() instanceof AiSpec);
        assertTrue(JumpControlSpec.RANDOM instanceof AiSpec);
        // JumpControlSpec is an enum; verify via its constant as an AiSpec instance
        AiSpec jump = JumpControlSpec.NONE;
        assertSame(JumpControlSpec.NONE, jump);

        ActivitySpec activity = new ActivitySpec(
                "wander", 0, List.of(new Sensor.NearestPlayers(8)),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                me.nagasonic.alkatraz.api.ai.task.TimeOfDayPredicate.DAY);
        assertTrue(activity instanceof AiSpec);

        assertTrue(new MoveControlSpec.Generic(0.4) instanceof AiSpec);
        assertTrue(new LookControlSpec.Vanilla() instanceof AiSpec);
        assertTrue(new NavigationSpec.FixedType(NavigationKind.GROUND) instanceof AiSpec);
        AiSpec navigation = new NavigationSpec.Default();
        assertNotNull(navigation);
    }

    @Test
    void everySpecInterface_isAssignableToAiSpec() {
        // The umbrella is deliberately non-sealed (unnamed-module restriction);
        // each spec family stays sealed over its own permitted subtypes. This
        // guards that all seven family roots stay under the AiSpec umbrella.
        assertTrue(AiSpec.class.isAssignableFrom(NativeBehaviorSpec.class));
        assertTrue(AiSpec.class.isAssignableFrom(Sensor.class));
        assertTrue(AiSpec.class.isAssignableFrom(ActivitySpec.class));
        assertTrue(AiSpec.class.isAssignableFrom(MoveControlSpec.class));
        assertTrue(AiSpec.class.isAssignableFrom(LookControlSpec.class));
        assertTrue(AiSpec.class.isAssignableFrom(JumpControlSpec.class));
        assertTrue(AiSpec.class.isAssignableFrom(NavigationSpec.class));
    }
}