package me.nagasonic.alkatraz.api.ai.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensorTest {

    @Test
    void nearestLivingEntities_accessors() {
        Sensor.NearestLivingEntities s = new Sensor.NearestLivingEntities(16);
        assertEquals(16, s.radius());
        assertThrows(IllegalArgumentException.class, () -> new Sensor.NearestLivingEntities(-1));
    }

    @Test
    void nearestPlayers_accessors() {
        Sensor.NearestPlayers s = new Sensor.NearestPlayers(8);
        assertEquals(8, s.radius());
        assertThrows(IllegalArgumentException.class, () -> new Sensor.NearestPlayers(-8));
    }

    @Test
    void hurtBy_constructs() {
        assertNotNull(new Sensor.HurtBy());
    }

    @Test
    void customSensor_isRecognisedAsSensor() {
        CustomSensor custom = new CustomSensor() {
            @Override
            public void sense(TaskBrainContext ctx) {
                ctx.setMemory(MemoryKey.IS_HURT, true);
            }
        };
        assertTrue(custom instanceof Sensor);
        assertTrue(custom instanceof CustomSensor);
    }
}
