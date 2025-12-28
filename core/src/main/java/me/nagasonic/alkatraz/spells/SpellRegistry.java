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
    private static Map<String, Spell> allSpellsByCode = Collections.unmodifiableMap(new HashMap<>());

    public static void registerSpells(){
        registerIfEnabled("magic_missile", new MagicMissile("MAGIC_MISSILE"));
        registerIfEnabled("fireball", new Fireball("FIREBALL"));
        registerIfEnabled("water_sphere", new WaterSphere("WATER_SPHERE"));
        registerIfEnabled("air_burst", new AirBurst("AIR_BURST"));
        registerIfEnabled("earth_throw", new EarthThrow("EARTH_THROW"));
        registerIfEnabled("lesser_heal", new LesserHeal("LESSER_HEAL"));
        registerIfEnabled("fire_blast", new FireBlast("FIRE_BLAST"));
        registerIfEnabled("detect", new Detect("DETECT"));
        registerIfEnabled("stealth", new Stealth("STEALTH"));
        registerIfEnabled("disguise", new Disguise("DISGUISE"));
        registerIfEnabled("swift", new Swift("SWIFT"));
        registerIfEnabled("fire_wall", new FireWall("FIRE_WALL"));
        registerIfEnabled("earth_spike", new EarthSpike("EARTH_SPIKE"));
        registerIfEnabled("water_pulse", new WaterPulse("WATER_PULSE"));
        registerIfEnabled("barrier", new Barrier("BARRIER"));
    }

    private static void registerIfEnabled(String key, Spell spell){
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/" + key + ".yml").get();
        if (spellConfig.getBoolean("enabled")) registerSpell(spell);
    }

    public static Map<Class<?>, Spell> getAllSpells() {
        return allSpells;
    }

    public static Map<String, Spell> getAllSpellsByCode() {
        return allSpellsByCode;
    }

    public static <T extends Spell> Spell getSpell(Class<T> spell){
        if (!allSpells.containsKey(spell)) throw new IllegalArgumentException("Spell " + spell.getSimpleName() + " was not registered for usage");
        return allSpells.get(spell);
    }

    public static Spell getSpell(String code){
        return allSpellsByCode.get(code);
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
        Map<String, Spell> spellsByCode = new HashMap<>(allSpellsByCode);
        spellsByCode.put(spell.getCode(), spell);
        allSpellsByCode = Collections.unmodifiableMap(spellsByCode);
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
