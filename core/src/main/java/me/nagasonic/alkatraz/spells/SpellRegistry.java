package me.nagasonic.alkatraz.spells;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.spells.implementation.*;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class SpellRegistry {
    private static Map<Class<?>, Spell> allSpells = Collections.unmodifiableMap(new HashMap<>());
    private static Map<String, Spell> allSpellsByID = Collections.unmodifiableMap(new HashMap<>());
    private static Map<String, Spell> allSpellsByIdFull = Collections.unmodifiableMap(new HashMap<>());

    private static int registeredCount = 0;

    /**
     * id -> constructor. This is the SINGLE authored mapping from a spell id to the
     * class that serves it. registerSpells() iterates {@link #SPELL_IDS}, which is
     * derived from this map's key order, so an id can never be registered without a
     * class behind it and a class can never be reachable without an id.
     *
     * <p>Two spell ids deliberately do not follow the PascalCase-of-the-id rule:
     * {@code earthsplitter} is served by {@link Fissure}, and {@code magic_missile}
     * by {@link MagicMissile} whose getSpellBook() returns null (documented at
     * english.lang). Everything else is PascalCase of the id with underscores
     * removed, and the type token is the id uppercased.
     */
    private static final Map<String, Supplier<Spell>> SPELL_FACTORY = new LinkedHashMap<>();

    /**
     * Authors one entry into {@link #SPELL_FACTORY}.
     *
     * <p><b>The name and argument order are load-bearing and must not be
     * "cleaned up".</b> Wave 4's {@code SpellRegistryParityTest} does not call
     * {@link #registerSpells()} - constructing 67 spells needs a live PluginManager -
     * so it discovers the registry roster by scanning this file as text for the
     * pattern {@code registerIfEnabled("(<id>)"}. Renaming this method, or moving
     * these calls out of this file, silently empties that roster and fails
     * {@code theThreeListsHaveTheSameSize}. Java resolves the
     * overload against the second parameter type ({@code Supplier<Spell>} here).
     */
    private static void registerIfEnabled(String id, Supplier<Spell> factory) {
        SPELL_FACTORY.put(id, factory);
    }

    static {
        registerIfEnabled("air_blades", () -> new AirBlades("AIR_BLADES"));
        registerIfEnabled("air_burst", () -> new AirBurst("AIR_BURST"));
        registerIfEnabled("barrier", () -> new Barrier("BARRIER"));
        registerIfEnabled("blink", () -> new Blink("BLINK"));
        registerIfEnabled("buff", () -> new Buff("BUFF"));
        registerIfEnabled("debuff", () -> new Debuff("DEBUFF"));
        registerIfEnabled("dark_tendrils", () -> new DarkTendrils("DARK_TENDRILS"));
        registerIfEnabled("earthen_wall", () -> new EarthenWall("EARTHEN_WALL"));
        registerIfEnabled("detect", () -> new Detect("DETECT"));
        registerIfEnabled("disguise", () -> new Disguise("DISGUISE"));
        registerIfEnabled("dispel", () -> new Dispel("DISPEL"));
        registerIfEnabled("earth_spike", () -> new EarthSpike("EARTH_SPIKE"));
        registerIfEnabled("earth_throw", () -> new EarthThrow("EARTH_THROW"));
        registerIfEnabled("fireball", () -> new Fireball("FIREBALL"));
        registerIfEnabled("fire_blast", () -> new FireBlast("FIRE_BLAST"));
        registerIfEnabled("fire_wall", () -> new FireWall("FIRE_WALL"));
        registerIfEnabled("flaming_volley", () -> new FlamingVolley("FLAMING_VOLLEY"));
        registerIfEnabled("geyser", () -> new Geyser("GEYSER"));
        registerIfEnabled("heal", () -> new Heal("HEAL"));
        registerIfEnabled("lesser_heal", () -> new LesserHeal("LESSER_HEAL"));
        registerIfEnabled("light_buff", () -> new LightBuff("LIGHT_BUFF"));
        registerIfEnabled("magic_missile", () -> new MagicMissile("MAGIC_MISSILE"));
        registerIfEnabled("stealth", () -> new Stealth("STEALTH"));
        registerIfEnabled("summon_zombies", () -> new SummonZombies("SUMMON_ZOMBIES"));
        registerIfEnabled("skeletal_knights", () -> new SkeletalKnights("SKELETAL_KNIGHTS"));
        registerIfEnabled("swift", () -> new Swift("SWIFT"));
        registerIfEnabled("tremor", () -> new Tremor("TREMOR"));
        registerIfEnabled("whirlpool", () -> new Whirlpool("WHIRLPOOL"));
        registerIfEnabled("water_pulse", () -> new WaterPulse("WATER_PULSE"));
        registerIfEnabled("water_sphere", () -> new WaterSphere("WATER_SPHERE"));
        registerIfEnabled("wind_barrier", () -> new WindBarrier("WIND_BARRIER"));
        registerIfEnabled("wind_vortex", () -> new WindVortex("WIND_VORTEX"));
        registerIfEnabled("meteor_shower", () -> new MeteorShower("METEOR_SHOWER"));
        registerIfEnabled("tsunami", () -> new Tsunami("TSUNAMI"));
        registerIfEnabled("earthsplitter", () -> new Fissure("EARTHSPLITTER"));
        registerIfEnabled("tornado", () -> new Tornado("TORNADO"));
        registerIfEnabled("radiance", () -> new Radiance("RADIANCE"));
        registerIfEnabled("shadow_realm", () -> new ShadowRealm("SHADOW_REALM"));
        registerIfEnabled("warp", () -> new Warp("WARP"));
        registerIfEnabled("flame_lance", () -> new FlameLance("FLAME_LANCE"));
        registerIfEnabled("inferno_pilgrimage", () -> new InfernoPilgrimage("INFERNO_PILGRIMAGE"));
        registerIfEnabled("sunfire_barrage", () -> new SunfireBarrage("SUNFIRE_BARRAGE"));
        registerIfEnabled("inferno_rite", () -> new InfernoRite("INFERNO_RITE"));
        registerIfEnabled("undertow", () -> new Undertow("UNDERTOW"));
        registerIfEnabled("everdrain", () -> new Everdrain("EVERDRAIN"));
        registerIfEnabled("thousand_waves", () -> new ThousandWaves("THOUSAND_WAVES"));
        registerIfEnabled("tide_of_the_deep", () -> new TideOfTheDeep("TIDE_OF_THE_DEEP"));
        registerIfEnabled("tectonic_strike", () -> new TectonicStrike("TECTONIC_STRIKE"));
        registerIfEnabled("fault_line", () -> new FaultLine("FAULT_LINE"));
        registerIfEnabled("continental_shift", () -> new ContinentalShift("CONTINENTAL_SHIFT"));
        registerIfEnabled("worldbreaker", () -> new Worldbreaker("WORLDBREAKER"));
        registerIfEnabled("updraft", () -> new Updraft("UPDRAFT"));
        registerIfEnabled("thunderclap", () -> new Thunderclap("THUNDERCLAP"));
        registerIfEnabled("cyclone_nest", () -> new CycloneNest("CYCLONE_NEST"));
        registerIfEnabled("stormcaller", () -> new Stormcaller("STORMCALLER"));
        registerIfEnabled("skywrath", () -> new Skywrath("SKYWRATH"));
        registerIfEnabled("light_bolt", () -> new LightBolt("LIGHT_BOLT"));
        registerIfEnabled("radiant_brand", () -> new RadiantBrand("RADIANT_BRAND"));
        registerIfEnabled("choir_of_light", () -> new ChoirOfLight("CHOIR_OF_LIGHT"));
        registerIfEnabled("apotheosis", () -> new Apotheosis("APOTHEOSIS"));
        registerIfEnabled("crown_of_dawn", () -> new CrownOfDawn("CROWN_OF_DAWN"));
        registerIfEnabled("umbral_volley", () -> new UmbralVolley("UMBRAL_VOLLEY"));
        registerIfEnabled("void_anchor", () -> new VoidAnchor("VOID_ANCHOR"));
        registerIfEnabled("oblivion", () -> new Oblivion("OBLIVION"));
        registerIfEnabled("arcane_ward", () -> new ArcaneWard("ARCANE_WARD"));
        registerIfEnabled("waystone_recall", () -> new WaystoneRecall("WAYSTONE_RECALL"));
        registerIfEnabled("echo_cast", () -> new EchoCast("ECHO_CAST"));
        registerIfEnabled("arcane_resonance", () -> new ArcaneResonance("ARCANE_RESONANCE"));
    }

    /**
     * Every bundled spell id, in registration order.
     *
     * <p><b>This declaration MUST stay below the static block above.</b> Java runs
     * field initialisers and static blocks in source order, so a SPELL_IDS declared
     * above it would take {@code SPELL_FACTORY.keySet()} while the map is still
     * empty and freeze an empty list. {@link #registerSpells()} would then loop zero
     * times and log "Registered 0 spells." with every spell in the plugin silently
     * dead. Nothing in the test suite catches that: {@code SpellRegistryParityTest}
     * reads this file as text and never executes it.
     *
     * <p>Derived from SPELL_FACTORY rather than authored twice. The id strings are
     * ALSO duplicated, as literals, in {@code Alkatraz.spellConfigPaths()} - that
     * duplication is deliberate and is what wave 4's SpellRegistryParityTest keeps
     * honest. Do not "de-duplicate" it by deriving this list from Alkatraz: that
     * empties the test's saved-config roster and fails the wave-7 gate.
     */
    public static final List<String> SPELL_IDS = List.copyOf(SPELL_FACTORY.keySet());

    public static void registerSpells() {
        registeredCount = 0;
        for (String id : SPELL_IDS) {
            Supplier<Spell> factory = SPELL_FACTORY.get(id);
            if (factory == null) {
                Alkatraz.logWarning("SpellRegistry: '" + id
                        + "' is in SPELL_IDS with no factory entry and was not registered.");
                continue;
            }
            YamlConfiguration spellConfig = ConfigManager.getConfig("spells/" + id + ".yml").get();
            if (!spellConfig.getBoolean("enabled")) continue;
            registerSpell(factory.get());
            registeredCount++;
        }
        Alkatraz.logInfo("Registered " + registeredCount + " spells.");
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
        long start = System.nanoTime();
        Map<Class<?>, Spell> spells = new HashMap<>(allSpells);
        spells.put(spell.getClass(), spell);
        allSpells = Collections.unmodifiableMap(spells);
        spell.loadConfiguration();
        Map<String, Spell> spellsById = new HashMap<>(allSpellsByID);
        spellsById.put(spell.getId(), spell);
        allSpellsByID = Collections.unmodifiableMap(spellsById);
        Map<String, Spell> combined = new HashMap<>(allSpellsByIdFull);
        combined.put(spell.getId(), spell);
        allSpellsByIdFull = Collections.unmodifiableMap(combined);
        Alkatraz.logHigh("Registered spell: " + spell.getId() + " (" + spell.getElement() + ") in "
                + (System.nanoTime() - start) / 1_000_000 + "ms");
    }

    public static boolean isRegistered(Class<? extends Spell> spell){
        return allSpells.containsKey(spell);
    }

    public static void registerApiSpell(me.nagasonic.alkatraz.api.spells.Spell apiSpell) {
        ApiSpellAdapter adapter = new ApiSpellAdapter(apiSpell);
        adapter.loadConfiguration();
        Map<String, Spell> combined = new HashMap<>(allSpellsByIdFull);
        combined.put(adapter.getId(), adapter);
        allSpellsByIdFull = Collections.unmodifiableMap(combined);
        Alkatraz.logHigh("Registered API spell: " + adapter.getId());
    }

    public static Map<String, Spell> getAllSpellsByIdFull() {
        return allSpellsByIdFull;
    }

    public static void reload() {
        allSpells = Collections.unmodifiableMap(new HashMap<>());
        allSpellsByID = Collections.unmodifiableMap(new HashMap<>());
        allSpellsByIdFull = Collections.unmodifiableMap(new HashMap<>());
        registerSpells();
    }
}
