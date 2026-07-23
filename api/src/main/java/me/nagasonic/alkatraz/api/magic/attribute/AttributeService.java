package me.nagasonic.alkatraz.api.magic.attribute;

import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AttributeService {

    private static AttributeService instance;

    public static AttributeService getInstance() {
        if (instance == null) instance = new AttributeService();
        return instance;
    }

    public static void setInstance(AttributeService service) {
        instance = service;
    }

    private final List<AttributeSource> sources = new ArrayList<>();

    public void registerSource(AttributeSource source) {
        sources.add(source);
    }

    public void clearSources() {
        sources.clear();
    }

    public double get(LivingEntity entity, NamespacedKey attribute) {
        return snapshot(entity, TriggerContext.empty(entity)).get(attribute, defaultFor(attribute));
    }

    public double get(LivingEntity entity, NamespacedKey attribute, TriggerContext context) {
        return snapshot(entity, context).get(attribute, defaultFor(attribute));
    }

    public AttributeSnapshot snapshot(LivingEntity entity, TriggerContext context) {
        Map<NamespacedKey, List<AttributeContribution>> grouped = new HashMap<>();

        for (AttributeSource source : sources) {
            try {
                for (AttributeContribution contribution : source.collect(entity, context)) {
                    grouped.computeIfAbsent(contribution.attribute(), k -> new ArrayList<>()).add(contribution);
                }
            } catch (Exception e) {
                java.util.logging.Logger.getLogger("Alkatraz").warning(
                    "AttributeSource " + source.getClass().getSimpleName() + " failed for " + entity.getName() + ": " + e.getMessage());
            }
        }

        Map<NamespacedKey, Double> resolved = new HashMap<>();
        for (Map.Entry<NamespacedKey, List<AttributeContribution>> entry : grouped.entrySet()) {
            resolved.put(entry.getKey(), resolve(entry.getValue()));
        }
        return new AttributeSnapshot(resolved);
    }

    private static double resolve(List<AttributeContribution> contributions) {
        contributions.sort(Comparator
                .comparing((AttributeContribution c) -> c.sourceType())
                .thenComparingInt(AttributeContribution::priority));

        double value = 0;
        boolean initialized = false;

        Map<AttributeContribution.AttributeOperation, Double> pending = new EnumMap<>(AttributeContribution.AttributeOperation.class);

        for (AttributeContribution contribution : contributions) {
            switch (contribution.operation()) {
                case SET -> pending.put(AttributeContribution.AttributeOperation.SET, contribution.value());
                case ADD -> {
                    double addValue = contribution.value();
                    pending.merge(AttributeContribution.AttributeOperation.ADD, addValue, Double::sum);
                }
                case MULTIPLY -> {
                    double multiplyValue = contribution.value();
                    if (multiplyValue != 0) {
                        pending.merge(AttributeContribution.AttributeOperation.MULTIPLY, multiplyValue, (a, b) -> a * b);
                    }
                }
            }
        }

        if (pending.containsKey(AttributeContribution.AttributeOperation.SET)) {
            value = pending.get(AttributeContribution.AttributeOperation.SET);
            initialized = true;
        }

        if (!initialized) {
            value = 0;
        }

        if (pending.containsKey(AttributeContribution.AttributeOperation.ADD)) {
            value += pending.get(AttributeContribution.AttributeOperation.ADD);
        }

        if (pending.containsKey(AttributeContribution.AttributeOperation.MULTIPLY)) {
            value *= pending.get(AttributeContribution.AttributeOperation.MULTIPLY);
        }

        return value;
    }

    private static double defaultFor(NamespacedKey attribute) {
        return MagicItemRegistries.ATTRIBUTE_TYPES.get(attribute)
                .map(AttributeType::defaultValue)
                .orElse(0D);
    }
}
