package me.nagasonic.alkatraz.items.magic.equipment;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.attribute.AttributeService;
import me.nagasonic.alkatraz.api.magic.attribute.AttributeSnapshot;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.config.VerbosityLevel;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.util.StatUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.Map;

public final class EquipmentStatService {

    private static EquipmentStatService instance;

    public static EquipmentStatService getInstance() {
        if (instance == null) {
            instance = new EquipmentStatService();
        }
        return instance;
    }

    private EquipmentStatService() {}

    private static final NamespacedKey MAX_MANA = MagicKeys.alkatraz("max_mana");
    private static final NamespacedKey MANA_REGENERATION = MagicKeys.alkatraz("mana_regeneration");
    private static final NamespacedKey MAGIC_AFFINITY = MagicKeys.alkatraz("magic_affinity");
    private static final NamespacedKey MAGIC_RESISTANCE = MagicKeys.alkatraz("magic_resistance");
    private static final NamespacedKey MAX_CIRCLE = MagicKeys.alkatraz("max_circle");
    private static final NamespacedKey FIRE_AFFINITY = MagicKeys.alkatraz("fire_affinity");
    private static final NamespacedKey WATER_AFFINITY = MagicKeys.alkatraz("water_affinity");
    private static final NamespacedKey AIR_AFFINITY = MagicKeys.alkatraz("air_affinity");
    private static final NamespacedKey EARTH_AFFINITY = MagicKeys.alkatraz("earth_affinity");
    private static final NamespacedKey LIGHT_AFFINITY = MagicKeys.alkatraz("light_affinity");
    private static final NamespacedKey DARK_AFFINITY = MagicKeys.alkatraz("dark_affinity");
    private static final NamespacedKey FIRE_RESISTANCE = MagicKeys.alkatraz("fire_resistance");
    private static final NamespacedKey WATER_RESISTANCE = MagicKeys.alkatraz("water_resistance");
    private static final NamespacedKey AIR_RESISTANCE = MagicKeys.alkatraz("air_resistance");
    private static final NamespacedKey EARTH_RESISTANCE = MagicKeys.alkatraz("earth_resistance");
    private static final NamespacedKey LIGHT_RESISTANCE = MagicKeys.alkatraz("light_resistance");
    private static final NamespacedKey DARK_RESISTANCE = MagicKeys.alkatraz("dark_resistance");
    private static final NamespacedKey SPELL_POWER = MagicKeys.alkatraz("spell_power");
    private static final NamespacedKey CAST_TIME_MULTIPLIER = MagicKeys.alkatraz("cast_time_multiplier");

