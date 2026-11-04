package me.nagasonic.alkatraz;
import me.nagasonic.alkatraz.commands.AlkatrazCommand;
import me.nagasonic.alkatraz.commands.CastCommand;
import me.nagasonic.alkatraz.commands.RecipesCommand;
import me.nagasonic.alkatraz.commands.SpellsCommand;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.api.config.ConfigUpdater;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.config.VerbosityLevel;
import me.nagasonic.alkatraz.dom.MinecraftVersion;
import me.nagasonic.alkatraz.focus.FocusManager;
import me.nagasonic.alkatraz.gui.EnchantingTableListener;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.gui.MenuListener;
import me.nagasonic.alkatraz.items.magic.MagicItemBootstrap;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentStorage;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentCacheListener;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentReconcileTask;
import me.nagasonic.alkatraz.items.magic.adapter.MagicItemTriggerAdapter;
import me.nagasonic.alkatraz.items.magic.barrier.BarrierEventListener;
import me.nagasonic.alkatraz.spells.modifier.ModifierLifecycleListener;
import me.nagasonic.alkatraz.items.magic.barrier.BarrierManager;
import me.nagasonic.alkatraz.items.magic.adapter.MagicItemComponentListener;
import me.nagasonic.alkatraz.items.magic.adapter.CombatTriggerListener;
import me.nagasonic.alkatraz.items.magic.adapter.MagicDamageListener;
import me.nagasonic.alkatraz.items.magic.adapter.MovementTriggerListener;
import me.nagasonic.alkatraz.items.magic.adapter.EnvironmentTriggerListener;
import me.nagasonic.alkatraz.items.magic.adapter.InventoryTriggerListener;
import me.nagasonic.alkatraz.items.magic.adapter.GatheringTriggerListener;
import me.nagasonic.alkatraz.items.magic.adapter.PlayerStateTriggerListener;
import me.nagasonic.alkatraz.items.magic.adapter.SocialTriggerListener;
import me.nagasonic.alkatraz.items.magic.adapter.PeriodicTriggerListener;
import me.nagasonic.alkatraz.items.magic.adapter.WorldGuardTriggerListener;
import me.nagasonic.alkatraz.items.magic.listener.CastEventListener;
import me.nagasonic.alkatraz.items.magic.listener.RecipeCraftListener;
import me.nagasonic.alkatraz.items.magic.listener.MagicStoneDropListener;
import me.nagasonic.alkatraz.items.magic.listener.MagicItemCraftListener;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeManager;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeManagerAPI;
import me.nagasonic.alkatraz.items.magic.recipe.adapter.CraftingEventRouter;
import me.nagasonic.alkatraz.items.magic.recipe.unlock.UnlockListener;
import me.nagasonic.alkatraz.items.magic.imbue.ImbueAnvilListener;
import me.nagasonic.alkatraz.items.magic.recipe.unlock.UnlockSweep;
import me.nagasonic.alkatraz.loot.LootInjector;
import me.nagasonic.alkatraz.loot.MobLootInjector;
import me.nagasonic.alkatraz.loot.implementation.SpellbookLoot;
import me.nagasonic.alkatraz.mobs.MagicAiServiceImpl;
import me.nagasonic.alkatraz.mobs.MagicEntities;
import me.nagasonic.alkatraz.mobs.MagicEntitySpawnListener;
import me.nagasonic.alkatraz.mobs.MobModifier;
import me.nagasonic.alkatraz.mobs.ai.task.TaskBrainTicker;
import me.nagasonic.alkatraz.nms.NMS;
import me.nagasonic.alkatraz.hooks.PlaceholderAPIHook;
import me.nagasonic.alkatraz.hooks.WorldGuardHook;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileRegistry;
import me.nagasonic.alkatraz.playerdata.SpellHotbarManager;
import me.nagasonic.alkatraz.progression.AffinityCurveMigrationNotice;
import me.nagasonic.alkatraz.progression.ProgressionService;
import me.nagasonic.alkatraz.progression.research.ResearchObjectiveListener;
import me.nagasonic.alkatraz.progression.research.ResearchService;
import me.nagasonic.alkatraz.progression.BoosterManager;
import me.nagasonic.alkatraz.spells.CoreSpellRegistryProvider;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.implementation.SummonCombatGuard;
import me.nagasonic.alkatraz.playerdata.profiles.CoreProfileProvider;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.spellbooks.SpellbookListener;
import me.nagasonic.alkatraz.spells.modifier.ModifierManager;
import me.nagasonic.alkatraz.spells.spellbooks.SpellbookVillagerListener;
import me.nagasonic.alkatraz.spells.util.SustainEffect;
import me.nagasonic.alkatraz.spells.vfx.ChannelRegistry;
import me.nagasonic.alkatraz.texturepack.ResourcePackListener;
import me.nagasonic.alkatraz.tutorial.FirstJoinTutorial;
import me.nagasonic.alkatraz.api.SpellAPI;
import me.nagasonic.alkatraz.api.mobs.MagicAiService;
import me.nagasonic.alkatraz.api.mobs.MagicBrainService;
import me.nagasonic.alkatraz.api.mobs.MagicEntityType;
import me.nagasonic.alkatraz.mobs.MagicBrains;
import me.nagasonic.alkatraz.util.SkinUtils;
import me.nagasonic.alkatraz.util.UpdateChecker;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static me.nagasonic.alkatraz.items.magic.listener.CastEventListener.switchFrom;

