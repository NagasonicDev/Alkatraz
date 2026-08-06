package me.nagasonic.alkatraz.playerdata;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
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
 * done in {@link me.nagasonic.alkatraz.items.magic.listener.CastEventListener}.
 */
public class SpellHotbarManager {

    private static LangManager lang() { return Alkatraz.getLangManager(); }

    /** Slot index of the exit-casting item (rightmost hotbar slot, where the wand sits). */
    public static final int EXIT_SLOT = 8;

    /** Number of configurable spell slots (slots 0-7). */
    public static final int SPELL_SLOT_COUNT = 8;

    private static final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private static final Map<UUID, ItemStack> savedOffhand = new HashMap<>();
    private static final Map<UUID, Integer> savedHeldSlot = new HashMap<>();

    private static final Map<UUID, ItemStack> hotbarActive = new HashMap<>();
    private static final Map<UUID, Long> justEntered = new HashMap<>();
    private static final Map<UUID, Long> justExited = new HashMap<>();

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
        Long exitDeadline = justExited.get(player.getUniqueId());
        if (exitDeadline != null && System.currentTimeMillis() < exitDeadline) return;
        hotbarActive.put(player.getUniqueId(), wand);
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        profile.setCanCast(false);

        UUID uuid = player.getUniqueId();

        ItemStack[] storageContents = player.getInventory().getStorageContents().clone();
        ItemStack offhand = player.getInventory().getItemInOffHand() == null
                ? null
                : player.getInventory().getItemInOffHand().clone();
        int heldSlot = player.getInventory().getHeldItemSlot();

        savedInventories.put(uuid, storageContents);
        savedOffhand.put(uuid, offhand);
        savedHeldSlot.put(uuid, heldSlot);

        persistSnapshot(uuid, storageContents, offhand, heldSlot);

        justEntered.put(uuid, System.currentTimeMillis() + 500);

        player.getInventory().setStorageContents(new ItemStack[36]);

