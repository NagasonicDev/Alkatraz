package me.nagasonic.alkatraz.spells;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.spells.implementation.*;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SpellRegistry {
    private static Map<Class<?>, Spell> allSpells = Collections.unmodifiableMap(new HashMap<>());
    private static Map<String, Spell> allSpellsByID = Collections.unmodifiableMap(new HashMap<>());

    public static void registerSpells(){
        registerIfEnabled("air_blades", new AirBlades("AIR_BLADES"));
        registerIfEnabled("air_burst", new AirBurst("AIR_BURST"));
        registerIfEnabled("barrier", new Barrier("BARRIER"));
        registerIfEnabled("dark_tendrils", new DarkTendrils("DARK_TENDRILS"));
        registerIfEnabled("detect", new Detect("DETECT"));
        registerIfEnabled("disguise", new Disguise("DISGUISE"));
        registerIfEnabled("earth_spike", new EarthSpike("EARTH_SPIKE"));
        registerIfEnabled("earth_throw", new EarthThrow("EARTH_THROW"));
        registerIfEnabled("fireball", new Fireball("FIREBALL"));
        registerIfEnabled("fire_blast", new FireBlast("FIRE_BLAST"));
        registerIfEnabled("fire_wall", new FireWall("FIRE_WALL"));
        registerIfEnabled("geyser", new Geyser("GEYSER"));
        registerIfEnabled("lesser_heal", new LesserHeal("LESSER_HEAL"));
        registerIfEnabled("magic_missile", new MagicMissile("MAGIC_MISSILE"));
        registerIfEnabled("stealth", new Stealth("STEALTH"));
        registerIfEnabled("swift", new Swift("SWIFT"));
        registerIfEnabled("water_pulse", new WaterPulse("WATER_PULSE"));
        registerIfEnabled("water_sphere", new WaterSphere("WATER_SPHERE"));
        registerIfEnabled("wind_vortex", new WindVortex("WIND_VORTEX"));
    }

    private static void registerIfEnabled(String key, Spell spell){
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/" + key + ".yml").get();
        if (spellConfig.getBoolean("enabled")) registerSpell(spell);
    }

    public static Map<Class<?>, Spell> getAllSpells() {
        return allSpells;
    }

    public static Map<String, Spell> getAllSpellsByID() {
        return allSpellsByID;
    }

    public static <T extends Spell> Spell getSpell(Class<T> spell){
        if (!allSpells.containsKey(spell)) throw new IllegalArgumentException("Spell " + spell.getSimpleName() + " was not registered for usage");
        return allSpells.get(spell);
    }

    public static Spell getSpellByCode(String code){
        for (Spell spell : allSpells.values()){
            if (Objects.equals(spell.getCode(), code)) return spell;
        }
        return null;
    }

    public static Spell getSpell(String id){
        return allSpellsByID.get(id);
    }

    public static Spell getSpellFromName(String name) {
        for (Spell spell : allSpells.values()){
            if (Objects.equals(spell.getId(), name)){
                return spell;
            }
        }
        return null;
    }

    public static void registerSpell(Spell spell){
        Map<Class<?>, Spell> spells = new HashMap<>(allSpells);
        spells.put(spell.getClass(), spell);
        allSpells = Collections.unmodifiableMap(spells);
        spell.loadConfiguration();
        Map<String, Spell> spellsById = new HashMap<>(allSpellsByID);
        spellsById.put(spell.getId(), spell);
        allSpellsByID = Collections.unmodifiableMap(spellsById);
        Alkatraz.logInfo("Registered spell: " + spell.getId());
    }

    public static boolean isRegistered(Class<? extends Spell> spell){
        return allSpells.containsKey(spell);
    }

    public static void reload() {
        allSpells = Collections.unmodifiableMap(new HashMap<>());
        registerSpells();
    }
}
