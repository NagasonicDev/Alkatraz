package me.nagasonic.alkatraz.spells.spellbooks;

import me.nagasonic.alkatraz.config.SpellbookConfig;
import me.nagasonic.alkatraz.items.magic.MagicItemServices;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SpellbookVillagerListener implements Listener {

    @EventHandler
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {
        if (!SpellbookConfig.isVillagerTradingEnabled()) return;
        if (event.getProfession() != Villager.Profession.LIBRARIAN) return;

        Villager villager = event.getEntity();

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        int tradesToAdd;
        int roll = rng.nextInt(100);
        int twoThreshold = SpellbookConfig.getVillagerChanceTwoTrades();
        int oneThreshold = twoThreshold + SpellbookConfig.getVillagerChanceOneTrade();

        if (roll < twoThreshold) {
            tradesToAdd = 2;
        } else if (roll < oneThreshold) {
            tradesToAdd = 1;
        } else {
            tradesToAdd = 0;
        }

        int maxTrades = SpellbookConfig.getVillagerMaxTrades();
        tradesToAdd = Math.min(tradesToAdd, maxTrades);

        if (tradesToAdd == 0) return;

        List<MerchantRecipe> newTrades = new ArrayList<>();
        for (int i = 0; i < tradesToAdd; i++) {
            int tier = rng.nextInt(1, 6);
            MerchantRecipe recipe = createTradeForTier(tier, rng);
            if (recipe != null) {
                newTrades.add(recipe);
            }
        }

        if (newTrades.size() < maxTrades) {
            MerchantRecipe grimoireRecipe = createGrimoireTrade(rng);
            if (grimoireRecipe != null) {
                newTrades.add(grimoireRecipe);
            }
        }

        if (!newTrades.isEmpty()) {
            List<MerchantRecipe> existing = new ArrayList<>(villager.getRecipes());
            existing.addAll(newTrades);
            villager.setRecipes(existing);
        }
    }

    private MerchantRecipe createTradeForTier(int tier, ThreadLocalRandom rng) {
        ItemStack spellbook;
        int[] costs = SpellbookConfig.getVillagerTierCosts(tier);

        switch (tier) {
            case 1 -> spellbook = SpellbookFactory.createRandomSpellBook(1);
            case 2 -> spellbook = SpellbookFactory.createRandomSpellBook(1, 2);
            case 3 -> spellbook = SpellbookFactory.createRandomSpellBook(2, 3);
            case 4 -> spellbook = SpellbookFactory.createRandomSpellBook(3, 4);
            case 5 -> spellbook = SpellbookFactory.createRandomSpellBook(4, 5);
            default -> spellbook = SpellbookFactory.createRandomSpellBook(1);
        }

        int emeraldCost = rng.nextInt(costs[0], costs[1] + 1);
        int magicStoneCost = rng.nextInt(costs[2], costs[3] + 1);

        ItemStack emeralds = new ItemStack(Material.EMERALD, emeraldCost);
        ItemStack magicStone = MagicItemServices.get().createItem(MagicKeys.alkatraz("magic_stone"));
        if (magicStone == null) return null;
        magicStone.setAmount(magicStoneCost);

        MerchantRecipe recipe = new MerchantRecipe(spellbook, SpellbookConfig.getVillagerTradeMaxUses());
        recipe.addIngredient(emeralds);
        recipe.addIngredient(magicStone);
        return recipe;
    }

    private MerchantRecipe createGrimoireTrade(ThreadLocalRandom rng) {
        String[] grimoireKeys = {
            "leather_grimoire", "runic_grimoire", "blaze_grimoire", "glacier_grimoire",
            "mountain_grimoire", "storm_grimoire", "luminous_grimoire", "void_grimoire"
        };
        
        String grimoireKey = grimoireKeys[rng.nextInt(grimoireKeys.length)];
        ItemStack grimoire = MagicItemServices.get().createItem(MagicKeys.alkatraz(grimoireKey));
        if (grimoire == null) return null;

        int[] costs = SpellbookConfig.getVillagerTierCosts(3);
        int emeraldCost = rng.nextInt(costs[0], costs[1] + 1);
        int magicStoneCost = rng.nextInt(costs[2], costs[3] + 1);

        ItemStack emeralds = new ItemStack(Material.EMERALD, emeraldCost);
        ItemStack magicStone = MagicItemServices.get().createItem(MagicKeys.alkatraz("magic_stone"));
        if (magicStone == null) return null;
        magicStone.setAmount(magicStoneCost);

        MerchantRecipe recipe = new MerchantRecipe(grimoire, SpellbookConfig.getVillagerTradeMaxUses());
        recipe.addIngredient(emeralds);
        recipe.addIngredient(magicStone);
        return recipe;
    }
}
