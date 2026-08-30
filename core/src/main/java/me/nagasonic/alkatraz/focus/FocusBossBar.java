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
 * green >= 50%, yellow 25-50%, red < 25%.
 */
public final class FocusBossBar {

    private final Map<UUID, BossBar> bars = new HashMap<>();

    public void show(Player player) {
        UUID uuid = player.getUniqueId();
        if (bars.containsKey(uuid)) return;
        BossBar bar = Bukkit.createBossBar(
                Alkatraz.getLangManager().get("focus.bar.title"),
                BarColor.GREEN,
                BarStyle.SOLID);
        bars.put(uuid, bar);
        bar.addPlayer(player);
    }

    public void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    public boolean isVisible(Player player) {
        return bars.containsKey(player.getUniqueId());
    }

    public void update(Player player, double focus, double max) {
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) return;
        bar.setTitle(Alkatraz.getLangManager().get("focus.bar.title", "focus", Math.round(focus), "max", Math.round(max)));
        double progress = max <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, focus / max));
        bar.setProgress(progress);
        if (progress >= 0.5) {
            bar.setColor(BarColor.GREEN);
        } else if (progress >= 0.25) {
            bar.setColor(BarColor.YELLOW);
        } else {
            bar.setColor(BarColor.RED);
        }
    }

    public void hideAll() {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }
}