public final class Alkatraz extends JavaPlugin {

    private static Alkatraz instance;
    private static YamlConfiguration pluginConfig;
    private static YamlConfiguration lootConfig;
    private static NMS nms = null;
    private static boolean enabled = true;
    private static boolean resourcePackForced = false;
    private static VerbosityLevel verbosity = VerbosityLevel.NORMAL;
    private static RecipeManagerAPI recipeManager = new RecipeManager();
    private static Metrics metrics;
    
    // Texture pack system
    private static me.nagasonic.alkatraz.texturepack.TexturePackManager texturePackManager = null;
    private static me.nagasonic.alkatraz.gui.GUIItemRegistry guiItemRegistry = null;
    private static me.nagasonic.alkatraz.lang.LangManager langManager = null;

    private FocusManager focusManager;

    {
        instance = this;
    }

    @Override
    public void onLoad() {
        pluginConfig = saveAndUpdateConfig("config.yml");
        lootConfig = saveAndUpdateConfig("loot.yml");
        saveConfig("playerdata/playerdata.yml");
        saveConfig("progression.yml");
        saveConfig("research.yml");
        saveConfig("texturepack.yml"); // Save texture pack config
        saveConfig("modifiers.yml"); // Save reusable modifier definitions
saveConfig("boosters.yml"); // Save booster definitions

        verbosity = VerbosityLevel.fromString(pluginConfig.getString("verbose"));
        saveSpellConfigs();
        saveMobConfigs();
        saveBrainConfigs();
        if (!setupNMS()){
            enabled = false;
            return;
        }

    }

