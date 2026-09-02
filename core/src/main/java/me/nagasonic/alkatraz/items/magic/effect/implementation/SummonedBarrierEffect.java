package me.nagasonic.alkatraz.items.magic.effect.implementation;

import me.nagasonic.alkatraz.api.magic.effect.Effect;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.items.magic.barrier.BarrierConfig;
import me.nagasonic.alkatraz.items.magic.barrier.BarrierManager;
import me.nagasonic.alkatraz.items.magic.condition.implementation.CooldownCondition;
import org.bukkit.entity.Player;

import java.util.Map;

public final class SummonedBarrierEffect implements Effect {

    private final BarrierConfig config;

    private SummonedBarrierEffect(BarrierConfig config) {
        this.config = config;
    }

    public static Effect fromConfig(Map<String, Object> config) {
        return new SummonedBarrierEffect(BarrierConfig.fromConfig(config));
    }

    @Override
    public void execute(TriggerContext context) {
        if (!(context.actor() instanceof Player player)) {
            return;
        }
        if (context.sourceItem() == null) {
            return;
        }
        String itemKey = CooldownCondition.stableItemKey(context.sourceItem());
        BarrierManager.start(player, context.sourceItem(), context.equipmentSlot(), config, itemKey);
    }
}
