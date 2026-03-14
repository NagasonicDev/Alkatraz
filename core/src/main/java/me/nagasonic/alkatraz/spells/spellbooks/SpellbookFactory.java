package me.nagasonic.alkatraz.spells.spellbooks;

import me.nagasonic.alkatraz.spells.configuration.impact.implementation.ManaCostImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory class for creating spellbooks
 * 
 * This class contains methods to easily create spellbooks for each spell.
 * You can customize requirements, impacts, and appearance here.
 */
public class SpellbookFactory {
    
    // ==========================================
    // REGULAR SPELLBOOKS
    // ==========================================
    
    /**
     * Creates a Fireball spellbook
     * Basic spell, low requirements
     */
    public static ItemStack createFireballSpellbook() {
        return new Spellbook("fireball")
                .setMaterial(Material.FIRE_CHARGE)
                .setDisplayName("&cFireball Tome")
                .addLoreLine("")
                .addLoreLine("&7A charred book that radiates heat.")
                .build();
    }
    
    /**
     * Creates a Dark Tendrils spellbook
     * Mid-level spell with circle requirement
     */
    public static ItemStack createDarkTendrilsSpellbook() {
        return new Spellbook("dark_tendrils")
                .setMaterial(Material.ENDER_EYE)
                .setDisplayName("&5Grimoire of Shadows")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 3, "Requires Circle Level 3"))
                .addLoreLine("")
                .addLoreLine("&7Dark whispers emanate from these pages...")
                .build();
    }
    
    /**
     * Creates a Wind Vortex spellbook
     * No special requirements, but gives bonus mana
     */
    public static ItemStack createWindVortexSpellbook() {
        return new Spellbook("wind_vortex")
                .setMaterial(Material.FEATHER)
                .setDisplayName("&fWinds of Change")
                .addImpact(new ManaCostImpact(null, -10)) // Grants 10 bonus max mana
                .addLoreLine("")
                .addLoreLine("&7The pages flutter even when closed.")
                .build();
    }
    
    /**
     * Creates an Air Ball spellbook
     * High-level spell with strict requirements and impacts
     */
    public static ItemStack createAirBallSpellbook() {
        return new Spellbook("air_ball")
                .setMaterial(Material.GHAST_TEAR)
                .setDisplayName("&fAvatar's Manual")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 4, "Requires Circle Level 4"))
                .addRequirement(new NumberStatRequirement<>("airPoints", 10, "Requires 10 Air Points"))
                .addImpact(new StatModifierImpact(null, "airAffinity", 5.0, StatModifierImpact.ModifierType.ADD))
                .addLoreLine("")
                .addLoreLine("&7Ancient airbending techniques are")
                .addLoreLine("&7inscribed within these sacred texts.")
                .build();
    }
    
    /**
     * Creates a Water Pulse spellbook
     */
    public static ItemStack createWaterPulseSpellbook() {
        return new Spellbook("water_pulse")
                .setMaterial(Material.HEART_OF_THE_SEA)
                .setDisplayName("&bOceanic Codex")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 2, "Requires Circle Level 2"))
                .addLoreLine("")
                .addLoreLine("&7Droplets of water bead on its surface.")
                .build();
    }
    
    // ==========================================
    // RANDOM SPELLBOOKS
    // ==========================================
    
    /**
     * Creates a Common Random Spellbook
     * Contains basic spells with high weights
     */
    public static ItemStack createCommonRandomSpellbook() {
        return new RandomSpellbook("Common")
                .setMaterial(Material.BOOK)
                .setDisplayName("&7Common Spellbook")
                .addSpell("fireball", 100)        // Very common
                .addSpell("water_pulse", 80)      // Common
                .addLoreLine("")
                .addLoreLine("&7Contains basic magical knowledge.")
                .build();
    }
    
    /**
     * Creates an Uncommon Random Spellbook
     * Mix of basic and intermediate spells
     */
    public static ItemStack createUncommonRandomSpellbook() {
        return new RandomSpellbook("Uncommon")
                .setMaterial(Material.WRITABLE_BOOK)
                .setDisplayName("&aUncommon Spellbook")
                .addSpell("fireball", 60)         // Less common
                .addSpell("water_pulse", 70)      // Somewhat common
                .addSpell("wind_vortex", 50)      // Uncommon
                .addSpell("dark_tendrils", 40)    // Rare
                .addLoreLine("")
                .addLoreLine("&7Contains intermediate magical knowledge.")
                .build();
    }
    
    /**
     * Creates a Rare Random Spellbook
     * Focuses on advanced spells
     */
    public static ItemStack createRareRandomSpellbook() {
        return new RandomSpellbook("Rare")
                .setMaterial(Material.ENCHANTED_BOOK)
                .setDisplayName("&9Rare Spellbook")
                .addSpell("wind_vortex", 60)      // More common in rare books
                .addSpell("dark_tendrils", 70)    // Common in rare books
                .addSpell("air_ball", 30)         // Rare even here
                .addLoreLine("")
                .addLoreLine("&7Contains advanced magical knowledge.")
                .build();
    }
    
    /**
     * Creates an Epic Random Spellbook
     * High-tier spells only
     */
    public static ItemStack createEpicRandomSpellbook() {
        return new RandomSpellbook("Epic")
                .setMaterial(Material.ENCHANTED_BOOK)
                .setDisplayName("&5Epic Spellbook")
                .addSpell("dark_tendrils", 50)
                .addSpell("air_ball", 60)
                .addSpell("wind_vortex", 40)
                .addLoreLine("")
                .addLoreLine("&7Contains powerful magical secrets.")
                .build();
    }
    
    /**
     * Creates a Fire Element Random Spellbook
     * Only contains fire spells
     */
    public static ItemStack createFireRandomSpellbook() {
        return new RandomSpellbook("Fire")
                .setMaterial(Material.FIRE_CHARGE)
                .setDisplayName("&cFire Spellbook")
                .addSpell("fireball", 100)
                // Add more fire spells as you create them
                // .addSpell("fire_blast", 60)
                // .addSpell("meteor", 20)
                .addLoreLine("")
                .addLoreLine("&7Contains spells of flame and ash.")
                .build();
    }
    
    /**
     * Creates an Air Element Random Spellbook
     */
    public static ItemStack createAirRandomSpellbook() {
        return new RandomSpellbook("Air")
                .setMaterial(Material.FEATHER)
                .setDisplayName("&fAir Spellbook")
                .addSpell("wind_vortex", 70)
                .addSpell("air_ball", 50)
                .addLoreLine("")
                .addLoreLine("&7Contains spells of wind and sky.")
                .build();
    }
    
    /**
     * Creates a Water Element Random Spellbook
     */
    public static ItemStack createWaterRandomSpellbook() {
        return new RandomSpellbook("Water")
                .setMaterial(Material.HEART_OF_THE_SEA)
                .setDisplayName("&bWater Spellbook")
                .addSpell("water_pulse", 100)
                // Add more water spells as you create them
                .addLoreLine("")
                .addLoreLine("&7Contains spells of ocean and ice.")
                .build();
    }
    
    /**
     * Creates a Dark Element Random Spellbook
     */
    public static ItemStack createDarkRandomSpellbook() {
        return new RandomSpellbook("Dark")
                .setMaterial(Material.ENDER_EYE)
                .setDisplayName("&5Dark Spellbook")
                .addSpell("dark_tendrils", 100)
                // Add more dark spells as you create them
                .addLoreLine("")
                .addLoreLine("&7Contains spells of shadow and void.")
                .build();
    }
    
    // ==========================================
    // UTILITY METHODS
    // ==========================================
    
    /**
     * Gets a spellbook by spell ID
     * This is a fallback that creates a basic spellbook for any spell
     */
    public static ItemStack getSpellbook(String spellId) {
        return new Spellbook(spellId).build();
    }
    
    /**
     * Creates a custom random spellbook with specified spells and weights
     * 
     * Example usage:
     * createCustomRandomSpellbook("Starter Pack",
     *     Material.BOOK,
     *     "fireball", 60,
     *     "water_pulse", 40
     * )
     */
    public static ItemStack createCustomRandomSpellbook(String name, Material material, Object... spellsAndWeights) {
        if (spellsAndWeights.length % 2 != 0) {
            throw new IllegalArgumentException("Must provide pairs of spell IDs and weights!");
        }
        


        Map<String, Double> weights = new HashMap<>();
        for (int i = 0; i < spellsAndWeights.length; i += 2) {
            String spellId = (String) spellsAndWeights[i];
            double weight = (Double) spellsAndWeights[i + 1];
            weights.put(spellId, weight);
        }
        RandomSpellbook book = new RandomSpellbook("&d" + name, weights)
                .setMaterial(material);
        
        return book.build();
    }
}