    @Override
    public void onEnable() {
        SustainEffect.install();
        long enableStart = System.nanoTime();
        logVeryHigh("Alkatraz enabling...");
        // Plugin startup logic
        Configs.reload();
        if ((boolean) Configs.CHECK_UPDATES.get()) UpdateChecker.checkUpdate();
        if (!enabled){
            logSevere("This version of Minecraft is not compatible with Alkatraz.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        } else nms.onEnable();
        resourcePackForced = pluginConfig.getBoolean("resource_pack_override");
        
        // Initialize texture pack system
        logVeryHigh("Initializing TexturePackManager...");
        texturePackManager = new me.nagasonic.alkatraz.texturepack.TexturePackManager();
        texturePackManager.load();
        
        // Initialize LangManager (must be before GUIItemRegistry, which uses lang strings)
        logVeryHigh("Initializing LangManager...");
        save("lang/english.lang");
        langManager = new me.nagasonic.alkatraz.lang.LangManager(
            pluginConfig.getString("language", "english"), getDataFolder());
        
        // Initialize GUI item registry
        logVeryHigh("Initializing GUIItemRegistry...");
        guiItemRegistry = new me.nagasonic.alkatraz.gui.GUIItemRegistry();
        guiItemRegistry.init();
        
        metrics = new Metrics(this, 27657);
        logVeryHigh("Registering profiles...");
        ProfileRegistry.registerProfiles();
        MagicEntities.registerProfiles();
        MagicAiServiceImpl ai = new MagicAiServiceImpl();
        MagicBrainService.setInstance(ai);
        MagicAiService.setInstance(ai);
        TaskBrainTicker.start();
        nms.registerMagicEntities();
        me.nagasonic.alkatraz.mobs.ai.AiSpecRegistry.assertAllSupported(
                me.nagasonic.alkatraz.api.mobs.NativeGoalSpec.Float.class,
                me.nagasonic.alkatraz.api.mobs.NativeGoalSpec.MeleeAttack.class,
                me.nagasonic.alkatraz.api.mobs.NativeGoalSpec.WaterAvoidingRandomStroll.class,
                me.nagasonic.alkatraz.api.mobs.NativeGoalSpec.LookAtPlayer.class,
                me.nagasonic.alkatraz.api.mobs.NativeGoalSpec.RandomLookAround.class,
                me.nagasonic.alkatraz.api.mobs.NativeGoalSpec.HurtByTarget.class,
                me.nagasonic.alkatraz.api.mobs.NativeGoalSpec.NearestAttackableTarget.class,
                me.nagasonic.alkatraz.api.mobs.NativeGoalSpec.Panic.class,
                me.nagasonic.alkatraz.api.mobs.NativeGoalSpec.AvoidEntity.class);
        logVeryHigh("Initializing ProfileManager...");
        ProfileManager.initialize();
        logVeryHigh("Initializing ProgressionService...");
        ProgressionService.initialize();
        me.nagasonic.alkatraz.progression.BoosterManager.startExpiryTask();
        logVeryHigh("Initializing ResearchService...");
        ResearchService.initialize();
        logVeryHigh("Initializing MagicItemBootstrap...");
        MagicItemBootstrap.initialize();
        logVeryHigh("Registering spells...");
        SpellRegistry.registerSpells();
        SpellAPI.setSpellRegistryProvider(new CoreSpellRegistryProvider());
        SpellAPI.setProfileProvider(new CoreProfileProvider());
        logVeryHigh("Registering listeners...");
        registerListener(new SpellbookListener());
        registerListener(new SpellbookVillagerListener());
        LootInjector.register(this);
        registerListener(new MagicEntitySpawnListener());
        registerListener(new MobModifier());
        MobLootInjector.register(this);
        SpellbookLoot.registerAll();
        registerListener(new CastEventListener());
        registerListener(new SpellHotbarManager.QuitListener());
        registerListener(new ResearchObjectiveListener());
        registerListener(new FirstJoinTutorial());
        registerListener(new AffinityCurveMigrationNotice());
        registerListener(new MagicItemTriggerAdapter());
        BarrierManager.initialize();
        registerListener(new BarrierEventListener());
registerListener(new ModifierLifecycleListener());
        registerListener(new CombatTriggerListener());
        registerListener(new MagicDamageListener());
        registerListener(new SummonCombatGuard());
        registerListener(new MovementTriggerListener());
        registerListener(new EnvironmentTriggerListener());
        registerListener(new InventoryTriggerListener());
        registerListener(new GatheringTriggerListener());
        registerListener(new PlayerStateTriggerListener());
        registerListener(new SocialTriggerListener());
        WorldGuardTriggerListener worldGuardTriggerListener = new WorldGuardTriggerListener();
        registerListener(worldGuardTriggerListener);
        new PeriodicTriggerListener().start();
        focusManager = new FocusManager();
        registerListener(focusManager);
        focusManager.start();
        registerListener(new MagicItemComponentListener());
        registerListener(new EnchantingTableListener());
        registerListener(new RecipeCraftListener());
        registerListener(new MagicItemCraftListener());
        registerListener(new MagicStoneDropListener());
        registerListener(new UnlockListener());
        registerListener(new CraftingEventRouter());
        registerListener(new ImbueAnvilListener());
        registerListener(new ResourcePackListener());
        registerListener(ChannelRegistry.INSTANCE);
        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
        logInfo("NMS version " + nms.getClass().getSimpleName() + " registered!");
        getCommand("spells").setExecutor(new SpellsCommand());
        getCommand("recipes").setExecutor(new RecipesCommand());
        getCommand("recipes").setTabCompleter(new RecipesCommand());
        getCommand("cast").setExecutor(new CastCommand());
        getCommand("alkatraz").setExecutor(new AlkatrazCommand());
        getCommand("alkatraz").setTabCompleter(new AlkatrazCommand());
        SpellComponentHandler.tick();
        UnlockSweep.start();
        logVeryHigh("Initializing PlaceholderAPI hook...");
        PlaceholderAPIHook placeholderHook = new PlaceholderAPIHook();
        if (placeholderHook.isPresent()) {
            placeholderHook.ifPresent();
        }
        WorldGuardHook worldGuardHook = WorldGuardHook.getInstance();
        if (worldGuardHook.isPresent()) {
            worldGuardHook.ifPresent();
        }
        long elapsed = (System.nanoTime() - enableStart) / 1_000_000;
        logVeryHigh("Alkatraz enabled in " + elapsed + "ms");
    }

    @Override
    public void onDisable() {
        try { Bukkit.getScheduler().cancelTasks(this); } catch (Exception ignored) { }
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
        TaskBrainTicker.shutdown();
        SpellComponentHandler.shutdown();
        // Drop stale mastery boss bars before profiles are unloaded.
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile mp =
                    me.nagasonic.alkatraz.playerdata.profiles.ProfileManager.getProfile(p, me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile.class);
            if (mp == null) continue;
            for (org.bukkit.boss.BossBar bar : new java.util.ArrayList<>(mp.getMasteryBars().values())) {
                try { bar.removeAll(); } catch (Exception ignored) { }
            }
            for (int taskId : mp.getMasteryBarTaskIds().values()) {
                try { Bukkit.getScheduler().cancelTask(taskId); } catch (Exception ignored) { }
            }
            mp.getMasteryBars().clear();
            mp.getMasteryBarTaskIds().clear();
        }
        me.nagasonic.alkatraz.gui.implementation.editor.EditorChatHandler.shutdown();
        ProfileManager.shutdown();
        BarrierManager.disposeAll();
        if (focusManager != null) {
            focusManager.shutdown();
        }
        PeriodicTriggerListener.stop();
        EquipmentReconcileTask.stop();
        ChannelRegistry.shutdown();
me.nagasonic.alkatraz.spells.effect.EffectRegistry.clearAll();
        BoosterManager.stopExpiryTask();
        BoosterManager.clear();
        ModifierManager.clear();
        SkinUtils.shutdown();
        UnlockSweep.stop();
         UpdateChecker.shutdown();
         LootInjector.resetRegistration();
         MobLootInjector.resetRegistration();
        SpellHotbarManager.exitAll();
        me.nagasonic.alkatraz.items.magic.adapter.MagicDamageListener.clearAll();
        me.nagasonic.alkatraz.spells.vfx.FakeWaterVfx.shutdown();
        for (Player p : Bukkit.getOnlinePlayers()){
            try {
                EquipmentStorage.unload(p);
            } catch (Exception e) {
                Alkatraz.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                        "Failed to unload equipment for player: " + p.getName(), e);
            }
        }
    }

