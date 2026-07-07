package me.nagasonic.alkatraz.api;

import me.nagasonic.alkatraz.api.spells.Spell;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface SpellRegistryProvider {

    void registerSpell(Spell spell);

    @Nullable
    default Spell getSpell(String id) {
        return null;
    }

    @Nullable
    default Spell getSpellByCode(String code) {
        return null;
    }

    default Map<String, Spell> getAllSpells() {
        return Map.of();
    }
}
