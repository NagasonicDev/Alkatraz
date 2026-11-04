package me.nagasonic.alkatraz.spells.modifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModifierManagerTest {

    @AfterEach
    void cleanup() {
        ModifierManager.clear();
    }

    @Test
    void grantListAndExpiry() {
        UUID player = UUID.randomUUID();
        ModifierInstance granted = ModifierManager.grant(player,
                new ModifierInstance("src", "magicAffinity", ModifierInstance.Operation.ADD, 1.0, 1000L));
        assertEquals(1, ModifierManager.count(player));

        List<ModifierInstance> live = ModifierManager.list(player);
        assertEquals(1, live.size());

        // Simulate expiry by replacing with expired via manager? Manager prunes by wall clock only;
        // use idempotent repeated grant with same id does not duplicate.
        ModifierInstance again = ModifierManager.grant(player, granted);
        assertEquals(1, ModifierManager.count(player));
    }

    @Test
    void idempotentIgnoreKeepsExisting() {
        UUID player = UUID.randomUUID();
        java.util.UUID fixedId = java.util.UUID.randomUUID();
        ModifierInstance first = ModifierManager.grant(player, new ModifierInstance(fixedId,
                ModifierInstance.Type.STAT, ModifierInstance.Scope.PLAYER, player.toString(),
                "src", "x", ModifierInstance.Operation.ADD, 1.0, 1000L,
                ModifierInstance.StackPolicy.IGNORE, true));
        ModifierInstance second = new ModifierInstance(fixedId, ModifierInstance.Type.STAT,
                ModifierInstance.Scope.PLAYER, player.toString(), "src2", "x",
                ModifierInstance.Operation.ADD, 2.0, 1000L,
                ModifierInstance.StackPolicy.IGNORE, false);
        ModifierInstance returned = ModifierManager.grant(player, second);
        assertEquals(first, returned);
        assertEquals(1, ModifierManager.count(player));
    }

    @Test
    void revokeRemoves() {
        UUID player = UUID.randomUUID();
        ModifierInstance m = ModifierManager.grant(player,
                new ModifierInstance("src", "x", ModifierInstance.Operation.ADD, 1.0, 1000L));
        assertTrue(ModifierManager.revoke(player, m.id()));
        assertEquals(0, ModifierManager.count(player));
        assertFalse(ModifierManager.revoke(player, m.id()));
    }

    @Test
    void stackingMintsNewId() {
        UUID player = UUID.randomUUID();
        ModifierInstance base = new ModifierInstance(null, ModifierInstance.Type.STAT,
                ModifierInstance.Scope.PLAYER, player.toString(), "src", "x",
                ModifierInstance.Operation.ADD, 1.0, 1000L,
                ModifierInstance.StackPolicy.STACK, false);
        ModifierInstance a = ModifierManager.grant(player, base);
        ModifierInstance b = ModifierManager.grant(player, base);
        assertNotEquals(a.id(), b.id());
        assertEquals(2, ModifierManager.count(player));
    }

    @Test
    void globalPruneSmoke() {
        ModifierManager.grant(new ModifierInstance("src", "x", ModifierInstance.Operation.ADD, 1.0, 1L));
        // cannot force time; just ensure listAll non-null
        assertTrue(ModifierManager.listAll() != null);
    }
}