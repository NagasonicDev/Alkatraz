package me.nagasonic.alkatraz.spells.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Team-only ally rule (spec section 3 item 3), plus deterministic capped ordering. */
class SpellTargetingTest {

    private static final UUID CASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    private static Player player(String name, UUID id, Team team, Scoreboard board) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getScoreboard()).thenReturn(board);
        when(board.getEntryTeam(name)).thenReturn(team);
        return player;
    }

    @Test
    void casterIsItsOwnAlly() {
        LivingEntity caster = mock(LivingEntity.class);
        when(caster.getUniqueId()).thenReturn(CASTER_ID);
        assertTrue(SpellTargeting.isAlly(caster, caster));
    }

    @Test
    void sameTeamIsAllyAndDifferentOrMissingTeamIsNot() {
        Scoreboard board = mock(Scoreboard.class);
        Team teamA = mock(Team.class);
        Team teamB = mock(Team.class);
        Player caster = player("Caster", CASTER_ID, teamA, board);
        Player mate = player("Mate", UUID.randomUUID(), teamA, board);
        Player stranger = player("Stranger", UUID.randomUUID(), teamB, board);
        Player lone = player("Lone", UUID.randomUUID(), null, board);

        assertTrue(SpellTargeting.isAlly(caster, mate));
        assertFalse(SpellTargeting.isAlly(caster, stranger));
        assertFalse(SpellTargeting.isAlly(caster, lone));
    }

    @Test
    void nonPlayerCasterHasNoAllies() {
        LivingEntity caster = mock(LivingEntity.class);
        when(caster.getUniqueId()).thenReturn(CASTER_ID);
        Player mate = mock(Player.class);
        when(mate.getUniqueId()).thenReturn(UUID.randomUUID());
        assertFalse(SpellTargeting.isAlly(caster, mate));
    }

    @Test
    void alliesAreCappedAndOrderedByDistanceThenUuid() {
        Scoreboard board = mock(Scoreboard.class);
        Team team = mock(Team.class);
        World world = mock(World.class);
        Location origin = new Location(world, 0, 0, 0);

        Player caster = player("Caster", CASTER_ID, team, board);
        // tie on distance 1.0 - UUID must break it
        Player near = player("Near", UUID.fromString("00000000-0000-0000-0000-000000000001"), team, board);
        Player nearTwo = player("NearTwo", UUID.fromString("00000000-0000-0000-0000-000000000002"), team, board);
        Player far = player("Far", UUID.fromString("00000000-0000-0000-0000-000000000003"), team, board);
        Player outsider = player("Outsider", UUID.fromString("00000000-0000-0000-0000-000000000004"), mock(Team.class), board);

        when(near.getLocation()).thenReturn(new Location(world, 1, 0, 0));
        when(nearTwo.getLocation()).thenReturn(new Location(world, 0, 0, 1));
        when(far.getLocation()).thenReturn(new Location(world, 3, 0, 0));
        when(outsider.getLocation()).thenReturn(new Location(world, 0.1, 0, 0));
        when(caster.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        when(world.getPlayers()).thenReturn(List.of(far, outsider, nearTwo, near, caster));

        List<Player> allies = SpellTargeting.allies(caster, origin, 8, 2);
        assertEquals(List.of(near, nearTwo), allies);
    }
}
