package me.nagasonic.alkatraz.spells.spellbooks;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.ParticleCaster;
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
    
    // LinkedHashMap, not HashMap: build() writes spell_<i>/weight_<i> in iteration
    // order, so a deterministic order makes two builds of the same book byte-identical.
    // Not final: both constructors below assign it.
    private Map<String, Double> weightedSpells; // spell_id -> weight
    private String displayName;
    private List<String> lore;
    private Material material;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }
    
    /**
     * Creates a new random spellbook
     */
    public RandomSpellbook(String name) {
        this.weightedSpells = new LinkedHashMap<>();
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
        this.weightedSpells = new LinkedHashMap<>(spells);
        generateDefaultLore();
    }
    
    /**
     * Generates default lore
     */
    private void generateDefaultLore() {
        lore.clear();
        lore.add(lang().get("spellbook.random_lore_line1"));
        lore.add(lang().get("spellbook.random_lore_line2"));
        lore.add("");
        lore.add(lang().get("spellbook.random_lore_line3"));
    }
    
    /**
     * Adds a spell with a weight. Higher weight = more likely to be selected.
     *
     * <p>A weight of zero or less is IGNORED rather than rejected. The old
     * implementation threw IllegalArgumentException, which meant a circle whose
     * spellbook.circle_weights key was missing or zero - circle 0 always, since
     * SpellbookConfig.getCircleWeight returns 0 outside 1-9 - took down plugin
     * enable from inside registerAll(). A spell that cannot be rolled is simply not
     * in the book; that is not a caller bug worth a stack trace at startup.
     */
    public RandomSpellbook addSpell(String spellId, double weight) {
        if (spellId == null || spellId.isBlank()) return this;
        if (weight <= 0) return this;
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
            
            List<String> finalLore = new ArrayList<>();
            
            for (String line : lore) {
                finalLore.add(ColorFormat.format(line));
            }
            
            if (!weightedSpells.isEmpty()) {
                finalLore.add("");
                
                List<String> sortedSpellIds = new ArrayList<>(weightedSpells.keySet());
                sortedSpellIds.sort((a, b) -> {
                    Spell sa = SpellRegistry.getSpell(a);
                    Spell sb = SpellRegistry.getSpell(b);
                    if (sa == null || sb == null) return 0;
                    return Integer.compare(sa.getRequiredCircleLevel(), sb.getRequiredCircleLevel());
                });
                
                StringBuilder currentLine = new StringBuilder();
                int count = 0;
                for (String spellId : sortedSpellIds) {
                    Spell spell = SpellRegistry.getSpell(spellId);
                    if (spell == null) continue;
                    
                    String elementColor = spell.getElement().getColor();
                    String cleanName = stripColorCodes(spell.getDisplayName());
                    
                    if (count > 0) {
                        currentLine.append(" &7| ");
                    }
                    currentLine.append(elementColor).append(cleanName);
                    count++;
                    
                    if (count >= 3) {
                        finalLore.add(ColorFormat.format(currentLine.toString()));
                        currentLine = new StringBuilder();
                        count = 0;
                    }
                }
                if (count > 0) {
                    finalLore.add(ColorFormat.format(currentLine.toString()));
                }
            }
            
            meta.setLore(finalLore);
            item.setItemMeta(meta);
        }
        
        NBT.modify(item, nbt -> {
            nbt.setString("spellbook_type", "random");
            
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

    private static String stripColorCodes(String str) {
        if (str == null) return "";
        return str.replaceAll("&[0-9a-fk-orA-FK-OR]", "")
                  .replaceAll("§[0-9a-fk-orA-FK-OR]", "")
                  .replaceAll("#[0-9a-fA-F]{6}", "")
                  .trim();
    }
    
    /**
     * Uses the random spellbook and transforms it
     * 
     * @param player The player using it
     * @param item The random spellbook item
     */
    public static void use(Player player, ItemStack item) {
        use(player, item, () -> {});
    }

    public static void use(Player player, ItemStack item, Runnable onComplete) {
        if (!isRandomSpellbook(item)) {
            onComplete.run();
            return;
        }
        
        // Load weighted spells from NBT. LinkedHashMap, not HashMap: spell_<i> was
        // written in a deterministic order by build(), so reading it back the same
        // way keeps selectWeightedRandom's walk reproducible.
        Map<String, Double> weightedSpells = new LinkedHashMap<>();
        Integer spellCount = NBT.get(item, nbt -> (Integer) nbt.getInteger("spell_count"));
        
        if (spellCount == null || spellCount == 0) {
            player.sendMessage(ColorFormat.format(lang().get("spellbook.empty")));
            onComplete.run();
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
            player.sendMessage(ColorFormat.format(lang().get("spellbook.corrupted")));
            onComplete.run();
            return;
        }
        
        // Select random spell based on weights
        String selectedSpellId = selectWeightedRandom(weightedSpells);
        
        if (selectedSpellId == null) {
            player.sendMessage(ColorFormat.format(lang().get("spellbook.selection_failed")));
            onComplete.run();
            return;
        }
        
        Spell selectedSpell = SpellRegistry.getSpell(selectedSpellId);
        if (selectedSpell == null) {
            player.sendMessage(ColorFormat.format(lang().get("spellbook.selection_not_found")));
            onComplete.run();
            return;
        }

        // Consume the spellbook immediately before animation
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem != null && isRandomSpellbook(handItem) && handItem.getAmount() > 0) {
            handItem.setAmount(handItem.getAmount() - 1);
        }

        // Play transformation animation
        playTransformationAnimation(player, selectedSpell, () -> {
            // Give the player the regular spellbook
            player.getInventory().addItem(selectedSpell.getSpellBook());

            // Message
            player.sendMessage(ColorFormat.format(lang().get("spellbook.transformed", "spell", selectedSpell.getDisplayName())));
            onComplete.run();
        });
    }
    
    /**
     * Selects a random spell based on weights.
     *
     * <p>The accumulator is a {@code double} and was an {@code int}. Every weight
     * used to be a whole number - {@code SpellbookConfig.getCircleWeight} returns an
     * {@code int} - so the truncation was exactly zero and the bug was latent. Now
     * that {@code SpellbookFactory} divides a circle's weight by its bucket size,
     * weights are fractional ({@code 375/7 = 53.571...}, {@code 30/7 = 4.285...}).
     * <p>Because {@code int += double} is a compound assignment, each step was
     * {@code trunc(I + p) = I + floor(p)}, so the walk could only ever reach
     * {@code sum(floor(p))} - short of the true total by {@code sum(frac(p)) =
     * sum(w(circle) mod count(circle))}. {@code randomValue} is drawn against the
     * true total, so that shortfall fell through to the {@code keySet().iterator()
     * .next()} fallback: 2.22% of uses for the {@code (6,7,8,9)} end-city book and
     * 3.92% for the {@code (7,8,9)} dragon and wither books. The bias is not by
     * circle - every entry but the first in iteration order is short by exactly
     * {@code frac(p)}, and the whole shortfall lands on that one entry. See the
     * per-band table at the top of this wave for every figure.
     */
    private static String selectWeightedRandom(Map<String, Double> weightedSpells) {
        double totalWeight = 0;
        for (double weight : weightedSpells.values()) {
            totalWeight += weight;
        }
        if (totalWeight <= 0) return null;

        Random random = new Random();
        double randomValue = random.nextDouble() * totalWeight;

        double currentWeight = 0;
        for (Map.Entry<String, Double> entry : weightedSpells.entrySet()) {
            currentWeight += entry.getValue();
            if (randomValue < currentWeight) {
                return entry.getKey();
            }
        }

        // Floating-point accumulation can leave randomValue a hair above the total.
        return weightedSpells.keySet().iterator().next();
    }
    
    /**
     * Plays transformation animation
     */
    private static void playTransformationAnimation(Player player, Spell spell, Runnable onComplete) {
        new BukkitRunnable() {
            int ticks = 0;
            int realTicks = 0;
            final int maxTicks = 20; // 2 seconds at a 2 tick period
            final int period = 2;
            
            @Override
            public void run() {
                realTicks += period;
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
                    double angle = (ticks * 1.0) + (i * Math.PI / 2);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    
                    org.bukkit.Location particleLoc = loc.clone().add(x, 0, z);
                    ParticleCaster.spawn(player, loc.getWorld(), org.bukkit.Particle.PORTAL, particleLoc, 3, 0, 0, 0, 0.1);
                }
                
                // Sound effects
                if (realTicks % 8 == 0) {
                    loc.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.0f + (ticks / (float) maxTicks) * 0.5f);
                }
                
                if (ticks == maxTicks - 1) {
                    // Final flash
                    ParticleCaster.spawn(player, loc.getWorld(), org.bukkit.Particle.FLASH, loc, 1, 0, 0, 0, Color.WHITE);
                    loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                }
                
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 2L);
    }
    
    /**
     * Checks if an ItemStack is a random spellbook
     */
    public static boolean isRandomSpellbook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        
        String type = NBT.get(item, nbt -> (String) nbt.getString("spellbook_type"));
        return "random".equals(type);
    }
}