    public static Alkatraz getInstance() {
        return instance;
    }

    public static VerbosityLevel getVerbosity() {
        return verbosity;
    }
    
    /**
     * Get the TexturePackManager instance.
     * @return The texture pack manager
     */
    public static me.nagasonic.alkatraz.texturepack.TexturePackManager getTexturePackManager() {
        return texturePackManager;
    }
    
    /**
     * Get the GUIItemRegistry instance.
     * @return The GUI item registry
     */
    public static me.nagasonic.alkatraz.gui.GUIItemRegistry getGuiItemRegistry() {
        return guiItemRegistry;
    }

    public static me.nagasonic.alkatraz.lang.LangManager getLangManager() {
        return langManager;
    }

    /**
     * Re-reads config.yml into the static {@link #pluginConfig} reference.
     *
     * <p>{@link Config#reload()} builds a fresh {@code YamlConfiguration}, so the instance captured
     * during {@code onLoad} went stale the moment a reload happened. Everything reading
     * {@link #getPluginConfig()} kept seeing boot-time values until a restart, which is why the
     * {@code particles:} section looked inert: {@code ParticleConfig} was reading a stale tree.
     */
    public static void reloadPluginConfig() {
        pluginConfig = ConfigManager.reloadConfig("config.yml").get();
        // Derived purely from config.yml, so it went stale the same way pluginConfig did.
        resourcePackForced = pluginConfig.getBoolean("resource_pack_override");
    }

