package me.nagasonic.alkatraz.items.magic.equipment;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.attribute.AttributeService;
import me.nagasonic.alkatraz.api.magic.attribute.AttributeSnapshot;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
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

    public void syncEquipmentStats(Player player) {
        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);

        AttributeService attributeService = AttributeService.getInstance();
        AttributeSnapshot snapshot = attributeService.snapshot(player, TriggerContext.empty(player));
        Map<NamespacedKey, Double> contributed = snapshot.asMap();
        Alkatraz.logDebug("Syncing equipment stats for " + player.getName() + " (" + contributed.size() + " attributes)");

        setIfContributed(contributed, profile, MagicKeys.alkatraz("max_mana"),
                v -> profile.setMaxMana(v));
        setIfContributed(contributed, profile, MagicKeys.alkatraz("mana_regeneration"),
                v -> profile.setManaRegeneration(v));
        setIfContributed(contributed, profile, MagicKeys.alkatraz("fire_affinity"),
                v -> profile.setFireAffinity(v));
        setIfContributed(contributed, profile, MagicKeys.alkatraz("water_affinity"),
                v -> profile.setWaterAffinity(v));
        setIfContributed(contributed, profile, MagicKeys.alkatraz("air_affinity"),
                v -> profile.setAirAffinity(v));
        setIfContributed(contributed, profile, MagicKeys.alkatraz("earth_affinity"),
                v -> profile.setEarthAffinity(v));
        setIfContributed(contributed, profile, MagicKeys.alkatraz("light_affinity"),
                v -> profile.setLightAffinity(v));
        setIfContributed(contributed, profile, MagicKeys.alkatraz("dark_affinity"),
                v -> profile.setDarkAffinity(v));
        setIfContributed(contributed, profile, MagicKeys.alkatraz("fire_resistance"),
                v -> profile.setFireResistance(v));
        setIfContributed(contributed, profile, MagicKeys.alkatraz("water_resistance"),
                v -> profile.setWaterResistance(v));
        setIfContributed(contributed, profile, MagicKeys.alkatraz("air_resistance"),
                v -> profile.setAirResistance(v));
        setIfContributed(contributed, profile, MagicKeys.alkatraz("earth_resistance"),
                v -> profile.setEarthResistance(v));
        setIfContributed(contributed, profile, MagicKeys.alkatraz("light_resistance"),
                v -> profile.setLightResistance(v));
        setIfContributed(contributed, profile, MagicKeys.alkatraz("dark_resistance"),
                v -> profile.setDarkResistance(v));

        setIfContributed(contributed, profile, MagicKeys.alkatraz("spell_power"),
                v -> profile.addMagicStat("spell_power", v, "set"));

        applySyncToPlayerProfile(profile);
    }

    private void setIfContributed(Map<NamespacedKey, Double> contributed, MagicProfile profile,
                                  NamespacedKey key, java.util.function.DoubleConsumer setter) {
        Double value = contributed.get(key);
        if (value != null) {
            setter.accept(value);
        } else {
            // Reset stat when equipment is removed
            setter.accept(0.0);
        }
    }

    private void applySyncToPlayerProfile(MagicProfile profile) {
        Player player = org.bukkit.Bukkit.getPlayer(profile.getOwner());
        if (player == null || !player.isOnline()) return;

        double newMana = Math.min(profile.getMana(), profile.getMaxMana());
        profile.setMana(newMana);

        profile.addManaPerSecond();
    }
}