        // Place configured spells into slots 0-7

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
        player.getInventory().setHeldItemSlot(0);
        player.updateInventory();
        Bukkit.getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
            profile.setCanCast(true);
        }, 4L);
    }

    /**
     * Deactivates hotbar mode and restores the player's saved inventory.
     * Safe to call when not in hotbar mode.
     *
     * <p>This is the in-game exit path (e.g. right-clicking the wand). The
     * persisted backup snapshot is removed because the player stays online and
     * their inventory is authoritative again from this point on.
     *
     * @param player the player un-equipping the wand
     */
    public static void exit(Player player) {
        exit(player, true);
    }

    /**
     * Deactivates hotbar mode and restores the player's saved inventory without
     * removing the persisted backup snapshot.
     *
     * <p>Used on quit: the restored inventory is written to the player's data
     * file, but if that save does not take effect on some server versions the
     * persisted snapshot survives so the next join can still recover the
     * original inventory.
     *
     * @param player the player disconnecting
     */
    public static void exitForQuit(Player player) {
        exit(player, false);
    }

    private static void exit(Player player, boolean clearPersisted) {
        if (!hotbarActive.containsKey(player.getUniqueId())) return;
        UUID uuid = player.getUniqueId();

        hotbarActive.remove(uuid);
        justEntered.remove(uuid);
        justExited.put(uuid, System.currentTimeMillis() + 500);

        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        profile.setCanCast(true);

        restoreFromMemory(player, uuid);

        if (clearPersisted) {
            deletePersistedSnapshot(uuid);
        }
    }

    /**
     * Restores a leftover saved inventory snapshot for the given player if one exists.
     *
     * <p>Used on join as a safety net: if a player disconnected while in hotbar mode
     * and the restored inventory did not reach the player's data file, this recovers
     * their original inventory from the in-memory snapshot or from the persisted
     * backup snapshot written when they entered hotbar mode.
     *
     * @param player the player to restore
     * @return {@code true} if a snapshot was found and restored
     */
    public static boolean restoreIfNeeded(Player player) {
        UUID uuid = player.getUniqueId();
        boolean fromMemory = savedInventories.containsKey(uuid);
        boolean fromDisk = snapshotFile(uuid).exists();
        if (!fromMemory && !fromDisk) return false;

        hotbarActive.remove(uuid);
        justEntered.remove(uuid);
        justExited.remove(uuid);

        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        profile.setCanCast(true);

        if (fromMemory) {
            restoreFromMemory(player, uuid);
            deletePersistedSnapshot(uuid);
        } else {
            restoreFromPersisted(player, uuid);
        }
        return true;
    }

    private static void restoreFromMemory(Player player, UUID uuid) {
        ItemStack[] storage = savedInventories.remove(uuid);
        ItemStack offhand = savedOffhand.remove(uuid);
        Integer heldSlot = savedHeldSlot.remove(uuid);
        applySnapshot(player, storage, offhand, heldSlot);
    }

    private static void restoreFromPersisted(Player player, UUID uuid) {
        File file = snapshotFile(uuid);
        if (!file.exists()) return;
        try {
            ReadWriteNBT compound = NBT.readFile(file);
            ItemStack[] storage = compound.getItemStackArray("storage");
            ItemStack offhand = compound.hasTag("offhand") ? compound.getItemStack("offhand") : null;
            Integer heldSlot = compound.hasTag("heldSlot") ? compound.getInteger("heldSlot") : null;
            applySnapshot(player, storage, offhand, heldSlot);
        } catch (IOException e) {
            Alkatraz.getInstance().getLogger().severe("Failed to read hotbar inventory snapshot for " + uuid);
            e.printStackTrace();
        } finally {
            deletePersistedSnapshot(uuid);
        }
    }

    private static void applySnapshot(Player player, ItemStack[] storage, ItemStack offhand, Integer heldSlot) {
        if (storage != null) {
            player.getInventory().setStorageContents(storage);
        }

        player.getInventory().setItemInOffHand(offhand);

        if (heldSlot != null) {
            player.getInventory().setHeldItemSlot(heldSlot);
        }

        player.updateInventory();
    }

    private static void persistSnapshot(UUID uuid, ItemStack[] storage, ItemStack offhand, int heldSlot) {
        ReadWriteNBT compound = NBT.createNBTObject();
        compound.setItemStackArray("storage", storage);
        if (offhand != null) {
            compound.setItemStack("offhand", offhand);
        }
        compound.setInteger("heldSlot", heldSlot);
        File file = snapshotFile(uuid);
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            NBT.writeFile(file, compound);
        } catch (IOException e) {
            Alkatraz.getInstance().getLogger().severe("Failed to persist hotbar inventory snapshot for " + uuid);
            e.printStackTrace();
        }
    }

    private static void deletePersistedSnapshot(UUID uuid) {
        File file = snapshotFile(uuid);
        if (file.exists()) {
            file.delete();
        }
    }

    private static File snapshotFile(UUID uuid) {
        return new File(
                new File(Alkatraz.getInstance().getDataFolder().getParentFile(), "Alkatraz/playerdata/" + uuid),
                "hotbar_snapshot.nbt");
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
     * Returns the saved storage contents for the given player without removing them,
     * or {@code null} if no inventory is saved.
     */
    public static ItemStack[] peekSavedContents(UUID uuid) {
        return savedInventories.get(uuid);
    }

    /**
     * Returns the saved offhand item for the given player without removing it,
     * or {@code null} if nothing is saved.
     */
    public static ItemStack peekSavedOffhand(UUID uuid) {
        return savedOffhand.get(uuid);
    }

    /**
     * Returns the saved held slot for the given player without removing it,
     * or {@code null} if nothing is saved.
     */
    public static Integer peekSavedHeldSlot(UUID uuid) {
        return savedHeldSlot.get(uuid);
    }

    /**
     * Restores a previously saved inventory snapshot into the player's live
     * inventory. Used by the death handler to keep the original items on
     * keep-inventory servers.
     */
    public static void restoreSnapshotToInventory(Player player, ItemStack[] storage, ItemStack offhand, Integer heldSlot) {
        applySnapshot(player, storage, offhand, heldSlot);
    }

    /**
     * Returns true if the player entered hotbar mode within the last 500ms.
     * Used to prevent the entry click from immediately triggering exit.
     */
    public static boolean isJustEntered(Player player) {
        Long deadline = justEntered.get(player.getUniqueId());
        return deadline != null && System.currentTimeMillis() < deadline;
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

    /**
     * Cleans up hotbar state for a dying player without restoring inventory.
     * Used by the death handler so the saved inventory can be dropped via
     * the event's drops list instead of being restored to the (about-to-be-
     * cleared) player inventory.
     */
    public static void cleanupForDeath(Player player) {
        UUID uuid = player.getUniqueId();
        if (!hotbarActive.containsKey(uuid)) return;

        hotbarActive.remove(uuid);
        justEntered.remove(uuid);
        justExited.put(uuid, System.currentTimeMillis() + 500);

        savedInventories.remove(uuid);
        savedOffhand.remove(uuid);
        savedHeldSlot.remove(uuid);

        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        profile.setCanCast(true);

        deletePersistedSnapshot(uuid);
    }

    /**
     * Exits hotbar mode for all currently active players.
     * Called on plugin disable to ensure no inventories are left in a broken state.
     */
    public static void exitAll() {
        for (UUID uuid : List.copyOf(hotbarActive.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                exit(player);
            }
        }
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
        meta.setDisplayName(lang().get("hotbar.slot_empty", "slot", String.valueOf(slotNumber)));
        List<String> lore = new ArrayList<>();
        lore.add(lang().get("hotbar.slot_empty_lore"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
