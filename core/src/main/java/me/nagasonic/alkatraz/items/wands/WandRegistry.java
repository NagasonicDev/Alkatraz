package me.nagasonic.alkatraz.items.wands;

import me.nagasonic.alkatraz.items.wands.implementation.WoodenWand;
import org.bukkit.Bukkit;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class WandRegistry {
    private static Map<Class<?>, Wand> allWands = Collections.unmodifiableMap(new HashMap<>());
    private static Map<String, Wand> allWandsByType = Collections.unmodifiableMap(new HashMap<>());

    public static void registerWands(){
        registerWand(new WoodenWand("WOODEN_WAND"));
    }

    public static Map<Class<?>, Wand> getAllWands() {
        return allWands;
    }

    public static Map<String, Wand> getAllWandsByType() {
        return allWandsByType;
    }

    public static <T extends Wand> Wand getWand(Class<T> wand){
        if (!allWands.containsKey(wand)) throw new IllegalArgumentException("Wand " + wand.getSimpleName() + " was not registered for usage");
        return allWands.get(wand);
    }

    public static Wand getWand(String name){
        return allWandsByType.get(name);
    }

    public static void registerWand(Wand wand){
        Map<Class<?>, Wand> wands = new HashMap<>(allWands);
        wands.put(wand.getClass(), wand);
        allWands = Collections.unmodifiableMap(wands);
        Map<String, Wand> wandsByType = new HashMap<>(allWandsByType);
        wandsByType.put(wand.getType(), wand);
        allWandsByType = Collections.unmodifiableMap(wandsByType);
        wand.loadConfiguration();
        Bukkit.addRecipe(wand.getRecipe());
    }

    public static boolean isRegistered(Class<? extends Wand> wand){
        return allWands.containsKey(wand);
    }

    public static void reload() {
        allWands = Collections.unmodifiableMap(new HashMap<>());
        registerWands();
    }
}
