package me.nagasonic.alkatraz.spells.configuration;

import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class SyntheticGroupOption extends SpellOption{
    /**
     * The real option that drives the custom-menu click (may be {@code null}
     * if no group member declares {@code use_custom_menu: true}, in which case
     * clicking the entry does nothing useful).
     */
    private final SpellOption delegateOption;

    /**
     * Optional preview lore lines shown in {@code SpellOptionsMenu}.
     * Built by the menu from the real member options before construction.
     */
    private final List<String> previewLore;

    /**
     * @param spell          The owning spell.
     * @param groupId        The group id (becomes this option's id).
     * @param displayName    Display name shown in {@code SpellOptionsMenu}.
     *                       This is stored as the description field because
     *                       SpellOption has no separate displayName field; the
     *                       menu reads {@link #getId()} for labels but the
     *                       override {@link #getSyntheticDisplayName()} is
     *                       preferred.
     * @param icon           Icon material shown in {@code SpellOptionsMenu}.
     * @param delegateOption The group member whose custom-menu opens on click.
     * @param previewLore    Extra lore lines (already colour-formatted).
     */
    public SyntheticGroupOption(Spell spell,
                                String groupId,
                                String displayName,
                                Material icon,
                                SpellOption delegateOption,
                                List<String> previewLore) {
        super(spell, groupId, displayName, icon, 0);
        this.delegateOption = delegateOption;
        this.previewLore    = previewLore == null ? List.of() : List.copyOf(previewLore);

        // Inherit custom-menu settings from the delegate so SpellOptionsMenu
        // can call option.openCustomMenu() without special-casing this class.
        if (delegateOption != null) {
            setUseCustomMenu(delegateOption.hasCustomMenu());
            setCustomMenuClass(delegateOption.getCustomMenuClass());
        }
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /**
     * The human-readable display name for this group entry.
     * Stored in the description field of the parent for simplicity.
     */
    public String getSyntheticDisplayName() {
        return getDescription();
    }

    public SpellOption getDelegateOption() {
        return delegateOption;
    }

    public List<String> getPreviewLore() {
        return previewLore;
    }

    /**
     * A synthetic option is never "hidden" — it is the visible representative
     * of the group.
     */
    @Override
    public boolean isHidden() {
        return false;
    }

    /**
     * A synthetic option has no persisted selection; always return {@code null}.
     */
    @Override
    public String getSelectedValueId(Player player) {
        return null;
    }
}
