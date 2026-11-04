package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.spells.effect.ActiveEffect;
import me.nagasonic.alkatraz.spells.effect.EffectRegistry;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * PlaceholderAPI handler exposing the player's active (timed) effects:
 * {@code %alkatraz_effect_source%}, {@code %alkatraz_effect_duration%},
 * {@code %alkatraz_effect_count%}, plus {@code _sources}, {@code _durations},
 * indexed ({@code source_0}) and per-spell ({@code has_<spellId>}) variants.
 */
public class EffectPlaceholder implements Placeholder {

    private final String name;

    public EffectPlaceholder() {
        this("effect");
    }

    public EffectPlaceholder(String name) {
        this.name = name == null || name.isBlank() ? "effect" : name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || params == null) return "";
        List<ActiveEffect> effects = EffectRegistry.effectsOn(player.getUniqueId());
        long now = System.currentTimeMillis();

        if (params.equals("count")) {
            return String.valueOf(effects.size());
        }
        if (params.equals("source")) {
            return effects.isEmpty() ? "" : effects.get(0).sourceSpellId();
        }
        if (params.equals("sources")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < effects.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(effects.get(i).sourceSpellId());
            }
            return sb.toString();
        }
        if (params.equals("duration")) {
            if (effects.isEmpty()) return "0";
            return String.valueOf(remainingSeconds(effects.get(0), now));
        }
        if (params.equals("durations")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < effects.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(remainingSeconds(effects.get(i), now));
            }
            return sb.toString();
        }

        if (params.startsWith("has_")) {
            String spellId = params.substring(4);
            for (ActiveEffect e : effects) {
                if (spellId.equals(e.sourceSpellId())) return "true";
            }
            return "false";
        }

        // Indexed access: source_0, duration_1, ...
        if (params.startsWith("source_")) {
            return indexed(effects, params.substring(7), true, now);
        }
        if (params.startsWith("duration_")) {
            return indexed(effects, params.substring(9), false, now);
        }

        return "";
    }

    private static String indexed(List<ActiveEffect> effects, String indexStr, boolean source, long now) {
        int idx;
        try {
            idx = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            return "";
        }
        if (idx < 0 || idx >= effects.size()) return "";
        ActiveEffect ae = effects.get(idx);
        return source ? ae.sourceSpellId() : String.valueOf(remainingSeconds(ae, now));
    }

    private static long remainingSeconds(ActiveEffect effect, long now) {
        if (effect.expiresAtMillis() == Long.MAX_VALUE) return -1;
        long remaining = effect.expiresAtMillis() - now;
        if (remaining <= 0) return 0;
        return remaining / 1000L;
    }
}