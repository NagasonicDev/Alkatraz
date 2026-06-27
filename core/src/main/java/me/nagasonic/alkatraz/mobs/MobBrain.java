package me.nagasonic.alkatraz.mobs;

/**
 * Declarative description of a magic mob's AI behaviour. Passed to the
 * version-specific {@code GoalBuilder} so that mob definitions (what the mob
 * does) stay in the API module and NMS goal wiring stays in the version module.
 *
 * <p>Build via the fluent {@link Builder}:
 * <pre>
 *   private static final MobBrain BRAIN = MobBrain.builder()
 *       .canSwim(true)
 *       .spellCast(new SpellCastConfig(6.0, 12.0, 14.0, 40))
 *       .meleeAttack(false)
 *       .lookAtPlayerRange(8.0f)
 *       .randomStroll(true)
 *       .build();
 * </pre>
 */
public final class MobBrain {

    private final boolean         canSwim;
    private final SpellCastConfig spellCast;      // null → no spellcasting goals
    private final boolean         meleeAttack;
    private final float           lookAtPlayerRange;
    private final boolean         randomStroll;

    private MobBrain(Builder b) {
        this.canSwim           = b.canSwim;
        this.spellCast         = b.spellCast;
        this.meleeAttack       = b.meleeAttack;
        this.lookAtPlayerRange = b.lookAtPlayerRange;
        this.randomStroll      = b.randomStroll;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Whether a swim/float goal should be registered. */
    public boolean canSwim() { return canSwim; }

    /**
     * Spell-casting configuration, or {@code null} if this mob should not cast
     * spells (e.g. a pure melee fighter whose spells are triggered externally).
     */
    public SpellCastConfig spellCast() { return spellCast; }

    /**
     * Whether a vanilla melee-attack goal should be added. Typically {@code false}
     * for mages that suppress melee, {@code true} for fighters.
     */
    public boolean meleeAttack() { return meleeAttack; }

    /** Radius passed to the LookAtPlayer goal. */
    public float lookAtPlayerRange() { return lookAtPlayerRange; }

    /** Whether a water-avoiding random stroll goal should be registered. */
    public boolean randomStroll() { return randomStroll; }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {

        private boolean         canSwim           = true;
        private SpellCastConfig spellCast         = null;
        private boolean         meleeAttack       = false;
        private float           lookAtPlayerRange = 8.0f;
        private boolean         randomStroll      = true;

        private Builder() {}

        public Builder canSwim(boolean canSwim)                       { this.canSwim = canSwim;                       return this; }
        public Builder spellCast(SpellCastConfig spellCast)           { this.spellCast = spellCast;                   return this; }
        public Builder meleeAttack(boolean meleeAttack)               { this.meleeAttack = meleeAttack;               return this; }
        public Builder lookAtPlayerRange(float lookAtPlayerRange)     { this.lookAtPlayerRange = lookAtPlayerRange;   return this; }
        public Builder randomStroll(boolean randomStroll)             { this.randomStroll = randomStroll;             return this; }

        public MobBrain build() { return new MobBrain(this); }
    }
}
