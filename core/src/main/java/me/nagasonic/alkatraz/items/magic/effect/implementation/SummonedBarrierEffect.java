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
            me.nagasonic.alkatraz.Alkatraz.logInfo("[DBG barrier] execute: actor is not a Player, returning");
            return;
        }
        if (context.sourceItem() == null) {
            me.nagasonic.alkatraz.Alkatraz.logInfo("[DBG barrier] execute: sourceItem is null, returning");
            return;
        }
        me.nagasonic.alkatraz.Alkatraz.logInfo("[DBG barrier] execute: actor=" + player.getName()
                + " slot=" + context.equipmentSlot() + " calling BarrierManager.start");
        String itemKey = CooldownCondition.stableItemKey(context.sourceItem());
        boolean created = BarrierManager.start(player, context.sourceItem(), context.equipmentSlot(), config, itemKey);
        me.nagasonic.alkatraz.Alkatraz.logInfo("[DBG barrier] execute: BarrierManager.start returned " + created);
    }
}
