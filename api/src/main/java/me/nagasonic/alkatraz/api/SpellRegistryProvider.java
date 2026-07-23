package me.nagasonic.alkatraz.api;

import me.nagasonic.alkatraz.api.spells.Spell;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * SPI (Service Provider Interface) for the spell registry.
 * Implementations provide storage and lookup for registered spells.
 */
public interface SpellRegistryProvider {

    /**
     * Registers a spell in the registry.
     *
     * @param spell the spell to register
     */
    void registerSpell(Spell spell);

    /**
     * Retrieves a spell by its unique identifier.
     *
     * @param id the spell's unique identifier
     * @return the spell, or {@code null} if no spell is registered with that ID
     */
    @Nullable
    default Spell getSpell(String id) {
        return null;
    }

    /**
     * Retrieves a spell by its short code.
     *
     * @param code the spell's short code
     * @return the spell, or {@code null} if no spell is registered with that code
     */
    @Nullable
    default Spell getSpellByCode(String code) {
        return null;
    }

    /**
     * Returns all registered spells as an unmodifiable map of spell ID to spell.
     *
     * @return an unmodifiable map of all registered spells
     */
    default Map<String, Spell> getAllSpells() {
        return Map.of();
    }
}