    /**
     * Rebuilds the LangManager from config. /alkatraz reload calls this before
     * SpellRegistry.reload(), which is what re-reads spell names and descriptions, so a
     * lang edit takes effect on reload instead of needing a server restart.
     */
    /**
     * Clears cached focus movement state on reload (defence against stale entries).
     */
    public static void clearFocusCaches() {
        if (instance != null && instance.focusManager != null) {
            instance.focusManager.clearCaches();
        }
    }

    public static void reloadLangManager() {
        langManager = new me.nagasonic.alkatraz.lang.LangManager(
                pluginConfig.getString("language", "english"), instance.getDataFolder());
    }

    public static RecipeManagerAPI getRecipeManager() {
        return recipeManager;
    }

    public static void log(VerbosityLevel level, String message) {
        if (isLoggable(level)) {
            instance.getServer().getLogger().info("[Alkatraz][" + level.name() + "] " + message);
        }
    }

    /**
     * Returns whether a message at the given level would actually be printed.
     * Use this to guard expensive message construction (string concatenation,
     * UUID formatting, etc.) on hot paths.
     *
     * @param level the level to test
     * @return true if a message at that level would be logged
     */
    public static boolean isLoggable(VerbosityLevel level) {
        return verbosity.isAtLeast(level);
    }

    public static void logInfo(String message){
        instance.getServer().getLogger().info("[Alkatraz] " + message);
    }

    public static void logWarning(String warning){
        instance.getServer().getLogger().warning("[Alkatraz] " + warning);
    }

    public static void logDebug(String message){
        if (verbosity.isAtLeast(VerbosityLevel.DEBUG)) {
            instance.getServer().getLogger().info("[Alkatraz][DEBUG] " + message);
        }
    }

    public static void logHigh(String message){
        if (verbosity.isAtLeast(VerbosityLevel.HIGH)) {
            instance.getServer().getLogger().info("[Alkatraz] " + message);
        }
    }

    public static void logVeryHigh(String message){
        if (verbosity.isAtLeast(VerbosityLevel.VERY_HIGH)) {
            instance.getServer().getLogger().info("[Alkatraz] " + message);
        }
    }

    public static void logSevere(String help){
        instance.getServer().getLogger().severe("[Alkatraz] " + help);
    }