    public void syncEquipmentStats(Player player) {
        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
        if (profile == null) return;

        AttributeService attributeService = AttributeService.getInstance();
        AttributeSnapshot snapshot = attributeService.snapshot(player, TriggerContext.empty(player));
        Map<NamespacedKey, Double> contributed = snapshot.asMap();
        if (Alkatraz.isLoggable(VerbosityLevel.DEBUG)) {
            Alkatraz.logDebug("Syncing equipment stats for " + player.getName() + " (" + contributed.size() + " attributes)");
        }

        // Base mana comes from circle level; equipment contribution is additive on top
        double baseMana = StatUtils.getMaxMana(profile.getCircleLevel());
        double baseRegen = StatUtils.getManaRegen(profile.getCircleLevel());
        double equipMana = contributed.getOrDefault(MAX_MANA, 0.0);
        double equipRegen = contributed.getOrDefault(MANA_REGENERATION, 0.0);
        double equipMaxCircle = contributed.getOrDefault(MAX_CIRCLE, 0.0);
        profile.setMaxMana(Math.max(100, baseMana + equipMana));
        profile.setManaRegeneration(Math.max(0, baseRegen + equipRegen));
        profile.setEquipmentMaxCircle((int) equipMaxCircle);

        // Affinity and resistance are DELTA-reconciled, not reassigned from the
        // circle table. ResearchService writes research rewards straight onto
        // these stats (addMagicStat -> setDouble) and never re-adds them, so
        // assigning the snapshot total wiped the entire elemental research
        // branch on the next sync - which runs every 20 ticks. Subtracting our
        // own last contribution and adding the new one lets research and gear
        // compose, and makes repeat syncs idempotent across server restarts
        // because the last-applied totals are persisted on the profile.
        reconcile(profile, contributed, MAGIC_AFFINITY,
                MagicProfile::getMagicAffinity, MagicProfile::setMagicAffinity,
                MagicProfile::getEquipSyncMagicAffinity, MagicProfile::setEquipSyncMagicAffinity);
        reconcile(profile, contributed, MAGIC_RESISTANCE,
                MagicProfile::getMagicResistance, MagicProfile::setMagicResistance,
                MagicProfile::getEquipSyncMagicResistance, MagicProfile::setEquipSyncMagicResistance);

        reconcile(profile, contributed, FIRE_AFFINITY,
                MagicProfile::getFireAffinity, MagicProfile::setFireAffinity,
                MagicProfile::getEquipSyncFireAffinity, MagicProfile::setEquipSyncFireAffinity);
        reconcile(profile, contributed, WATER_AFFINITY,
                MagicProfile::getWaterAffinity, MagicProfile::setWaterAffinity,
                MagicProfile::getEquipSyncWaterAffinity, MagicProfile::setEquipSyncWaterAffinity);
        reconcile(profile, contributed, EARTH_AFFINITY,
                MagicProfile::getEarthAffinity, MagicProfile::setEarthAffinity,
                MagicProfile::getEquipSyncEarthAffinity, MagicProfile::setEquipSyncEarthAffinity);
        reconcile(profile, contributed, AIR_AFFINITY,
                MagicProfile::getAirAffinity, MagicProfile::setAirAffinity,
                MagicProfile::getEquipSyncAirAffinity, MagicProfile::setEquipSyncAirAffinity);
        reconcile(profile, contributed, LIGHT_AFFINITY,
                MagicProfile::getLightAffinity, MagicProfile::setLightAffinity,
                MagicProfile::getEquipSyncLightAffinity, MagicProfile::setEquipSyncLightAffinity);
        reconcile(profile, contributed, DARK_AFFINITY,
                MagicProfile::getDarkAffinity, MagicProfile::setDarkAffinity,
                MagicProfile::getEquipSyncDarkAffinity, MagicProfile::setEquipSyncDarkAffinity);

        reconcile(profile, contributed, FIRE_RESISTANCE,
                MagicProfile::getFireResistance, MagicProfile::setFireResistance,
                MagicProfile::getEquipSyncFireResistance, MagicProfile::setEquipSyncFireResistance);
        reconcile(profile, contributed, WATER_RESISTANCE,
                MagicProfile::getWaterResistance, MagicProfile::setWaterResistance,
                MagicProfile::getEquipSyncWaterResistance, MagicProfile::setEquipSyncWaterResistance);
        reconcile(profile, contributed, EARTH_RESISTANCE,
                MagicProfile::getEarthResistance, MagicProfile::setEarthResistance,
                MagicProfile::getEquipSyncEarthResistance, MagicProfile::setEquipSyncEarthResistance);
        reconcile(profile, contributed, AIR_RESISTANCE,
                MagicProfile::getAirResistance, MagicProfile::setAirResistance,
                MagicProfile::getEquipSyncAirResistance, MagicProfile::setEquipSyncAirResistance);
        reconcile(profile, contributed, LIGHT_RESISTANCE,
                MagicProfile::getLightResistance, MagicProfile::setLightResistance,
                MagicProfile::getEquipSyncLightResistance, MagicProfile::setEquipSyncLightResistance);
        reconcile(profile, contributed, DARK_RESISTANCE,
                MagicProfile::getDarkResistance, MagicProfile::setDarkResistance,
                MagicProfile::getEquipSyncDarkResistance, MagicProfile::setEquipSyncDarkResistance);

        setIfContributed(contributed, profile, SPELL_POWER,
                profile::setSpellPower, 0.0);
        setIfContributed(contributed, profile, CAST_TIME_MULTIPLIER,
                profile::setCastTimeMultiplier, 1.0);

        applySyncToPlayerProfile(profile);
    }

    private void reconcile(MagicProfile profile, Map<NamespacedKey, Double> contributed, NamespacedKey key,
                           java.util.function.ToDoubleFunction<MagicProfile> getter,
                           java.util.function.ObjDoubleConsumer<MagicProfile> setter,
                           java.util.function.ToDoubleFunction<MagicProfile> lastAppliedGetter,
                           java.util.function.ObjDoubleConsumer<MagicProfile> lastAppliedSetter) {
        double nowComputed = contributed.getOrDefault(key, 0.0);
        double lastApplied = lastAppliedGetter.applyAsDouble(profile);
        setter.accept(profile, getter.applyAsDouble(profile) - lastApplied + nowComputed);
        lastAppliedSetter.accept(profile, nowComputed);
    }

    private void setIfContributed(Map<NamespacedKey, Double> contributed, MagicProfile profile,
                                  NamespacedKey key, java.util.function.DoubleConsumer setter, double fallback) {
        Double value = contributed.get(key);
        setter.accept(value != null ? value : fallback);
    }

    private void applySyncToPlayerProfile(MagicProfile profile) {
        Player player = org.bukkit.Bukkit.getPlayer(profile.getOwner());
        if (player == null || !player.isOnline()) return;

        double newMana = Math.min(profile.getMana(), profile.getMaxMana());
        profile.setMana(newMana);

        profile.addManaPerSecond();
    }
}
