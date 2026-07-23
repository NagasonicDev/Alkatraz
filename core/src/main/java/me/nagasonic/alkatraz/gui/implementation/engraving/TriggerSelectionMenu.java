package me.nagasonic.alkatraz.gui.implementation.engraving;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.instance.Engraving;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerType;
import me.nagasonic.alkatraz.gui.PagedMenu;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.items.magic.util.ItemTypeMapper;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.StatUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class TriggerSelectionMenu extends PagedMenu<TriggerType> {

    private static final int[] CONTENT_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private static me.nagasonic.alkatraz.lang.LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public TriggerSelectionMenu(Player viewer) {
        super(viewer,
                ColorFormat.format(lang().get("menu.trigger_select")),
                54,
                getFilteredTriggers(viewer),
                14);
        this.contentSlots = CONTENT_SLOTS;
        this.nextPageSlot = 53;
        this.previousPageSlot = 45;
        this.backButtonSlot = 49;
    }

    private static List<TriggerType> getFilteredTriggers(Player viewer) {
        EngravingSession session = EngravingSession.get(viewer.getUniqueId());
        if (session == null) return List.of();

        Set<String> itemTypes = ItemTypeMapper.getTypes(session.targetStack().getType());

        return MagicItemRegistries.TRIGGER_TYPES.values().stream()
                .filter(t -> t.allowedItemTypes().isEmpty()
                        || t.allowedItemTypes().stream().anyMatch(itemTypes::contains))
                .collect(Collectors.toList());
    }

    @Override
    protected void addDecorations() {
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, createBorderPane());
            inventory.setItem(i + 45, createBorderPane());
        }
        for (int i = 9; i < 18; i++) {
            inventory.setItem(i, createBorderPane());
        }
        for (int i = 36; i < 45; i++) {
            inventory.setItem(i, createBorderPane());
        }
    }

    private ItemStack createBorderPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format("&7"));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    protected ItemStack createDisplayItem(TriggerType trigger, int index) {
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(lang().get("engraving.trigger_prefix", "trigger", trigger.getKey().getKey()));
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&7" + trigger.description()));
        lore.add("");
        lore.add(ColorFormat.format("&eClick to select this trigger"));
        meta.setLore(lore);
        item.setItemMeta(meta);

        setMenuData(item, "trigger_key", MagicKeys.format(trigger.getKey()));
        return item;
    }

    @Override
    protected void handleContentClick(TriggerType trigger, InventoryClickEvent event) {
        EngravingSession session = EngravingSession.get(viewer.getUniqueId());
        if (session == null) return;

        session.setSelectedTriggerKey(trigger.getKey());

        // Check engraving slot limit
        int currentEngravings = session.targetInstance().engravings().size();
        Object rawMax = session.targetDefinition().staticConfig().get("max_engravings");
        int maxEngravings = rawMax != null ? Integer.parseInt(rawMax.toString()) : 1;
        if (currentEngravings >= maxEngravings) {
            viewer.sendMessage(ColorFormat.format("&cEngraving slots are full!"));
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Calculate mana cost: 100 for first engraving, +50 per additional
        int manaCost = 100 + (currentEngravings * 50);

        // Check player has enough mana
        MagicProfile profile = ProfileManager.getProfile(viewer.getUniqueId(), MagicProfile.class);
        if (profile == null) return;

        if (profile.getMana() < manaCost) {
            viewer.sendMessage(ColorFormat.format("&cNot enough mana! Need &b" + manaCost + " &cmana, you have &b" + (int) profile.getMana()));
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Deduct mana
        StatUtils.subMana(viewer, manaCost);

        // Consume one rune from the player's inventory
        NamespacedKey targetKey = session.selectedEngravingKey();
        if (targetKey != null) {
            for (int i = 0; i <= 35; i++) {
                ItemStack invItem = viewer.getInventory().getItem(i);
                if (invItem == null || invItem.getType() == Material.AIR) continue;
                Optional<NamespacedKey> key = MagicItemStack.readEngravingKey(invItem);
                if (key.isPresent() && key.get().equals(targetKey)) {
                    int amount = invItem.getAmount() - 1;
                    if (amount <= 0) {
                        viewer.getInventory().setItem(i, null);
                    } else {
                        invItem.setAmount(amount);
                    }
                    break;
                }
            }
        }

        // Apply engraving
        Engraving engraving = new Engraving(session.selectedEngravingKey(), session.selectedTriggerKey());
        session.targetInstance().addEngraving(engraving);
        MagicItemStack.writeInstance(session.targetStack(), session.targetInstance());

        viewer.sendMessage(ColorFormat.format("&aInstalled engraving: &f"
                + session.selectedEngravingKey().getKey() + " &8(" + trigger.getKey().getKey() + ")"));
        viewer.sendMessage(ColorFormat.format("&aUsed &b" + manaCost + " &amana."));
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);

        EngravingSession.remove(viewer.getUniqueId());
        new EngravingTableMenu(viewer, session.targetStack(), session.targetInstance(), session.targetDefinition()).open();
    }

    @Override
    protected void addBackButton() {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta meta = back.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format(lang().get("common.back")));
            back.setItemMeta(meta);
        }
        setMenuData(back, "action", "back");
        inventory.setItem(backButtonSlot, back);
    }

    @Override
    protected void handleBackClick() {
        EngravingSession session = EngravingSession.get(viewer.getUniqueId());
        if (session == null) {
            close();
            return;
        }
        new EngravingTableMenu(viewer, session.targetStack(), session.targetInstance(), session.targetDefinition()).open();
    }
}