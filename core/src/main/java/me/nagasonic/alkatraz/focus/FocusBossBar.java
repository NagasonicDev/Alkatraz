package me.nagasonic.alkatraz.focus;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the per-player focus {@link BossBar}. Shown only while the player
 * visibly holds a casting tool (wand/grimoire) and refreshed as focus
 * changes. Color reflects remaining focus vs the effective maximum:
 * green &gt;= 50%, yellow 25-50%, red &lt; 25%.
 *
 * <p>Each entry remembers the last progress, colour and rounded focus/max that
 * were pushed to the client, so an unchanged tick costs three comparisons
 * instead of three packets plus a colour-code regex.</p>
 */
public final class FocusBossBar {

    private static final class Entry {
        private final BossBar bar;
        private double progress = Double.NaN;
        private BarColor color;
        private long roundedFocus = Long.MIN_VALUE;
        private long roundedMax = Long.MIN_VALUE;

        private Entry(BossBar bar, BarColor color) {
            this.bar = bar;
            this.color = color;
        }
    }

    private final Map<UUID, Entry> bars = new HashMap<>();

    public void show(Player player) {
        UUID uuid = player.getUniqueId();
        if (bars.containsKey(uuid)) return;
        BossBar bar = Bukkit.createBossBar(
                Alkatraz.getLangManager().get("focus.bar.title"),
                BarColor.GREEN,
                BarStyle.SOLID);
        bars.put(uuid, new Entry(bar, BarColor.GREEN));
        bar.addPlayer(player);
    }

    public void hide(Player player) {
        Entry entry = bars.remove(player.getUniqueId());
        if (entry != null) {
            entry.bar.removeAll();
        }
    }

    public boolean isVisible(Player player) {
        return bars.containsKey(player.getUniqueId());
    }

    public void update(Player player, double focus, double max) {
        Entry entry = bars.get(player.getUniqueId());
        if (entry == null) return;

        double progress = max <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, focus / max));
        if (progress != entry.progress) {
            entry.progress = progress;
            entry.bar.setProgress(progress);
        }

        BarColor color;
        if (progress >= 0.5) {
            color = BarColor.GREEN;
        } else if (progress >= 0.25) {
            color = BarColor.YELLOW;
        } else {
            color = BarColor.RED;
        }
        if (color != entry.color) {
            entry.color = color;
            entry.bar.setColor(color);
        }

        long roundedFocus = Math.round(focus);
        long roundedMax = Math.round(max);
        if (roundedFocus != entry.roundedFocus || roundedMax != entry.roundedMax) {
            entry.roundedFocus = roundedFocus;
            entry.roundedMax = roundedMax;
            entry.bar.setTitle(Alkatraz.getLangManager().get(
                    "focus.bar.title", "focus", roundedFocus, "max", roundedMax));
        }
    }

    public void hideAll() {
        for (Entry entry : bars.values()) {
            entry.bar.removeAll();
        }
        bars.clear();
    }
}
