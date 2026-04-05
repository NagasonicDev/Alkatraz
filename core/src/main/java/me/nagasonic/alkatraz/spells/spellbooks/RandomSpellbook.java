package me.nagasonic.alkatraz.spells.spellbooks;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * A spellbook that randomly selects a spell from a weighted list
 * 
 * Features:
 * - Weighted random selection
 * - Transforms into regular spellbook on use
 * - Visual animation during transformation
 * - Easy to create with builder pattern
 */
public class RandomSpellbook {
    
    private Map<String, Double> weightedSpells; // spell_id -> weight
    private String displayName;
    private List<String> lore;
    private Material material;
    
    /**
     * Creates a new random spellbook
     */
    public RandomSpellbook(String name) {
        this.weightedSpells = new HashMap<>();
        this.material = Material.ENCHANTED_BOOK;
        this.displayName = name;
        this.lore = new ArrayList<>();
        generateDefaultLore();
    }
    
    /**
     * Creates a random spellbook with a category
     */
    public RandomSpellbook(String name, Map<String, Double> spells) {
        this(name);
        this.weightedSpells = spells;
        generateDefaultLore();
    }
    
    /**
     * Generates default lore
     */
    private void generateDefaultLore() {
        lore.clear();
        lore.add("&7This mysterious tome contains");
        lore.add("&7unknown magical knowledge...");
        lore.add("");
        lore.add("&dRight-click to reveal a random spell!");
    }
    
