package me.nagasonic.alkatraz.api.ai.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemoryKeyTest {

    @Test
    void id_returnsSuppliedId() {
        assertEquals("wander", new MemoryKey<>("wander").id());
    }

    @Test
    void blankId_throws() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryKey<>(""));
        assertThrows(IllegalArgumentException.class, () -> new MemoryKey<>("  "));
    }

    @Test
    void nullId_throws() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryKey<>((String) null));
    }

    @Test
    void equalsAndHashCode_areIdBased() {
        MemoryKey<Object> a = new MemoryKey<>("attack_target");
        MemoryKey<Object> b = new MemoryKey<>("attack_target");
        MemoryKey<Object> c = new MemoryKey<>("other");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void constants_haveStableIds() {
        assertEquals("attack_target", MemoryKey.ATTACK_TARGET.id());
        assertEquals("walk_target", MemoryKey.WALK_TARGET.id());
        assertEquals("nearest_hostiles", MemoryKey.NEAREST_HOSTILES.id());
        assertEquals("is_hurt", MemoryKey.IS_HURT.id());
    }
}
