package me.nagasonic.alkatraz.spells.modifier;

import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.OptionValue;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.impact.ValueImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.CastModifierImpact;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collects {@link AppliedModifier} instances from grouped slot options for any spell.
 */
public final class CastModifierCollector {

    private CastModifierCollector() {}

    /**
     * Gathers cast modifiers from all slot options in a group.
     */
    public static List<AppliedModifier> fromGroup(Spell spell, Player player, String groupId) {
        List<SpellOption> slots = spell.getAllOptions().values().stream()
                .filter(o -> groupId.equals(o.getGroup()))
                .filter(o -> o.getRole() == SpellOption.OptionRole.SLOT)
                .sorted(Comparator.comparingInt(SpellOption::getSlotIndex))
                .toList();

        return fromSlotOptions(player, slots);
    }

    /**
     * Gathers cast modifiers from an explicit list of slot options.
     */
    public static List<AppliedModifier> fromSlotOptions(Player player, List<SpellOption> slotOptions) {
        List<AppliedModifier> modifiers = new ArrayList<>();

        for (SpellOption slot : slotOptions) {
            String selectedId = slot.getSelectedValueId(player);
            if (selectedId == null || "none".equals(selectedId)) continue;

            slot.getValueById(selectedId).ifPresent(value ->
                    modifiers.addAll(modifiersFromValue(value)));
        }

        return modifiers;
    }

    public static List<AppliedModifier> modifiersFromValue(OptionValue<?> value) {
        List<AppliedModifier> modifiers = new ArrayList<>();
        for (ValueImpact impact : value.getImpacts()) {
            if (impact instanceof CastModifierImpact castImpact) {
                modifiers.add(castImpact.getModifier());
            }
        }
        return modifiers;
    }
}
