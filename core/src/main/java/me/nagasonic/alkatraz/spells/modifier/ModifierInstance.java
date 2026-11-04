package me.nagasonic.alkatraz.spells.modifier;

import java.util.Objects;
import java.util.UUID;

/**
 * Reusable, metadata-carrying description of a single granted modifier.
 *
 * <p>This is the foundation shared by admin grants, timed boosters and events:
 * it carries the target scope, the stat/attribute it touches, how it is combined,
 * when it starts and when it expires, plus an optional handle that knows how to
 * undo itself (an {@link AppliedModifier}).
 *
 * <p>It intentionally holds plain data plus an optional handle: no Bukkit
 * side-effects happen in this class. {@link ModifierManager} is responsible for
 * recording, expiry and (un)applying.
 */
public final class ModifierInstance {

    /** What kind of value the modifier changes. */
    public enum Type {
        STAT, ATTRIBUTE, APPLIED
    }

    /** Where the modifier applies. */
    public enum Scope {
        PLAYER, GROUP, WORLD, GLOBAL
    }

    /** How the amount is combined with the current value. */
    public enum Operation {
        ADD, MULTIPLY, SET
    }

    /** What happens when the same modifier is granted again. */
    public enum StackPolicy {
        REPLACE, STACK, IGNORE, REFRESH, STRONGEST
    }

    private final UUID id;
    private final Type type;
    private final Scope scope;
    private final String scopeKey;
    private final String source;
    private final String statOrAttributeId;
    private final Operation operation;
    private final double amount;
    private final long startAtMillis;
    private final long expiresAtMillis;
    private final StackPolicy stackPolicy;
    private final boolean persistent;
    private final AppliedModifier appliedModifier;
    private final Object handle;

    public ModifierInstance(UUID id,
                            Type type,
                            Scope scope,
                            String scopeKey,
                            String source,
                            String statOrAttributeId,
                            Operation operation,
                            double amount,
                            long startAtMillis,
                            long expiresAtMillis,
                            StackPolicy stackPolicy,
                            boolean persistent,
                            AppliedModifier appliedModifier,
                            Object handle) {
        this.id = (id == null) ? UUID.randomUUID() : id;
        this.type = (type == null) ? Type.STAT : type;
        this.scope = (scope == null) ? Scope.PLAYER : scope;
        this.scopeKey = (scopeKey == null) ? "" : scopeKey;
        this.source = (source == null) ? "" : source;
        this.statOrAttributeId = (statOrAttributeId == null) ? "" : statOrAttributeId;
        this.operation = (operation == null) ? Operation.ADD : operation;
        this.amount = amount;
        long start = startAtMillis <= 0 ? System.currentTimeMillis() : startAtMillis;
        this.startAtMillis = start;
        this.expiresAtMillis = expiresAtMillis > 0 ? expiresAtMillis : Long.MAX_VALUE;
        this.stackPolicy = (stackPolicy == null) ? StackPolicy.REPLACE : stackPolicy;
        this.persistent = persistent;
        this.appliedModifier = appliedModifier;
        this.handle = handle;
    }

    public ModifierInstance(UUID id,
                            Type type,
                            Scope scope,
                            String scopeKey,
                            String source,
                            String statOrAttributeId,
                            Operation operation,
                            double amount,
                            long startAtMillis,
                            long expiresAtMillis,
                            StackPolicy stackPolicy,
                            boolean persistent) {
        this(id, type, scope, scopeKey, source, statOrAttributeId, operation, amount,
                startAtMillis, expiresAtMillis, stackPolicy, persistent, null, null);
    }

    public ModifierInstance(UUID id,
                            Type type,
                            Scope scope,
                            String scopeKey,
                            String source,
                            String statOrAttributeId,
                            Operation operation,
                            double amount,
                            long durationMillis,
                            StackPolicy stackPolicy,
                            boolean persistent) {
        this(id, type, scope, scopeKey, source, statOrAttributeId, operation, amount,
                System.currentTimeMillis(),
                durationMillis > 0 ? System.currentTimeMillis() + durationMillis : Long.MAX_VALUE,
                stackPolicy, persistent, null, null);
    }

