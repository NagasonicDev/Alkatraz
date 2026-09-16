package me.nagasonic.alkatraz.api.ai.control;

import me.nagasonic.alkatraz.api.ai.navigation.NavigationSpec;

import java.util.Optional;

/**
 * Immutable set of control actuators for one mob. Each slot is individually
 * omittable: an absent slot means "keep the vanilla actuator" (the
 * zero-config default rule).
 */
public final class ControlProfile {

    private final Optional<NavigationSpec> navigation;
    private final Optional<MoveControlSpec> move;
    private final Optional<LookControlSpec> look;
    private final Optional<Double> jumpChance;

    private ControlProfile(Optional<NavigationSpec> navigation, Optional<MoveControlSpec> move,
                           Optional<LookControlSpec> look, Optional<Double> jumpChance) {
        this.navigation = navigation;
        this.move = move;
        this.look = look;
        this.jumpChance = jumpChance;
    }

    /**
     * Builds a profile. A {@code null} slot is treated as "omit"; a present
     * {@code jumpChance} must lie in {@code [0, 1]}.
     */
    public static ControlProfile of(NavigationSpec navigation, MoveControlSpec move,
                                    LookControlSpec look, Optional<Double> jumpChance) {
        if (jumpChance != null && jumpChance.isPresent()) {
            double chance = jumpChance.get();
            if (chance < 0.0 || chance > 1.0) {
                throw new IllegalArgumentException("jumpChance must be within 0..1");
            }
        }
        return new ControlProfile(
                Optional.ofNullable(navigation),
                Optional.ofNullable(move),
                Optional.ofNullable(look),
                jumpChance == null ? Optional.empty() : jumpChance);
    }

    /** The requested navigation actuator, if any. */
    public Optional<NavigationSpec> navigation() {
        return navigation;
    }

    /** The requested move-control actuator, if any. */
    public Optional<MoveControlSpec> move() {
        return move;
    }

    /** The requested look-control actuator, if any. */
    public Optional<LookControlSpec> look() {
        return look;
    }

    /** The jump chance (present means RANDOM jump control), if any. */
    public Optional<Double> jumpChance() {
        return jumpChance;
    }
}
