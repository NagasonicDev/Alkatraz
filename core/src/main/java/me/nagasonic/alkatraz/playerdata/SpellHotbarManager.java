package me.nagasonic.alkatraz.playerdata;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Manages the spell hotbar mode that activates when a player equips a wand.
 *
 * <p>On entering hotbar mode:
 * <ul>
 *   <li>The entire player inventory (slots 0-35) is saved and cleared.</li>
 *   <li>The wand is placed back into slot 8 (the rightmost hotbar slot).</li>
 *   <li>Slots 0-7 are filled with the player's configured hotbar spells.</li>
 * </ul>
 *
 * <p>On exiting hotbar mode the saved inventory is restored exactly as it was.
 *
 * <p>Inventory interaction is fully blocked while in hotbar mode; enforcement is
 * done in {@link me.nagasonic.alkatraz.items.wands.WandHotbarListeners}.
 */
public class SpellHotbarManager {

    /** Slot index of the exit-casting item (rightmost hotbar slot, where the wand sits). */
    public static final int EXIT_SLOT = 8;

    /** Number of configurable spell slots (slots 0-7). */
    public static final int SPELL_SLOT_COUNT = 8;

    private static final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private static final Map<UUID, ItemStack> savedOffhand = new HashMap<>();
    private static final Map<UUID, Integer> savedHeldSlot = new HashMap<>();

    private static final Map<UUID, ItemStack> hotbarActive = new HashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Activates hotbar mode for the given player.
     * Safe to call multiple times — will not double-activate.
     *
     * @param player the player equipping the wand
     * @param wand   the wand ItemStack (placed into slot 8)
     */
    public static void enter(Player player, ItemStack wand) {
        if (hotbarActive.containsKey(player.getUniqueId())) return;
        hotbarActive.put(player.getUniqueId(), wand);

        UUID uuid = player.getUniqueId();

        savedInventories.put(
                uuid,
                player.getInventory().getStorageContents().clone()
        );

        savedOffhand.put(
                uuid,
                player.getInventory().getItemInOffHand() == null
                        ? null
                        : player.getInventory().getItemInOffHand().clone()
        );

        savedHeldSlot.put(
                uuid,
                player.getInventory().getHeldItemSlot()
        );

        player.getInventory().setStorageContents(new ItemStack[36]);

        // Place configured spells into slots 0-7
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        for (int slot = 0; slot < SPELL_SLOT_COUNT; slot++) {
            String spellId = profile.getHotbarSpellIds().get(slot);
            Spell spell = SpellRegistry.getSpell(spellId);
            if (spell != null) {
                player.getInventory().setItem(slot, buildSpellItem(spell));
                continue;
            }
            // Empty configured slot
            player.getInventory().setItem(slot, buildEmptySlotItem(slot + 1));
        }

        // Place the wand in slot 8
        player.getInventory().setItem(EXIT_SLOT, wand != null ? wand.clone() : null);
        player.updateInventory();
    }

    /**
     * Deactivates hotbar mode and restores the player's saved inventory.
     * Safe to call when not in hotbar mode.
     *
     * @param player the player un-equipping the wand
     */
    public static void exit(Player player) {
        if (!hotbarActive.containsKey(player.getUniqueId())) return;
        UUID uuid = player.getUniqueId();

        hotbarActive.remove(uuid);

        ItemStack[] storage = savedInventories.remove(uuid);
        ItemStack offhand = savedOffhand.remove(uuid);
        Integer heldSlot = savedHeldSlot.remove(uuid);

        if (storage != null) {
            player.getInventory().setStorageContents(storage);
        }

        player.getInventory().setItemInOffHand(offhand);

        if (heldSlot != null) {
            player.getInventory().setHeldItemSlot(heldSlot);
        }

        player.updateInventory();
    }

    /**
     * Returns {@code true} if the player is currently in hotbar mode.
     */
    public static boolean isActive(Player player) {
        return hotbarActive.containsKey(player.getUniqueId());
    }

    /**
     * Returns the wand of the active player.
     */
    public static ItemStack getWand(Player player) {
        return hotbarActive.get(player.getUniqueId());
    }

    /**
     * Refreshes the hotbar spell items without touching the saved snapshot.
     * Call this after the player reconfigures their hotbar spells.
     */
    public static void refresh(Player player) {
        if (!isActive(player)) return;

        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        for (int slot = 0; slot < SPELL_SLOT_COUNT; slot++) {
            String spellId = profile.getHotbarSpellIds().get(slot);
            Spell spell = SpellRegistry.getSpell(spellId);
            if (spell != null) {
                player.getInventory().setItem(slot, buildSpellItem(spell));
                continue;
            }
            player.getInventory().setItem(slot, buildEmptySlotItem(slot + 1));
        }
        player.updateInventory();
    }

    // -------------------------------------------------------------------------
    // Item builders
    // -------------------------------------------------------------------------

    /**
     * Builds the hotbar display item for a spell.
     * Shows name, element, cast time, mana cost and cooldown — kept minimal
     * so players can read it at a glance while in combat.
     */
    public static ItemStack buildSpellItem(Spell spell) {
        ItemStack item = spell.getGuiItem().clone();
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ColorFormat.format(spell.getDisplayName()));

        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7" + spell.getElement().getName()));
        lore.add(ColorFormat.format("&bMana Cost: &f" + spell.getCost()));
        lore.add(ColorFormat.format("&bCast Time: &f" + spell.getCastTime() + "s"));
        lore.add(ColorFormat.format("&bCooldown: &f" + spell.getCooldown() + "s"));
        meta.setLore(lore);

        item.setItemMeta(meta);
        NBT.modify(item, nbt -> {
            nbt.setString("spell_id", spell.getId());
        });
        return item;
    }

    /** Placeholder item shown in unconfigured hotbar slots. */
    private static ItemStack buildEmptySlotItem(int slotNumber) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorFormat.format("&8Spell Slot " + slotNumber + " &7(empty)"));
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7Configure in the Spells menu."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