    /**
     * Convenience constructor: a player-scoped stat modifier granted now, replacing
     * any previous one with the same id.
     */
    public ModifierInstance(String source,
                            String statOrAttributeId,
                            Operation operation,
                            double amount,
                            long durationMillis) {
        this(null, Type.STAT, Scope.PLAYER, "", source, statOrAttributeId, operation,
                amount, durationMillis, StackPolicy.REPLACE, false);
    }

    // ---- getters (and aliases) ----

    public UUID id() { return id; }
    public UUID getId() { return id; }

    public Type type() { return type; }
    public Type getType() { return type; }

    public Scope scope() { return scope; }
    public Scope getScope() { return scope; }

    public String scopeKey() { return scopeKey; }
    public String getScopeKey() { return scopeKey; }

    public String source() { return source; }
    public String getSource() { return source; }

    public String statOrAttributeId() { return statOrAttributeId; }
    public String getStatOrAttributeId() { return statOrAttributeId; }
    public String getStatId() { return statOrAttributeId; }

    public Operation operation() { return operation; }
    public Operation getOperation() { return operation; }

    public double amount() { return amount; }
    public double getAmount() { return amount; }

    public long startAtMillis() { return startAtMillis; }
    public long getStartAtMillis() { return startAtMillis; }
    public long startTime() { return startAtMillis; }
    public long getStartTime() { return startAtMillis; }

    public long expiresAtMillis() { return expiresAtMillis; }
    public long getExpiresAtMillis() { return expiresAtMillis; }
    public long expiryTime() { return expiresAtMillis; }
    public long getExpiryTime() { return expiresAtMillis; }

    public StackPolicy stackPolicy() { return stackPolicy; }
    public StackPolicy getStackPolicy() { return stackPolicy; }

    public boolean persistent() { return persistent; }
    public boolean isPersistent() { return persistent; }

    public AppliedModifier appliedModifier() { return appliedModifier; }
    public AppliedModifier getAppliedModifier() { return appliedModifier; }

    public Object handle() { return handle; }
    public Object getHandle() { return handle; }

    public long durationMillis() {
        if (expiresAtMillis == Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.max(0L, expiresAtMillis - startAtMillis);
    }

    public boolean isExpiredAt(long nowMillis) {
        return expiresAtMillis <= nowMillis;
    }

    // ---- derived helpers ----

    public ModifierInstance withId(UUID newId) {
        return new ModifierInstance(newId, type, scope, scopeKey, source, statOrAttributeId,
                operation, amount, startAtMillis, expiresAtMillis, stackPolicy, persistent,
                appliedModifier, handle);
    }

    public ModifierInstance withScope(Scope newScope, String newScopeKey) {
        return new ModifierInstance(id, type, newScope, newScopeKey, source, statOrAttributeId,
                operation, amount, startAtMillis, expiresAtMillis, stackPolicy, persistent,
                appliedModifier, handle);
    }

    public ModifierInstance withExpiry(long newExpiresAtMillis) {
        return new ModifierInstance(id, type, scope, scopeKey, source, statOrAttributeId,
                operation, amount, startAtMillis, newExpiresAtMillis, stackPolicy, persistent,
                appliedModifier, handle);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModifierInstance other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "ModifierInstance{" + "id=" + id + ", type=" + type + ", scope=" + scope
                + ", scopeKey='" + scopeKey + '\'' + ", source='" + source + '\''
                + ", statOrAttributeId='" + statOrAttributeId + '\'' + ", operation=" + operation
                + ", amount=" + amount + ", startAtMillis=" + startAtMillis
                + ", expiresAtMillis=" + expiresAtMillis + ", stackPolicy=" + stackPolicy
                + ", persistent=" + persistent + '}';
    }

}