    private void registerListener(Listener listener){
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private boolean setupNMS() {
        try {
            String nmsVersion = MinecraftVersion.getServerVersion().getNmsVersion();
            if (nmsVersion == null) return false;
            Class<?> clazz = Class.forName("me.nagasonic.alkatraz.nms_" + nmsVersion + ".NMS_" + nmsVersion);

            if (NMS.class.isAssignableFrom(clazz)) {
                nms = (NMS) clazz.getDeclaredConstructor().newInstance();
            }

            return nms != null;
        } catch (Exception | Error e) {
            getLogger().severe("Failed to load NMS adapter: " + e.getMessage());
            return false;
        }
    }

    public static NMS getNms() {
        return nms;
    }

    public static boolean isResourcePackForced() {
        return resourcePackForced;
    }

    public static void setResourcePackForced(boolean resourcePackForced) {
        Alkatraz.resourcePackForced = resourcePackForced;
    }

    public YamlConfiguration saveConfig(String name){
        save(name);
        return ConfigManager.saveConfig(name).get();
    }

    public void save(String name){
        File file = new File(this.getDataFolder(), name);
        if (!file.exists()) this.saveResource(name, false);
    }

    private void updateConfig(String name){
        File configFile = new File(getDataFolder(), name);
        try {
            ConfigUpdater.update(instance, name, configFile, new ArrayList<>());
        } catch (IOException e) {
            getLogger().severe("Failed to update config file: " + e.getMessage());
        }
    }

    private void updateConfig(String name, List<String> excludedSections){
        File configFile = new File(getDataFolder(), name);
        try {
            ConfigUpdater.update(instance, name, configFile, excludedSections);
        } catch (IOException e) {
            getLogger().severe("Failed to update config file: " + e.getMessage());
        }
    }

    private YamlConfiguration saveAndUpdateConfig(String config){
        save(config);
        updateConfig(config);
        return saveConfig(config);
    }

    private YamlConfiguration saveAndUpdateConfig(String config, List<String> excludedSections){
        save(config);
        updateConfig(config, excludedSections);
        return saveConfig(config);
    }

    public static YamlConfiguration getPluginConfig() {
        return pluginConfig;
    }

    public static YamlConfiguration getLootConfig() {
        return lootConfig;
    }

    private void saveSpellConfigs(){
        for (String path : spellConfigPaths()) {
            saveConfig(path);
        }
    }

    /**
     * Every spell config copied out of the jar on first run, in the same order as
     * SpellRegistry.SPELL_IDS.
     *
     * <p>{@code debuff_options.yml} is the only {@code *_options.yml} in this list.
     * The other 36 (and the 29 wave-5 ones) are saved by each spell's own
     * loadConfiguration(), which is why they are absent here - see
     * spec section 10.3. Do NOT add them: a second saveConfig for a file the spell
     * already saves is harmless but pointless, and it widens the list that
     * SpellRegistryParityTest.savedSpellConfigIds() scans.
     *
     * <p>The literals stay as literals deliberately. Wave 4's
     * SpellRegistryParityTest reads this file as text with the pattern
     * "spells/([a-z0-9_]+)\\.yml" because after this refactor there are no saveConfig
     * calls left to match. Replacing the literals with "spells/" + id + ".yml"
     * would empty that roster and fail the wave-7 gate.
     */
    public static Set<String> spellConfigPaths() {
        return new LinkedHashSet<>(List.of(
                "spells/air_blades.yml",
                "spells/air_burst.yml",
                "spells/barrier.yml",
                "spells/blink.yml",
                "spells/buff.yml",
                "spells/debuff.yml",
                "spells/debuff_options.yml",
                "spells/dark_tendrils.yml",
                "spells/detect.yml",
                "spells/disguise.yml",
                "spells/dispel.yml",
                "spells/earthen_wall.yml",
                "spells/earth_spike.yml",
                "spells/earth_throw.yml",
                "spells/earthsplitter.yml",
                "spells/fireball.yml",
                "spells/fire_blast.yml",
                "spells/fire_wall.yml",
                "spells/flaming_volley.yml",
                "spells/geyser.yml",
                "spells/heal.yml",
                "spells/lesser_heal.yml",
                "spells/light_buff.yml",
                "spells/magic_missile.yml",
                "spells/meteor_shower.yml",
                "spells/radiance.yml",
                "spells/shadow_realm.yml",
                "spells/skeletal_knights.yml",
                "spells/stealth.yml",
                "spells/summon_zombies.yml",
                "spells/swift.yml",
                "spells/tornado.yml",
                "spells/tsunami.yml",
                "spells/tremor.yml",
                "spells/warp.yml",
                "spells/water_pulse.yml",
                "spells/water_sphere.yml",
                "spells/whirlpool.yml",
                "spells/wind_barrier.yml",
                "spells/wind_vortex.yml",
                "spells/flame_lance.yml",
                "spells/inferno_pilgrimage.yml",
                "spells/sunfire_barrage.yml",
                "spells/inferno_rite.yml",
                "spells/undertow.yml",
                "spells/everdrain.yml",
                "spells/thousand_waves.yml",
                "spells/tide_of_the_deep.yml",
                "spells/tectonic_strike.yml",
                "spells/fault_line.yml",
                "spells/continental_shift.yml",
                "spells/worldbreaker.yml",
                "spells/updraft.yml",
                "spells/thunderclap.yml",
                "spells/cyclone_nest.yml",
                "spells/stormcaller.yml",
                "spells/skywrath.yml",
                "spells/light_bolt.yml",
                "spells/radiant_brand.yml",
                "spells/choir_of_light.yml",
                "spells/apotheosis.yml",
                "spells/crown_of_dawn.yml",
                "spells/umbral_volley.yml",
                "spells/void_anchor.yml",
                "spells/oblivion.yml",
                "spells/arcane_ward.yml",
                "spells/waystone_recall.yml",
                "spells/echo_cast.yml",
                "spells/arcane_resonance.yml"
        ));
    }

    private void saveMobConfigs() {
        for (String path : mobConfigPaths()) {
            saveConfig(path);
        }
    }

    private void saveBrainConfigs() {
        for (String path : brainConfigPaths()) {
            saveConfig(path);
        }
    }

    public static Set<String> mobConfigPaths() {
        Set<String> paths = new LinkedHashSet<>(List.of(
                "mobs/allay.yml",
                "mobs/armadillo.yml",
                "mobs/axolotl.yml",
                "mobs/bat.yml",
                "mobs/bee.yml",
                "mobs/blaze.yml",
                "mobs/bogged.yml",
                "mobs/breeze.yml",
                "mobs/camel.yml",
                "mobs/cat.yml",
                "mobs/cave_spider.yml",
                "mobs/chicken.yml",
                "mobs/cod.yml",
                "mobs/cow.yml",
                "mobs/creeper.yml",
                "mobs/dolphin.yml",
                "mobs/donkey.yml",
                "mobs/drowned.yml",
                "mobs/elder_guardian.yml",
                "mobs/ender_dragon.yml",
                "mobs/enderman.yml",
                "mobs/evoker.yml",
                "mobs/fox.yml",
                "mobs/frog.yml",
                "mobs/ghast.yml",
                "mobs/glow_squid.yml",
                "mobs/goat.yml",
                "mobs/guardian.yml",
                "mobs/hoglin.yml",
                "mobs/horse.yml",
                "mobs/husk.yml",
                "mobs/illusioner.yml",
                "mobs/iron_golem.yml",
                "mobs/llama.yml",
                "mobs/magma_cube.yml",
                "mobs/mooshroom.yml",
                "mobs/mule.yml",
                "mobs/ocelot.yml",
                "mobs/panda.yml",
                "mobs/parrot.yml",
                "mobs/phantom.yml",
                "mobs/pig.yml",
                "mobs/piglin.yml",
                "mobs/piglin_brute.yml",
                "mobs/pillager.yml",
                "mobs/polar_bear.yml",
                "mobs/pufferfish.yml",
                "mobs/rabbit.yml",
                "mobs/ravager.yml",
                "mobs/salmon.yml",
                "mobs/sheep.yml",
                "mobs/shulker.yml",
                "mobs/silverfish.yml",
                "mobs/skeleton.yml",
                "mobs/skeleton_horse.yml",
                "mobs/slime.yml",
                "mobs/sniffer.yml",
                "mobs/snow_golem.yml",
                "mobs/spider.yml",
                "mobs/squid.yml",
                "mobs/stray.yml",
                "mobs/strider.yml",
                "mobs/tadpole.yml",
                "mobs/trader_llama.yml",
                "mobs/tropical_fish.yml",
                "mobs/turtle.yml",
                "mobs/vex.yml",
                "mobs/villager.yml",
                "mobs/vindicator.yml",
                "mobs/wandering_trader.yml",
                "mobs/warden.yml",
                "mobs/witch.yml",
                "mobs/wither.yml",
                "mobs/wither_skeleton.yml",
                "mobs/wolf.yml",
                "mobs/zoglin.yml",
                "mobs/zombie.yml",
                "mobs/zombie_horse.yml",
                "mobs/zombie_villager.yml",
                "mobs/zombified_piglin.yml"));
        for (MagicEntityType type : MagicEntityType.values()) {
            paths.add(type.getConfigPath());
        }
        return paths;
    }

    public static Set<String> brainConfigPaths() {
        Set<String> paths = new LinkedHashSet<>();
        for (MagicEntityType type : MagicEntityType.values()) {
            paths.add("brains/" + type.getId() + ".yml");
        }
        for (String id : MagicBrains.SUMMON_BRAIN_IDS) {
            paths.add("brains/" + id + ".yml");
        }
        return paths;
    }
}