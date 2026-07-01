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

    private static int registeredCount = 0;

    public static void registerSpells(){
        registeredCount = 0;
        registerIfEnabled("air_blades", new AirBlades("AIR_BLADES"));
        registerIfEnabled("air_burst", new AirBurst("AIR_BURST"));
        registerIfEnabled("barrier", new Barrier("BARRIER"));
        registerIfEnabled("blink", new Blink("BLINK"));
        registerIfEnabled("buff", new Buff("BUFF"));
        registerIfEnabled("debuff", new Debuff("DEBUFF"));
        registerIfEnabled("dark_tendrils", new DarkTendrils("DARK_TENDRILS"));
        registerIfEnabled("earthen_wall", new EarthenWall("EARTHEN_WALL"));
        registerIfEnabled("detect", new Detect("DETECT"));
        registerIfEnabled("disguise", new Disguise("DISGUISE"));
        registerIfEnabled("earth_spike", new EarthSpike("EARTH_SPIKE"));
        registerIfEnabled("earth_throw", new EarthThrow("EARTH_THROW"));
        registerIfEnabled("fireball", new Fireball("FIREBALL"));
        registerIfEnabled("fire_blast", new FireBlast("FIRE_BLAST"));
        registerIfEnabled("fire_wall", new FireWall("FIRE_WALL"));
        registerIfEnabled("flaming_volley", new FlamingVolley("FLAMING_VOLLEY"));
        registerIfEnabled("geyser", new Geyser("GEYSER"));
        registerIfEnabled("heal", new Heal("HEAL"));
        registerIfEnabled("lesser_heal", new LesserHeal("LESSER_HEAL"));
        registerIfEnabled("light_buff", new LightBuff("LIGHT_BUFF"));
        registerIfEnabled("magic_missile", new MagicMissile("MAGIC_MISSILE"));
        registerIfEnabled("stealth", new Stealth("STEALTH"));
        registerIfEnabled("summon_zombies", new SummonZombies("SUMMON_ZOMBIES"));
        registerIfEnabled("swift", new Swift("SWIFT"));
        registerIfEnabled("tremor", new Tremor("TREMOR"));
        registerIfEnabled("whirlpool", new Whirlpool("WHIRLPOOL"));
        registerIfEnabled("water_pulse", new WaterPulse("WATER_PULSE"));
        registerIfEnabled("water_sphere", new WaterSphere("WATER_SPHERE"));
        registerIfEnabled("wind_barrier", new WindBarrier("WIND_BARRIER"));
        registerIfEnabled("wind_vortex", new WindVortex("WIND_VORTEX"));
        Alkatraz.logInfo("Registered " + registeredCount + " spells.");
    }

    private static void registerIfEnabled(String key, Spell spell){
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/" + key + ".yml").get();
        if (spellConfig.getBoolean("enabled")) {
            registerSpell(spell);
            registeredCount++;
        }
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
        if (id == null || id.isEmpty()) return null;
        return getAllSpells().values().stream()
                .filter(s -> id.equals(s.getId()))
                .findFirst()
                .orElse(null);
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
    }

    public static boolean isRegistered(Class<? extends Spell> spell){
        return allSpells.containsKey(spell);
    }

    public static void reload() {
        allSpells = Collections.unmodifiableMap(new HashMap<>());
        registerSpells();
    }
}
