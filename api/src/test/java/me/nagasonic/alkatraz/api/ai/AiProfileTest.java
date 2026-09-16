package me.nagasonic.alkatraz.api.ai;

import me.nagasonic.alkatraz.api.ai.attribute.AttributeProfile;
import me.nagasonic.alkatraz.api.ai.attribute.AttributeValue;
import me.nagasonic.alkatraz.api.ai.task.TaskBrain;
import org.bukkit.attribute.Attribute;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AiProfileTest {

    private static TaskBrain brain() {
        return TaskBrain.builder().build();
    }

    private static AttributeProfile attributes() {
        return AttributeProfile.of(new AttributeValue(Attribute.GENERIC_MAX_HEALTH, 40.0));
    }

    @Test
    void emptyProfile_normalisesNullSlots() {
        AiProfile p = new AiProfile(null, null, null);
        assertTrue(p.taskBrain().isEmpty());
        assertTrue(p.control().isEmpty());
        assertTrue(p.attributes().isEmpty());
    }

    @Test
    void fullProfile_roundTrips() {
        TaskBrain brain = brain();
        AttributeProfile attrs = attributes();
        AiProfile p = new AiProfile(Optional.of(brain), null, Optional.of(attrs));
        assertEquals(brain, p.taskBrain().orElseThrow());
        assertTrue(p.control().isEmpty());
        assertEquals(attrs, p.attributes().orElseThrow());
    }

    @Test
    void records_areValueTyped() {
        TaskBrain brain = brain();
        AiProfile a = new AiProfile(Optional.of(brain), null, null);
        AiProfile b = new AiProfile(Optional.of(brain), null, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