    /**
     * Adds a spell with a weight
     * Higher weight = more likely to be selected
     * 
     * @param spellId The spell ID
     * @param weight The weight (1-100 recommended)
     */
    public RandomSpellbook addSpell(String spellId, double weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be positive!");
        }
        weightedSpells.put(spellId, weight);
        return this;
    }
    
    /**
     * Adds multiple spells with equal weight
     */
    public RandomSpellbook addSpells(int weight, String... spellIds) {
        for (String spellId : spellIds) {
            addSpell(spellId, weight);
        }
        return this;
    }
    
    /**
     * Sets custom display name
     */
    public RandomSpellbook setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }
    
    /**
     * Sets custom lore (replaces default)
     */
    public RandomSpellbook setLore(List<String> lore) {
        this.lore = new ArrayList<>(lore);
        return this;
    }
    
    /**
     * Adds a line to the lore
     */
    public RandomSpellbook addLoreLine(String line) {
        this.lore.add(line);
        return this;
    }
    
    /**
     * Sets the material
     */
    public RandomSpellbook setMaterial(Material material) {
        this.material = material;
        return this;
    }
    
    /**
     * Builds the ItemStack for this random spellbook
     */
    public ItemStack build() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ColorFormat.format(displayName));
            
            // Build lore with spell list
            List<String> finalLore = new ArrayList<>();
            
            // Add custom lore
            for (String line : lore) {
                finalLore.add(ColorFormat.format(line));
            }
            
            // Add possible spells section
            if (!weightedSpells.isEmpty()) {
                finalLore.add("");
                finalLore.add(ColorFormat.format("&7Possible spells:"));
                
                // Sort by weight (highest first)
                List<Map.Entry<String, Double>> sorted = new ArrayList<>(weightedSpells.entrySet());
                sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
                
                for (Map.Entry<String, Double> entry : sorted) {
                    Spell spell = SpellRegistry.getSpell(entry.getKey());
                    if (spell != null) {
                        finalLore.add(ColorFormat.format("&e- " + getRarity(spell.getId()) + " " + spell.getDisplayName()));
                    }
                }
            }
            
            meta.setLore(finalLore);
            item.setItemMeta(meta);
        }
        
        // Add NBT data
        NBT.modify(item, nbt -> {
            nbt.setString("spellbook_type", "random");
            
            // Store weighted spells
            int index = 0;
            for (Map.Entry<String, Double> entry : weightedSpells.entrySet()) {
                nbt.setString("spell_" + index, entry.getKey());
                nbt.setDouble("weight_" + index, entry.getValue());
                index++;
            }
            nbt.setInteger("spell_count", weightedSpells.size());
        });
        
        return item;
    }

    /**
     * Determines rarity display based on weight
     */
    private String getRarity(String spell) {
        Map<String, Double> percentages = new HashMap<>();

        // Calculate total weight
        double totalWeight = 0;
        for (double weight : weightedSpells.values()) {
            totalWeight += weight;
        }

        // Convert each weight to percentage
        for (Map.Entry<String, Double> entry : weightedSpells.entrySet()) {
            double percent = (entry.getValue() / totalWeight) * 100.0;
            percentages.put(entry.getKey(), percent);
        }
        return Utils.getDecimalFormat(2).format(percentages.get(spell));
    }
    
    /**
     * Uses the random spellbook and transforms it
     * 
     * @param player The player using it
     * @param item The random spellbook item
     */
    public static void use(Player player, ItemStack item) {
        if (!isRandomSpellbook(item)) return;
        
        // Load weighted spells from NBT
        Map<String, Double> weightedSpells = new HashMap<>();
        Integer spellCount = NBT.get(item, nbt -> (Integer) nbt.getInteger("spell_count"));
        
        if (spellCount == null || spellCount == 0) {
            player.sendMessage(ColorFormat.format("&cThis spellbook appears to be empty!"));
            return;
        }
        
        for (int i = 0; i < spellCount; i++) {
            final int index = i;
            String spellId = NBT.get(item, nbt -> (String) nbt.getString("spell_" + index));
            Double weight = NBT.get(item, nbt -> (Double) nbt.getDouble("weight_" + index));
            
            if (spellId != null && weight != null) {
                weightedSpells.put(spellId, weight);
            }
        }
        
        if (weightedSpells.isEmpty()) {
            player.sendMessage(ColorFormat.format("&cThis spellbook appears to be corrupted!"));
            return;
        }
        
        // Select random spell based on weights
        String selectedSpellId = selectWeightedRandom(weightedSpells);
        
        if (selectedSpellId == null) {
            player.sendMessage(ColorFormat.format("&cFailed to select a spell!"));
            return;
        }
        
        Spell selectedSpell = SpellRegistry.getSpell(selectedSpellId);
        if (selectedSpell == null) {
            player.sendMessage(ColorFormat.format("&cSelected spell not found!"));
            return;
        }
        player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
        
        // Play transformation animation
        playTransformationAnimation(player, selectedSpell, () -> {
            
            // Replace item in player's hand
            player.getInventory().addItem(selectedSpell.getSpellBook());
            
            // Message
            player.sendMessage(ColorFormat.format("&aThe spellbook transforms into " + selectedSpell.getDisplayName() + "&a!"));
        });
    }
    
    /**
     * Selects a random spell based on weights
     */
    private static String selectWeightedRandom(Map<String, Double> weightedSpells) {
        // Calculate total weight
        double totalWeight = 0;
        for (double weight : weightedSpells.values()) {
            totalWeight += weight;
        }
        
        // Select random value
        Random random = new Random();
        double randomValue = random.nextDouble(totalWeight);
        
        // Find selected spell
        int currentWeight = 0;
        for (Map.Entry<String, Double> entry : weightedSpells.entrySet()) {
            currentWeight += entry.getValue();
            if (randomValue < currentWeight) {
                return entry.getKey();
            }
        }
        
        // Fallback (shouldn't happen)
        return weightedSpells.keySet().iterator().next();
    }
    
    /**
     * Plays transformation animation
     */
    private static void playTransformationAnimation(Player player, Spell spell, Runnable onComplete) {
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 40; // 2 seconds
            
            @Override
            public void run() {
                if (!player.isOnline() || ticks >= maxTicks) {
                    if (player.isOnline() && ticks >= maxTicks) {
                        onComplete.run();
                    }
                    cancel();
                    return;
                }
                
                org.bukkit.Location loc = player.getLocation().add(0, 1.5, 0);
                
                // Swirling particles
                double radius = 0.5 + (ticks / (double) maxTicks) * 0.5;
                for (int i = 0; i < 4; i++) {
                    double angle = (ticks * 0.5) + (i * Math.PI / 2);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    
                    org.bukkit.Location particleLoc = loc.clone().add(x, 0, z);
                    loc.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, particleLoc, 3, 0, 0, 0, 0.1);
                }
                
                // Sound effects
                if (ticks % 8 == 0) {
                    loc.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.0f + (ticks / (float) maxTicks) * 0.5f);
                }
                
                if (ticks == maxTicks - 1) {
                    // Final flash
                    loc.getWorld().spawnParticle(org.bukkit.Particle.FLASH, loc, 1, 0, 0, 0, Color.WHITE);
                    loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                }
                
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }
    
    /**
     * Checks if an ItemStack is a random spellbook
     */
    public static boolean isRandomSpellbook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        
        String type = NBT.get(item, nbt -> (String) nbt.getString("spellbook_type"));
        return "random".equals(type);
    }
    
    /**
     * Helper to capitalize first letter
     */
    private static String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
