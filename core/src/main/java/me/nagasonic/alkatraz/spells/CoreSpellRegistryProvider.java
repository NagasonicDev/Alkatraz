package me.nagasonic.alkatraz.spells;

import me.nagasonic.alkatraz.api.SpellRegistryProvider;
import me.nagasonic.alkatraz.api.spells.Spell;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CoreSpellRegistryProvider implements SpellRegistryProvider {

    private final Map<String, Spell> apiSpells = new HashMap<>();

    @Override
    public void registerSpell(Spell spell) {
        apiSpells.put(spell.getId(), spell);
        SpellRegistry.registerApiSpell(spell);
    }

    @Nullable
    @Override
    public Spell getSpell(String id) {
        return apiSpells.get(id);
    }

    @Nullable
    @Override
    public Spell getSpellByCode(String code) {
        for (Spell spell : apiSpells.values()) {
            if (spell.getCode() != null && spell.getCode().equals(code)) {
                return spell;
            }
        }
        return null;
    }

    @Override
    public Map<String, Spell> getAllSpells() {
        return Collections.unmodifiableMap(apiSpells);
    }
}
