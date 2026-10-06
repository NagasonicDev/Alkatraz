package me.nagasonic.alkatraz.spells;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.events.CastEvent;
import me.nagasonic.alkatraz.events.PlayerCastEvent;
import me.nagasonic.alkatraz.events.PlayerSpellFailEvent;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.FocusConfig;
import me.nagasonic.alkatraz.config.VerbosityLevel;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.SpellOptionLoader;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.types.SpellRole;
import me.nagasonic.alkatraz.api.dom.Permission;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.StatUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public abstract class Spell {
    protected final String type;
    protected String id;
    protected String displayName;
    protected List<String> description;
    protected Element element;
    protected String code;
    protected BarColor masteryBarColor;
    protected SpellRole role = SpellRole.BURST;
    protected ItemStack guiItem;
    protected int guiCustomModelData;
    protected long cooldown;
    protected int cost;
    protected double castTime;
    protected int level;
    protected int requiredCircle;
    protected double requiredFocus = 0;
    protected boolean enabled;
    protected int maxMastery;

    // Spell options (defined once per spell, selections stored per-player)
    protected Map<String, SpellOption> options = new HashMap<>();

    // Lazily built per-instance cache of the SLOT options of each option group.
    private final Map<String, List<SpellOption>> groupOptionCache = new ConcurrentHashMap<>();

    // Tracks players whose cast was cancelled by the spell itself (e.g. invalid position)
    // so the framework can refund mana and skip cooldown/mastery.
    private final Set<UUID> castCancelledPlayers = new HashSet<>();

    // Tracks players whose cast was cancelled because focus dropped below this
    // spell's required focus mid-cast. Unlike castCancelledPlayers there is no
    // mana refund, cooldown or mastery.
    private final Set<UUID> focusCancelledPlayers = new HashSet<>();

    public Spell(String type) {
        this.type = type;
        setupOptions();
    }

    // Sound configuration
    protected String prepareSound = "BLOCK_ENCHANTMENT_TABLE_USE";
    protected float prepareSoundVolume = 0.5f;
    protected float prepareSoundPitch = 0.8f;
    protected String castSound = "ENTITY_EVOKER_CAST_SPELL";
    protected float castSoundVolume = 1.0f;
    protected float castSoundPitch = 1.0f;

    public final void loadOptions() {
        SpellOptionLoader.loadOptions(this, this.id);

        // If no options were loaded from YAML, fall back to the code-based hook.
        if (options.isEmpty()) {
            setupOptions();
        }
        groupOptionCache.clear();
    }

    /**
     * Override this to define spell options in code (called as a fallback when
     * no YAML options file is found).
     */
    protected void setupOptions() {
        // Subclasses can override to add their options programmatically.
    }

    public abstract void loadConfiguration();

    public abstract void castAction(Player p, ItemStack wand);

    public abstract void mobCastAction(Mob caster, ItemStack wand);

    public abstract int circleAction(LivingEntity caster, SpellPrepareEvent e);

    public abstract ItemStack getSpellBook();

    /**
     * Main spell casting method â€” handles validation, mana consumption, and
     * timing.
     */
    public void cast(Player p, ItemStack wand) {
        long startTime = System.nanoTime();
        if (Alkatraz.isLoggable(VerbosityLevel.HIGH)) {
            Alkatraz.logHigh("Spell cast started: " + getId() + " by " + p.getName());
        }
        MagicProfile profile = ProfileManager.getProfile(p, MagicProfile.class);

        // Check circle level requirement
        if (profile.getCircleLevel() < getRequiredCircleLevel()) {
            Utils.sendActionBar(p, lang().get("spells.cast.too_low_circle"));
            fireSpellFail(p, wand, "LOW_CIRCLE");
            return;
        }

        // Get modified mana cost (can be affected by spell options)
        int manaCost = getModifiedManaCost(p);

        if (!profile.canCast()) {
            Utils.sendActionBar(p, lang().get("spells.cast.cannot_cast_now"));
            fireSpellFail(p, wand, "CANNOT_CAST");
            return;
        }
        // Check mana
        if (profile.getMana() < manaCost) {
            Utils.sendActionBar(p, lang().get("spells.cast.not_enough_mana"));
            fireSpellFail(p, wand, "NOT_ENOUGH_MANA");
            return;
        }

        // Check focus requirement
        if (FocusConfig.isEnabled() && profile.getFocus() < getRequiredFocus()) {
            Utils.sendActionBar(p, lang().get("spells.cast.not_enough_focus"));
            fireSpellFail(p, wand, "NOT_ENOUGH_FOCUS");
            return;
        }

        // Check Cooldown
        if (!Permission.hasPermission(p, Permission.NO_COOLDOWN) && profile.getCooldown(this) != null) {
            long timePassed = System.currentTimeMillis() - profile.getCooldown(this);
            if (TimeUnit.MILLISECONDS.toSeconds(timePassed) < getCooldown()) {
                Utils.sendActionBar(p, lang().get("spells.cast.please_wait",
                        "time", TimeUnit.MILLISECONDS.toSeconds(getCooldown() * 1000 - timePassed)));
                fireSpellFail(p, wand, "COOLDOWN");
                return;
            }
        }

        // Check if player is alive
        if (p.isDead()) {
            fireSpellFail(p, wand, "DEAD");
            return;
        }
        // Create and fire spell prepare event
        PlayerSpellPrepareEvent castEvent = new PlayerSpellPrepareEvent(p, this, wand);
        Bukkit.getPluginManager().callEvent(castEvent);
        if (castEvent.isCancelled()) {
            fireSpellFail(p, wand, "CANCELLED");
            return;
        }
        // Set casting state
        profile.setCasting(true);
        profile.setCastingSpell(this);
        profile.setCastingWand(wand);



        // Consume mana
        StatUtils.subMana(p, manaCost);

        // Add Arcane Knowledge through the configurable progression source.
        StatUtils.addArcaneKnowledge(p, "spell_cast", getRequiredCircleLevel());

        // Send action bar message
        Utils.sendActionBar(p, ColorFormat.format(lang().get("spells.cast.casted") + " " + getDisplayName()));

        // Start circle animation
        int circleTaskId = circleAction(p, castEvent);
        profile.setCurrentCircleTaskId(circleTaskId);

        // Play the preparation sound
        playSound(p, prepareSound, prepareSoundVolume, prepareSoundPitch);

        // Calculate cast time (affected by wand and mastery)
        float baseCastTime  = getFullCastTime(wand, p, getCastTime());
        long  finalCastTime = calculateFinalCastTime(profile, baseCastTime);

        // Schedule spell execution after cast time
        int completionTaskId = Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                Alkatraz.getInstance(), () -> {
                    UUID uuid = p.getUniqueId();
                    if (focusCancelledPlayers.remove(uuid)) {
                        profile.setCasting(false);
                        profile.setCastingSpell(null);
                        return;
                    }
                    Bukkit.getServer().getScheduler().cancelTask(circleTaskId);
                    if (!castEvent.isCancelled()) {
                        PlayerCastEvent playerCastEvent = new PlayerCastEvent(p, Spell.this, wand);
                        Bukkit.getPluginManager().callEvent(playerCastEvent);
                        if (!playerCastEvent.isCancelled()) {
                            playSound(p, castSound, castSoundVolume, castSoundPitch);
                            long castEnd = System.nanoTime();
                            if (Alkatraz.isLoggable(VerbosityLevel.VERY_HIGH)) {
                                Alkatraz.logVeryHigh("Spell cast completed: " + getId() + " by " + p.getName() + " in " + ((castEnd - startTime) / 1_000_000) + "ms");
                            }
                            castAction(p, wand);
                            // Only grant cooldown/mastery if the spell didn't self-cancel
                            if (castCancelledPlayers.remove(uuid)) {
                                // Refund mana
                                StatUtils.addMana(p, manaCost);
                            } else {
                                profile.setCooldown(this, System.currentTimeMillis());
                                if (profile.getSpellMastery(this) < getMaxMastery()) {
                                    StatUtils.addSpellMastery(p, this, 1);
                                }
                            }
                        }
                    }
                    profile.setCasting(false);
                    profile.setCastingSpell(null);
                }, finalCastTime);
        profile.setCompletionTaskId(completionTaskId);
    }

    /**
     * Loads common spell configuration from YAML.
     */
    public void loadCommonConfig(YamlConfiguration spellConfig) {
        this.id            = spellConfig.getString("id");
        this.element       = Element.valueOf(spellConfig.getString("element"));

        // Name and description live in english.lang, not in this file: a spell's name and
        // the name of the book it is learned from are different strings, and keeping them
        // in two files is how light_bolt ended up showing "Scroll of the First Ray".
        String prefix = this.id.replace("_", "");
        String nameKey = "spells." + prefix + ".name";
        String langName = lang().get(nameKey);
        // LangManager returns the key itself on a miss, so this is a miss, not a name.
        // Falling back to the id keeps getDisplayName() non-null for the sort comparator
        // in SpellsMenu and the boss bar in StatUtils.
        this.displayName = langName.equals(nameKey)
                ? this.id.replace("_", " ")
                : langName;

        this.description = splitLangDescription(lang().get("spells." + prefix + ".description"));

        this.code          = spellConfig.getString("code");
        this.castTime      = spellConfig.getDouble("cast_time");
        this.cost          = spellConfig.getInt("mana_cost");
        this.level         = spellConfig.getInt("level");
        this.requiredCircle = spellConfig.getInt("required_circle", this.level);
        if (spellConfig.contains("required_focus")) {
            this.requiredFocus = spellConfig.getDouble("required_focus");
        } else {
            YamlConfiguration bundled = ConfigManager.getDefault("spells/" + this.id + ".yml");
            this.requiredFocus = bundled != null ? bundled.getDouble("required_focus", 0) : 0;
        }
        this.enabled       = spellConfig.getBoolean("enabled");
        this.maxMastery    = spellConfig.getInt("maximum_mastery");
        this.cooldown      = spellConfig.getLong("cooldown");
        this.masteryBarColor = BarColor.valueOf(spellConfig.getString("mastery_bar_color"));
        SpellRole parsedRole = SpellRole.parse(spellConfig.getString("role"));
        if (parsedRole == null) {
            if (spellConfig.isSet("role")) {
                Alkatraz.logWarning("Spell '" + this.id + "' has an unrecognised role: '"
                        + spellConfig.getString("role") + "'. Falling back to BURST.");
            }
            parsedRole = SpellRole.BURST;
        }
        this.role = parsedRole;
        this.guiItem       = Utils.materialFromString(spellConfig.getString("gui_item"));
        this.guiCustomModelData = spellConfig.getInt("gui_custom_model_data", 0);

        loadSoundConfig(spellConfig);
    }

    /**
     * Splits a lang description value into lore lines. english.lang has no line
     * continuations, so lines are separated by a literal backslash-n in the value; the
     * regex below is \\n, a literal backslash followed by n. Returns an empty list rather
     * than null so the lore loops in SpellsMenu and Spellbook cannot NPE on a missing key.
     */
    static List<String> splitLangDescription(String value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(value.split("\\\\n"));
    }

    private void loadSoundConfig(YamlConfiguration spellConfig) {
        prepareSound       = spellConfig.getString("prepare_sound", "BLOCK_ENCHANTMENT_TABLE_USE");
        prepareSoundVolume = (float) spellConfig.getDouble("prepare_sound_volume", 0.5);
        prepareSoundPitch  = (float) spellConfig.getDouble("prepare_sound_pitch", 0.8);
        castSound          = spellConfig.getString("cast_sound", "ENTITY_EVOKER_CAST_SPELL");
        castSoundVolume    = (float) spellConfig.getDouble("cast_sound_volume", 1.0);
        castSoundPitch     = (float) spellConfig.getDouble("cast_sound_pitch", 1.0);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    private void playSound(Player p, String soundName, float volume, float pitch) {
        try {
            Sound sound = Sound.valueOf(soundName);
            p.getWorld().playSound(p.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
        }
    }

    /**
     * Called by spell implementations when castAction() determines the spell
     * cannot proceed (e.g. no valid teleport destination). This signals the
     * framework to refund mana and skip cooldown/mastery for this cast.
     */
    protected final void cancelCast(Player p) {
        castCancelledPlayers.add(p.getUniqueId());
    }

    /**
     * Cancels an ongoing cast because the player's focus dropped below this
     * spell's required focus. The circle animation and the completion task are
     * cancelled immediately; mana is NOT refunded and no cooldown or mastery
     * is granted.
     */
    public final void cancelCastByFocus(Player p) {
        UUID uuid = p.getUniqueId();
        if (!focusCancelledPlayers.add(uuid)) return;
        MagicProfile profile = ProfileManager.getProfile(p, MagicProfile.class);
        if (profile == null) return;
        ItemStack wand = profile.getCastingWand();
        Bukkit.getServer().getScheduler().cancelTask(profile.getCurrentCircleTaskId());
        Bukkit.getServer().getScheduler().cancelTask(profile.getCompletionTaskId());
        focusCancelledPlayers.remove(uuid);
        profile.setCasting(false);
        profile.setCastingSpell(null);
        Utils.sendActionBar(p, lang().get("spells.cast.focus_lost"));
        fireSpellFail(p, wand, "FOCUS_LOST");
    }

    /**
     * Fires a {@link PlayerSpellFailEvent} for a cast that could not proceed.
     */
    private void fireSpellFail(Player p, ItemStack wand, String reason) {
        Bukkit.getPluginManager().callEvent(new PlayerSpellFailEvent(p, this, wand, reason));
    }

    /**
     * Mob cast entry point â€” fires CastEvent, then delegates to mobCastAction if not cancelled.
     */
    public void mobCast(Mob caster, ItemStack wand) {
        CastEvent castEvent = new CastEvent(caster, this, wand);
        Bukkit.getPluginManager().callEvent(castEvent);
        if (castEvent.isCancelled()) return;
        mobCastAction(caster, wand);
    }

    public boolean canMobCast(Mob mob) {
        return true;
    }

    // ============================================
    // Spell Options Management
    // ============================================

    public void addOption(SpellOption option) {
        options.put(option.getId(), option);
        groupOptionCache.clear();
    }

    public SpellOption getOption(String optionId) {
        return options.get(optionId);
    }

    public Map<String, SpellOption> getAllOptions() {
        return Collections.unmodifiableMap(options);
    }

    /**
     * Returns the SLOT options of a group ordered by slot index. The list is
     * cached per spell instance because the option graph never changes at
     * runtime; a {@code /alkatraz reload} builds brand new {@code Spell}
     * instances, so the cache is discarded with them.
     */
    public List<SpellOption> getGroupOptions(String groupId) {
        return groupOptionCache.computeIfAbsent(groupId, key ->
                options.values().stream()
                        .filter(o -> key.equals(o.getGroup()))
                        .filter(o -> o.getRole() == SpellOption.OptionRole.SLOT)
                        .sorted(Comparator.comparingInt(SpellOption::getSlotIndex))
                        .toList());
    }

    // ============================================
    // Player Profile Helpers
    // ============================================

    protected boolean hasSpellTag(Player caster, Spell spell, String tag) {
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        return profile.hasSpellTag(spell, tag);
    }

    protected double getModifiedStat(Player caster, String statName, double baseValue) {
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);

        List<MagicProfile.SpellModifier> modifiers = profile.getSpellModifiers(this, statName);
        if (!modifiers.isEmpty()) {
            double setSum = 0;
            int setCount = 0;
            for (MagicProfile.SpellModifier modifier : modifiers) {
                StatModifierImpact.ModifierType type = modifierType(profile, modifier);
                if (type == StatModifierImpact.ModifierType.SET) {
                    setSum += modifier.value();
                    setCount++;
                }
            }

            double val = setCount > 0 ? setSum / setCount : baseValue;

            for (MagicProfile.SpellModifier modifier : modifiers) {
                StatModifierImpact.ModifierType type = modifierType(profile, modifier);
                if (type == StatModifierImpact.ModifierType.MULTIPLY) val *= modifier.value();
            }
            for (MagicProfile.SpellModifier modifier : modifiers) {
                StatModifierImpact.ModifierType type = modifierType(profile, modifier);
                if (type == StatModifierImpact.ModifierType.ADD) val += modifier.value();
            }
            return Double.isFinite(val) ? val : baseValue;
        }

        return baseValue;
    }

    private static final Map<String, StatModifierImpact.ModifierType> MODIFIER_TYPES =
            new ConcurrentHashMap<>();

    private static StatModifierImpact.ModifierType modifierType(
            MagicProfile profile, MagicProfile.SpellModifier modifier) {
        String typeStr = profile.getSpellModifierType(modifier);
        if (typeStr == null) return null;
        StatModifierImpact.ModifierType cached = MODIFIER_TYPES.get(typeStr);
        if (cached != null) return cached;
        StatModifierImpact.ModifierType parsed = StatModifierImpact.ModifierType.valueOf(typeStr);
        MODIFIER_TYPES.put(typeStr, parsed);
        return parsed;
    }

    protected int getModifiedManaCost(Player caster) {
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);

        List<MagicProfile.SpellModifier> mods = profile.getSpellModifiers(this, "mana_cost");
        if (!mods.isEmpty()) {
            double value = 0;
            for (MagicProfile.SpellModifier mod : mods) value += mod.value();
            return (int) Math.max(0, cost + value);
        }

        return cost;
    }

    public long calculateFinalCastTime(MagicProfile profile, float baseCastTime) {
        float castTimeInTicks = baseCastTime * 20;
        if (profile.getSpellMastery(this) >= getMaxMastery()) {
            castTimeInTicks *= 0.75f;
        }
        return (long) castTimeInTicks;
    }

    public float getFullCastTime(ItemStack wand, double spellCastTime) {
        double wandCastTime;
        if (wand != null){
            if (MagicItemStack.isMagicItem(wand)) {
                wandCastTime = MagicItemStack.readDefinition(wand)
                        .map(def -> def.attributes().getOrDefault(MagicKeys.alkatraz("cast_time_multiplier"), 1.0))
                        .orElse(1.0);
            } else {
                wandCastTime = NBT.get(wand, nbt -> (Double) nbt.getDouble("cast_time_multiplier"));
                if (wandCastTime == 0.0) wandCastTime = 1.0;
            }
        }else{
            wandCastTime = 1.0;
        }
        return (float) wandCastTime * (float) spellCastTime;
    }

    public float getFullCastTime(ItemStack wand, Player player, double spellCastTime) {
        if (player != null && wand != null && MagicItemStack.isMagicItem(wand)) {
            MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
            if (profile != null) {
                double resolved = profile.getCastTimeMultiplier();
                if (resolved <= 0.0) resolved = 1.0;
                return (float) resolved * (float) spellCastTime;
            }
        }
        return getFullCastTime(wand, spellCastTime);
    }

    public double calcPower(double base, LivingEntity target, Player caster) {
        MagicProfile casterProfile = ProfileManager.getProfile(caster, MagicProfile.class);
        double casterAffinity = casterProfile.getAffinity(getElement());

        double targetResistance;
        if (target instanceof Player t) {
            MagicProfile targetProfile = ProfileManager.getProfile(t, MagicProfile.class);
            targetResistance = targetProfile.getResistance(getElement());
        } else {
            targetResistance = Utils.getEntityResistance(getElement(), target);
        }

        return base * (1 + ((casterAffinity - targetResistance) / 100));
    }

    // ============================================
    // Getters
    // ============================================

    public String getType()          { return type; }
    public String getId()            { return id; }
    public Element getElement()      { return element; }
    public String getDisplayName()   { return displayName; }
    public List<String> getDescription() { return description; }
    public String getCode()          { return code; }
    public int getCost()             { return cost; }
    public long getCooldown()        { return cooldown; }
    public double getCastTime()      { return castTime; }
    public int getMaxMastery()       { return maxMastery; }
    public int getLevel()            { return level; }
    public int getRequiredCircleLevel() { return requiredCircle; }
    public double getRequiredFocus() { return requiredFocus; }
    public BarColor getMasteryBarColor() { return masteryBarColor; }
    public SpellRole getRole() { return role; }
    public ItemStack getGuiItem()    { return guiItem; }
    public int getGuiCustomModelData() { return guiCustomModelData; }
    public boolean isEnabled()       { return enabled; }

    /**
     * Reads magic power from a wand, supporting both new PDC magic items and legacy NBT wands.
     * For new PDC items, reads from the item definition's spell_power attribute.
     * For legacy wands, reads from NBT magic_power key.
     * Returns 1.0 (a neutral multiplier) if wand is null or has no power data.
     * <p>
     * The raw {@code alkatraz:spell_power} value is interpreted as a PERCENT and the
     * returned value is {@code 1 + raw/100}: a stored spell_power of 10 yields a 10%
     * bonus (1.10x total multiplier).
     * <p>
     * Callers that have a player should use {@link #getWandPower(ItemStack, Player)},
     * which sources the same attribute from the equipment profile. That profile snapshot
     * already includes the MAIN_HAND slot, so routing through it counts the wand exactly
     * once; this overload stays the only option for mob casting and for callers with no
     * profile.
     */
    public static double getWandPower(ItemStack wand) {
        if (wand == null) return 1.0;
        double raw;
        if (MagicItemStack.isMagicItem(wand)) {
            raw = MagicItemStack.readInstance(wand)
                    .flatMap(instance -> MagicItemRegistries.ITEM_DEFINITIONS.get(instance.definitionKey()))
                    .map(def -> def.attributes().getOrDefault(MagicKeys.alkatraz("spell_power"), 0.0))
                    .orElse(0.0);
        } else {
            raw = NBT.get(wand, nbt -> {
                if (nbt.hasTag("magic_power")) return nbt.getDouble("magic_power");
                return 0.0;
            });
        }
        return 1.0 + raw / 100.0;
    }

    /**
     * Reads spell power from a wand, accounting for player-attributed bonuses.
     * Returns 1.0 for null wand (neutral multiplier).
     */
    public static double getWandPowerOrDefault(ItemStack wand) {
        double power = getWandPower(wand);
        return power == 0.0 && wand != null ? 1.0 : power;
    }

    /**
     * Reads spell power from a wand, accounting for player-attributed bonuses.
     * Returns 1.0 for null wand (neutral multiplier).
     * <p>
     * The profile's {@code spell_power} stat is a PERCENT, so this returns
     * {@code 1 + spellPower/100}. It deliberately does NOT multiply by
     * {@link #getWandPower(ItemStack)} on top: {@code EquipmentService.buildProfile}
     * scans MAIN_HAND, so the wand's own attribute is already inside the profile total
     * and re-reading the item counted it a second time. It also treated the two sources
     * as different units, which turned a fully kitted profile into a
     * {@code (1 + 20/100) * (1 + 43) = 52.8x} multiplier instead of
     * {@code 1 + 43/100 = 1.43x}.
     * <p>
     * Falls back to the one-arg item-definition overload when there is no player or no
     * loaded profile (mob casting, offline lookups).
     *
     * @param wand   the wand item stack, may be null
     * @param player the casting player, may be null
     * @return the spell power multiplier; 1.0 when the profile carries no spell power
     */
    public static double getWandPower(ItemStack wand, Player player) {
        if (player == null) return getWandPower(wand);
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        if (profile == null) return getWandPower(wand);
        return 1.0 + profile.getSpellPower() / 100.0;
    }

    public static double getWandPowerOrDefault(ItemStack wand, Player player) {
        double power = getWandPower(wand, player);
        return power == 0.0 && wand != null ? 1.0 : power;
    }
}
