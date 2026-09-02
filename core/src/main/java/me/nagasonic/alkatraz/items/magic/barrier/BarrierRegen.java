package me.nagasonic.alkatraz.items.magic.barrier;

public final class BarrierRegen {

    private BarrierRegen() {}

    public record RegenResult(double hpGained, double manaSpent) {}

    /**
     * Computes one regen step (the caller decides the step size, e.g. 1s worth).
     * The barrier heals up to hpPerStep, capped by room underneath maxHp. Each HP
     * costs manaCostPerHp mana from the caster. If mana runs short, either regen is
     * clamped to what mana allows (partialOnLowMana) or stopped entirely.
     */
    public static RegenResult step(boolean enabled, boolean active, double hitpoints, double maxHp,
                                   double hpPerStep, double manaCostPerHp, double manaAvailable,
                                   boolean partialOnLowMana) {
        if (!enabled || !active) {
            return new RegenResult(0, 0);
        }
        double room = maxHp - hitpoints;
        if (room <= 0) {
            return new RegenResult(0, 0);
        }
        double desired = Math.min(hpPerStep, room);
        if (manaCostPerHp <= 0) {
            return new RegenResult(desired, 0);
        }
        if (manaAvailable <= 0) {
            return new RegenResult(0, 0);
        }
        double affordable = manaAvailable / manaCostPerHp;
        if (affordable < desired) {
            if (!partialOnLowMana) {
                return new RegenResult(0, 0);
            }
            desired = Math.max(0, affordable);
        }
        if (desired <= 0) {
            return new RegenResult(0, 0);
        }
        return new RegenResult(desired, desired * manaCostPerHp);
    }
}
