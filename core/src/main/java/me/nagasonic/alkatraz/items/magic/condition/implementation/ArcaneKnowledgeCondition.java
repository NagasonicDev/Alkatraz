package me.nagasonic.alkatraz.items.magic.condition.implementation;

import me.nagasonic.alkatraz.api.magic.condition.Condition;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;

import java.util.Map;

public final class ArcaneKnowledgeCondition implements Condition {

    private final double amount;

    public ArcaneKnowledgeCondition(double amount) {
        this.amount = amount;
    }

    public static Condition fromConfig(Map<String, Object> config) {
        Object rawAmount = config.getOrDefault("amount", 0);
        double amount = rawAmount instanceof Number number
                ? number.doubleValue()
                : Double.parseDouble(String.valueOf(rawAmount));
        return new ArcaneKnowledgeCondition(amount);
    }

    @Override
    public boolean test(TriggerContext context) {
        return context.playerActor()
                .map(player -> ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class).getArcaneKnowledge() >= amount)
                .orElse(false);
    }
}
