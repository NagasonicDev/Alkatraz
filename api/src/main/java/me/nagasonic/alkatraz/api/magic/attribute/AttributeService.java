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

/**
 * Central service that collects {@link AttributeContribution contributions} from all
 * registered {@link AttributeSource sources}, resolves them into final attribute values,
 * and produces {@link AttributeSnapshot snapshots} for any given entity.
 *
 * <p>Use {@link #get(LivingEntity, NamespacedKey)} to retrieve a single attribute value,
 * or {@link #snapshot(LivingEntity, TriggerContext)} to obtain a full snapshot of all
 * resolved attributes for an entity.</p>
 */
public final class AttributeService {

    private static AttributeService instance;

    /**
     * Returns the singleton instance, creating one if it does not yet exist.
     *
     * @return the current {@link AttributeService} instance
     */
    public static AttributeService getInstance() {
        if (instance == null) instance = new AttributeService();
        return instance;
    }

    /**
     * Replaces the current singleton instance with the provided service.
     * Intended for plugin initialisation and testing.
     *
     * @param service the service instance to set as the singleton
     */
    public static void setInstance(AttributeService service) {
        instance = service;
    }

    private final List<AttributeSource> sources = new ArrayList<>();

    /**
     * Registers an {@link AttributeSource} whose contributions will be included in
     * all future snapshot calculations.
     *
     * @param source the attribute source to register
     */
    public void registerSource(AttributeSource source) {
        sources.add(source);
    }

    /**
     * Removes all previously registered {@link AttributeSource sources}.
     */
    public void clearSources() {
        sources.clear();
    }

    /**
     * Resolves the final value of an attribute for an entity using an empty trigger context.
     *
     * @param entity    the living entity to query attributes for
     * @param attribute the {@link NamespacedKey} of the attribute to retrieve
     * @return the resolved attribute value, or the attribute's default if not present
     */
    public double get(LivingEntity entity, NamespacedKey attribute) {
        return snapshot(entity, TriggerContext.empty(entity)).get(attribute, defaultFor(attribute));
    }

    /**
     * Resolves the final value of an attribute for an entity within the given trigger context.
     *
     * @param entity    the living entity to query attributes for
     * @param attribute the {@link NamespacedKey} of the attribute to retrieve
     * @param context   the trigger context that may influence attribute collection
     * @return the resolved attribute value, or the attribute's default if not present
     */
    public double get(LivingEntity entity, NamespacedKey attribute, TriggerContext context) {
        return snapshot(entity, context).get(attribute, defaultFor(attribute));
    }

    /**
     * Builds a complete {@link AttributeSnapshot} for the given entity by collecting
     * contributions from all registered sources and resolving each attribute.
     *
     * @param entity  the living entity to snapshot attributes for
     * @param context the trigger context that may influence attribute collection
     * @return an immutable snapshot of all resolved attribute values
     */
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
