package me.nagasonic.alkatraz.items.magic.service;

import me.nagasonic.alkatraz.api.magic.attribute.AttributeContribution;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.items.magic.config.SetBonusConfig;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentProfile;

import java.util.*;

public class SetBonusService {

    private static SetBonusService instance;
    private Map<String, SetBonusConfig.SetBonusData> setBonuses;
    private boolean loaded = false;

    public static SetBonusService getInstance() {
        if (instance == null) {
            instance = new SetBonusService();
        }
        return instance;
    }

    public void loadSetBonuses() {
        this.setBonuses = SetBonusConfig.loadAll();
        this.loaded = true;
        Alkatraz.logInfo("Loaded " + setBonuses.size() + " set bonus definitions.");
    }

    public List<AttributeContribution> getSetBonuses(EquipmentProfile profile) {
        List<AttributeContribution> contributions = new ArrayList<>();
        if (profile == null || setBonuses == null) {
            return contributions;
        }

        Map<String, List<MagicItemInstance>> setGroups = groupBySetName(profile);
        for (Map.Entry<String, List<MagicItemInstance>> entry : setGroups.entrySet()) {
            String setName = entry.getKey();
            List<MagicItemInstance> instances = entry.getValue();
            List<AttributeContribution> setContributions = applySetBonuses(setName, instances.size());
            contributions.addAll(setContributions);
        }

        return contributions;
    }

    private Map<String, List<MagicItemInstance>> groupBySetName(EquipmentProfile profile) {
        Map<String, List<MagicItemInstance>> groups = new HashMap<>();
        for (MagicItemInstance instance : profile.instances().values()) {
            MagicItemRegistries.ITEM_DEFINITIONS.get(instance.definitionKey()).ifPresent(definition -> {
                String setName = extractSetName(definition.getKey().getKey());
                groups.computeIfAbsent(setName, k -> new ArrayList<>()).add(instance);
            });
        }
        return groups;
    }

    private String extractSetName(String itemKey) {
        String[] parts = itemKey.split(":");
        String namespaceAware = parts.length > 1 ? parts[1] : parts[0];
        String itemName = namespaceAware;
        for (String suffix : new String[]{"_necklace", "_bracelet", "_leggings", "_pendant"}) {
            if (itemName.endsWith(suffix)) {
                return itemName.substring(0, itemName.length() - suffix.length());
            }
        }
        for (String suffix : new String[]{"_ring", "_robe", "_boots", "_hat"}) {
            if (itemName.endsWith(suffix)) {
                return itemName.substring(0, itemName.length() - suffix.length());
            }
        }
        return itemName;
    }

    private List<AttributeContribution> applySetBonuses(String setName, int pieceCount) {
        List<AttributeContribution> contributions = new ArrayList<>();
        if (!setBonuses.containsKey(setName)) {
            return contributions;
        }

        SetBonusConfig.SetBonusData data = setBonuses.get(setName);
        for (Map.Entry<Integer, List<SetBonusConfig.BonusEntry>> entry : data.bonuses.entrySet()) {
            int threshold = entry.getKey();
            if (pieceCount >= threshold) {
                for (SetBonusConfig.BonusEntry bonus : entry.getValue()) {
                    contributions.add(new AttributeContribution(
                            bonus.attribute,
                            bonus.value,
                            AttributeContribution.AttributeOperation.ADD,
                            AttributeContribution.AttributeSourceType.BUFF,
                            0
                    ));
                }
            }
        }

        return contributions;
    }
}
