package me.nagasonic.alkatraz.spells.spellbooks;

import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.ManaCostImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

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
    public static ItemStack createSpellBook(Spell spell) {
        return spell.getSpellBook();
    }
    
    // ==========================================
    // RANDOM SPELLBOOKS
    // ==========================================

    public static int getWeight(int circle){
        return switch (circle){
            case 1 -> 10000;
            case 2 -> 5000;
            case 3 -> 2500;
            case 4 -> 1250;
            case 5 -> 750;
            case 6 -> 375;
            case 7 -> 150;
            case 8 -> 75;
            case 9 -> 30;
            default -> 0;
        };
    }


    public static ItemStack createRandomSpellBook(int... circles){
        Map<Integer, List<Spell>> spellsByCircle = new HashMap<>();
        for (int c : circles) {
            List<Spell> spells = new ArrayList<>();
            for (Spell spell : SpellRegistry.getAllSpells().values()) {
                if (spell.getLevel() == c){
                    spells.add(spell);
                }
            }
            spellsByCircle.put(c, spells);
        }
        RandomSpellbook random = new RandomSpellbook("Random Spellbook")
                .setDisplayName("&7Random Spellbook")
                .addLoreLine("")
                .addLoreLine("&7Contains spells from " + Arrays.toString(circles) + " circle levels");
        for (Integer circle : spellsByCircle.keySet()) {
            List<Spell> spells = spellsByCircle.get(circle);
            for (Spell spell : spells){
                random.addSpell(spell.getId(), getWeight(circle));
            }
        }
        return random.build();
    }

    public static ItemStack createRandomElementSpellBook(Element element){
        Map<Integer, List<Spell>> spellsByCircle = new HashMap<>();
        for (Spell spell : SpellRegistry.getAllSpells().values()) {
            if (spell.getElement() == element){
                if (!spellsByCircle.containsKey(spell.getLevel())){
                    List<Spell> spells = new ArrayList<>();
                    spells.add(spell);
                    spellsByCircle.put(spell.getLevel(), spells);
                }else{
                    List<Spell> spells = spellsByCircle.get(spell.getLevel());
                    spells.add(spell);
                    spellsByCircle.replace(spell.getLevel(), spells);
                }
            }
        }
        RandomSpellbook random = new RandomSpellbook(element.getName() + " Spellbook")
                .setDisplayName(element.getColor() + element.getName() + " Spellbook")
                .addLoreLine("")
                .addLoreLine("&7Contains" + element.getName() + " spells.");
        for (Integer circle : spellsByCircle.keySet()) {
            List<Spell> spells = spellsByCircle.get(circle);
            for (Spell spell : spells){
                random.addSpell(spell.getId(), getWeight(circle));
            }
        }
        return random.build();
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
