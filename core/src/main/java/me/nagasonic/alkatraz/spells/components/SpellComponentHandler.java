package me.nagasonic.alkatraz.spells.components;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class SpellComponentHandler implements Listener {
    private static Map<UUID, SpellComponent> activeComponents = new HashMap<>();

    public static void register(SpellComponent component){
        activeComponents.put(component.getComponentID(), component);
    }

    public static void remove(UUID uuid){
        activeComponents.remove(uuid);
    }

    public static Map<UUID, SpellComponent> getActiveComponents() {
        return activeComponents;
    }

    public static void tick(){
        BukkitRunnable detector = new BukkitRunnable() {
            @Override
            public void run() {
                for (SpellComponent comp : activeComponents.values()){
                    if (comp instanceof SpellEntityComponent ecomp) { //Particle Components are calculated on creation
                        Entity e = ecomp.getEntity();
                        Location loc = e.getLocation();
                        for (SpellComponent component : activeComponents.values()) {
                            if (component instanceof SpellEntityComponent ec){
                                Location other = ec.getEntity().getLocation();
                                if (loc.distance(other) <= 0.25){
                                    collide(comp, component);
                                }
                            }
                        }
                    }
                }
            }
        };
        detector.runTaskTimer(Alkatraz.getInstance(), 0, 1);
    }

    public static void collide(SpellComponent component1, SpellComponent component2){
        if (component1.getType() == SpellComponentType.OFFENSE && component2.getType() == SpellComponentType.DEFENSE){

        }
    }
}
