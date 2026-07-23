package me.nagasonic.alkatraz.spells;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.events.CastEvent;
import me.nagasonic.alkatraz.events.PlayerCastEvent;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.configuration.SpellOption;
import me.nagasonic.alkatraz.spells.configuration.SpellOptionLoader;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.dom.Permission;
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
    protected ItemStack guiItem;
    protected long cooldown;
    protected int cost;
    protected double castTime;
    protected int level;
    protected int requiredCircle;
    protected boolean enabled;
    protected int maxMastery;

    // Spell options (defined once per spell, selections stored per-player)
    protected Map<String, SpellOption> options = new HashMap<>();

    // Tracks players whose cast was cancelled by the spell itself (e.g. invalid position)
    // so the framework can refund mana and skip cooldown/mastery.
    private final Set<UUID> castCancelledPlayers = new HashSet<>();

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
        Alkatraz.logHigh("Spell cast started: " + getId() + " by " + p.getName());
        MagicProfile profile = ProfileManager.getProfile(p, MagicProfile.class);

        // Check circle level requirement
        if (profile.getCircleLevel() < getRequiredCircleLevel()) {
            Utils.sendActionBar(p, lang().get("spells.cast.too_low_circle"));
            return;
        }

        // Get modified mana cost (can be affected by spell options)
        int manaCost = getModifiedManaCost(p);

        if (!profile.canCast()) {
            Utils.sendActionBar(p, lang().get("spells.cast.cannot_cast_now"));
            return;
        }
        // Check mana
        if (profile.getMana() < manaCost) {
            Utils.sendActionBar(p, lang().get("spells.cast.not_enough_mana"));
            return;
        }

        // Check Cooldown
        if (!Permission.hasPermission(p, Permission.NO_COOLDOWN) && profile.getCooldown(this) != null) {
            long timePassed = System.currentTimeMillis() - profile.getCooldown(this);
            if (TimeUnit.MILLISECONDS.toSeconds(timePassed) < getCooldown()) {
                Utils.sendActionBar(p, lang().get("spells.cast.please_wait",
                        "time", TimeUnit.MILLISECONDS.toSeconds(getCooldown() * 1000 - timePassed)));
                return;
            }
        }

        // Check if player is alive
        if (p.isDead()) return;
        // Create and fire spell prepare event
        PlayerSpellPrepareEvent castEvent = new PlayerSpellPrepareEvent(p, this, wand);
        Bukkit.getPluginManager().callEvent(castEvent);
        if (castEvent.isCancelled()) return;
        // Set casting state
        profile.setCasting(true);



        // Consume mana
        profile.setMana(profile.getMana() - manaCost);

        // Add Arcane Knowledge through the configurable progression source.
        StatUtils.addArcaneKnowledge(p, "spell_cast", getRequiredCircleLevel());

        // Send action bar message
        Utils.sendActionBar(p, ColorFormat.format(lang().get("spells.cast.casted") + " " + getDisplayName()));

        // Start circle animation
        int circleTaskId = circleAction(p, castEvent);

        // Play the preparation sound
        playSound(p, prepareSound, prepareSoundVolume, prepareSoundPitch);

        // Calculate cast time (affected by wand and mastery)
        float baseCastTime  = getFullCastTime(wand, getCastTime());
        long  finalCastTime = calculateFinalCastTime(profile, baseCastTime);

        // Schedule spell execution after cast time
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                Alkatraz.getInstance(), () -> {
                    Bukkit.getServer().getScheduler().cancelTask(circleTaskId);
                    if (!castEvent.isCancelled()) {
                        PlayerCastEvent playerCastEvent = new PlayerCastEvent(p, Spell.this, wand);
                        Bukkit.getPluginManager().callEvent(playerCastEvent);
                        if (!playerCastEvent.isCancelled()) {
                            playSound(p, castSound, castSoundVolume, castSoundPitch);
                            long castEnd = System.nanoTime();
                            Alkatraz.logVeryHigh("Spell cast completed: " + getId() + " by " + p.getName() + " in " + ((castEnd - startTime) / 1_000_000) + "ms");
                            castAction(p, wand);
                            // Only grant cooldown/mastery if the spell didn't self-cancel
                            UUID uuid = p.getUniqueId();
                            if (castCancelledPlayers.remove(uuid)) {
                                // Refund mana
                                profile.setMana(profile.getMana() + manaCost);
                            } else {
                                profile.setCooldown(this, System.currentTimeMillis());
                                if (profile.getSpellMastery(this) < getMaxMastery()) {
                                    StatUtils.addSpellMastery(p, this, 1);
                                }
                            }
                        }
                    }
                    profile.setCasting(false);
                }, finalCastTime);
    }

    /**
     * Loads common spell configuration from YAML.
     */
    public void loadCommonConfig(YamlConfiguration spellConfig) {
        this.id            = spellConfig.getString("id");
        this.displayName   = spellConfig.getString("display_name");
        this.description   = spellConfig.getStringList("description");
        this.element       = Element.valueOf(spellConfig.getString("element"));

        LangManager lang = Alkatraz.getLangManager();
        String nameKey = "override.spells." + this.id + ".name";
        String langName = lang.get(nameKey);
        if (!langName.equals(nameKey)) {
            this.displayName = langName;
        } else {
            this.displayName = ColorFormat.format(spellConfig.getString("display_name"));
        }

        String descKey = "override.spells." + this.id + ".description";
        String langDesc = lang.get(descKey);
        if (!langDesc.equals(descKey)) {
            this.description = Arrays.asList(ColorFormat.format(langDesc).split("\\n"));
        } else {
            this.description = spellConfig.getStringList("description");
        }
        this.code          = spellConfig.getString("code");
        this.castTime      = spellConfig.getDouble("cast_time");
        this.cost          = spellConfig.getInt("mana_cost");
        this.level         = spellConfig.getInt("level");
        this.requiredCircle = spellConfig.getInt("required_circle", this.level);
        this.enabled       = spellConfig.getBoolean("enabled");
        this.maxMastery    = spellConfig.getInt("maximum_mastery");
        this.cooldown      = spellConfig.getLong("cooldown");
        this.masteryBarColor = BarColor.valueOf(spellConfig.getString("mastery_bar_color"));
        this.guiItem       = Utils.materialFromString(spellConfig.getString("gui_item"));

        loadSoundConfig(spellConfig);
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
    }

    public SpellOption getOption(String optionId) {
        return options.get(optionId);
    }

    public Map<String, SpellOption> getAllOptions() {
        return new HashMap<>(options);
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

        if (profile.hasSpellModifier(this, statName)) {
            List<MagicProfile.SpellModifier> modifiers =
                    profile.getSpellModifiers(this, statName);
            List<MagicProfile.SpellModifier> adds  = new ArrayList<>();
            List<MagicProfile.SpellModifier> mults = new ArrayList<>();
            List<MagicProfile.SpellModifier> sets  = new ArrayList<>();

            for (MagicProfile.SpellModifier modifier : modifiers) {
                String typeStr = profile.getSpellModifierType(modifier);
                if (typeStr != null) {
                    StatModifierImpact.ModifierType type =
                            StatModifierImpact.ModifierType.valueOf(typeStr);
                    if (type == StatModifierImpact.ModifierType.ADD)      adds.add(modifier);
                    if (type == StatModifierImpact.ModifierType.MULTIPLY) mults.add(modifier);
                    if (type == StatModifierImpact.ModifierType.SET)      sets.add(modifier);
                }
            }
            double val;
            if (!sets.isEmpty()) {
                val = 0;
                for (MagicProfile.SpellModifier setmod : sets) val += setmod.value();
                val /= sets.size();
            } else {
                val = baseValue;
            }
            for (MagicProfile.SpellModifier mult   : mults) val *= mult.value();
            for (MagicProfile.SpellModifier addmod : adds)  val += addmod.value();
            return Double.isFinite(val) ? val : baseValue;
        }

        return baseValue;
    }

    protected int getModifiedManaCost(Player caster) {
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);

        if (profile.hasSpellModifier(this, "mana_cost")) {
            List<MagicProfile.SpellModifier> mods =
                    profile.getSpellModifiers(this, "mana_cost");
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
    public BarColor getMasteryBarColor() { return masteryBarColor; }
    public ItemStack getGuiItem()    { return guiItem; }
    public boolean isEnabled()       { return enabled; }

    /**
     * Reads magic power from a wand, supporting both new PDC magic items and legacy NBT wands.
     * For new PDC items, reads from the item definition's spell_power attribute.
     * For legacy wands, reads from NBT magic_power key.
     * Returns 0.0 if wand is null or has no power data.
     * <p>
     * The returned value is a percentage-based multiplier:
     * a stored spell_power of 10 yields a 10% bonus (1.10x total multiplier).
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
}
