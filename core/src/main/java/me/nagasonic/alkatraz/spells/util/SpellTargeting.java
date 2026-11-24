package me.nagasonic.alkatraz.spells.util;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ally rules for the 0.9.2 spells (spec section 3 item 3). An ally is the caster
 * or a player on the caster's scoreboard team. Team membership is only a convenience
 * filter; being carried by another player's spell additionally requires that
 * player's explicit teleport opt-in ({@link TeleportSafety#hasTeleportConsent}).
 *
 * <p>Result order is deterministic - nearest first, ties broken by UUID - so a
 * capped selection is reproducible across calls and test runs.
 */
public final class SpellTargeting {

    private SpellTargeting() {
    }

    /** True when {@code candidate} is the caster or shares the caster's scoreboard team. */
    public static boolean isAlly(LivingEntity caster, LivingEntity candidate) {
        if (caster == null || candidate == null) return false;
        if (caster.getUniqueId().equals(candidate.getUniqueId())) return true;
        if (!(caster instanceof Player casterPlayer) || !(candidate instanceof Player candidatePlayer)) return false;
        Team team = teamOf(casterPlayer);
        return team != null && team.equals(teamOf(candidatePlayer));
    }

    /**
     * Consenting players within {@code radius} of {@code origin}, capped at
     * {@code max}. Excludes the caster (callers handle the caster separately).
     */
    public static List<Player> allies(LivingEntity caster, Location origin, double radius, int max) {
        if (caster == null || origin == null || origin.getWorld() == null || max <= 0) return List.of();
        double radiusSq = radius * radius;
        List<Player> found = new ArrayList<>();
        for (Player player : origin.getWorld().getPlayers()) {
            if (player.isDead()) continue;
            if (caster.getUniqueId().equals(player.getUniqueId())) continue;
            if (!isAlly(caster, player)) continue;
            if (player.getLocation().distanceSquared(origin) > radiusSq) continue;
            found.add(player);
        }
        found.sort(Comparator
                .comparingDouble((Player p) -> p.getLocation().distanceSquared(origin))
                .thenComparing(p -> p.getUniqueId().toString()));
        if (found.size() > max) return new ArrayList<>(found.subList(0, max));
        return found;
    }

    /** Team members who also opted in to being teleported by other players. */
    public static List<Player> consenting(LivingEntity caster, Location origin, double radius, int max) {
        List<Player> found = new ArrayList<>(allies(caster, origin, radius, max));
        found.removeIf(player -> !TeleportSafety.hasTeleportConsent(player));
        return found;
    }

    /** Convenience filter for callers that need to prioritise consenting allies. */
    public static boolean isConsentingAlly(LivingEntity caster, Player candidate) {
        return isAlly(caster, candidate) && TeleportSafety.hasTeleportConsent(candidate);
    }

    private static Team teamOf(Player player) {
        Scoreboard board = player.getScoreboard();
        return board == null ? null : board.getEntryTeam(player.getName());
    }
}
