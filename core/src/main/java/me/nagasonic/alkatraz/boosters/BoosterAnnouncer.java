package me.nagasonic.alkatraz.boosters;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;

public class BoosterAnnouncer {

    protected BoosterAnnouncer() {}

    private static LangManager lang() {
        try { return Alkatraz.getLangManager(); } catch (Exception e) { return null; }
    }

    private static String fmtDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h";
    }

    private static String multiplierText(double multiplier) {
        if (multiplier == Math.floor(multiplier)) return String.valueOf((long) multiplier);
        return String.valueOf(multiplier);
    }

    public static void announceStart(String boosterId, String name, double multiplier,
                                     long durationSeconds) {
        announceStart(Bukkit.getOnlinePlayers(), boosterId, name, multiplier, durationSeconds, "server");
    }

    public static void announceStart(Player player, String boosterId, String name, double multiplier,
                                     long durationSeconds) {
        announceStart(java.util.List.of(player), boosterId, name, multiplier, durationSeconds, "server");
    }

    public static void announceStart(Collection<? extends Player> players, String boosterId, String name,
                                     double multiplier, long durationSeconds, String scope) {
        if (players == null) return;
        LangManager lm = lang();
        String title = lm == null ? name : lm.get("boosters.title_start", "name", name);
        String subtitle = lm == null
                ? multiplierText(multiplier) + "x for " + fmtDuration(durationSeconds)
                : lm.get("boosters.subtitle_start", "name", name,
                        "multiplier", multiplierText(multiplier), "duration", fmtDuration(durationSeconds));
        String chat = lm == null
                ? "[Booster] " + name + " started (" + multiplierText(multiplier) + "x for "
                        + fmtDuration(durationSeconds) + ")"
                : lm.get("boosters.announce_start", "id", boosterId == null ? "" : boosterId,
                        "name", name, "multiplier", multiplierText(multiplier),
                        "duration", fmtDuration(durationSeconds), "scope", scope == null ? "server" : scope);
        for (Player p : players) {
            if (p == null) continue;
            if (!chat.isEmpty()) p.sendMessage(Utils.chat(chat));
            Utils.sendTitle(p, title, subtitle, 40, 5);
        }
    }

    public static void announceStop(String boosterId, String name, String scope) {
        announceStop(Bukkit.getOnlinePlayers(), boosterId, name, scope);
    }

    public static void announceStop(Player player, String boosterId, String name) {
        announceStop(java.util.List.of(player), boosterId, name, "server");
    }

    public static void announceStop(Collection<? extends Player> players, String boosterId, String name, String scope) {
        if (players == null) return;
        LangManager lm = lang();
        String chat = lm == null
                ? "[Booster] " + name + " has ended."
                : lm.get("boosters.announce_stop", "id", boosterId == null ? "" : boosterId,
                        "name", name, "scope", scope == null ? "server" : scope);
        for (Player p : players) {
            if (p == null) continue;
            if (!chat.isEmpty()) p.sendMessage(Utils.chat(chat));
        }
    }

    public static void countdown(Collection<? extends Player> players, String boosterId, String name,
                                 long secondsRemaining) {
        if (players == null) return;
        LangManager lm = lang();
        String title = lm == null ? secondsRemaining + "s" : lm.getRaw("boosters.countdown_title",
                "seconds", String.valueOf(secondsRemaining));
        String subtitle = lm == null ? name + " ending" : lm.getRaw("boosters.countdown_subtitle", "name", name);
        for (Player p : players) {
            if (p == null) continue;
            Utils.sendTitle(p, title, subtitle, 20, 5);
        }
    }

    public static void countdown(Player player, String boosterId, String name, long secondsRemaining) {
        countdown(java.util.List.of(player), boosterId, name, secondsRemaining);
    }

    public static String formatChat(String name, double multiplier, String source, String scope) {
        LangManager lm = lang();
        if (lm == null) return "[Booster] " + name + " - " + multiplierText(multiplier) + "x ("
                + (source == null ? "" : source) + ")";
        return lm.get("boosters.chat_format", "name", name, "multiplier", multiplierText(multiplier),
                "source", source == null ? "" : source, "scope", scope == null ? "server" : scope);
    }

    public static String title(String name) {
        return name;
    }

    public static String subtitle(String name, double multiplier, long durationSeconds) {
        return multiplierText(multiplier) + "x for " + fmtDuration(durationSeconds);
    }

    // ---- alias surface that future callers may expect ----
    public static void announce(Collection<? extends Player> players, String boosterId, String name, double multiplier,
                                 long durationSeconds, String scope) {
        announceStart(players, boosterId, name, multiplier, durationSeconds, scope);
    }
    public static void announce(Player player, String boosterId, String name, double multiplier,
                                 long durationSeconds) {
        announceStart(player, boosterId, name, multiplier, durationSeconds);
    }
    public static void broadcast(Collection<? extends Player> players, String boosterId, String name, double multiplier,
                                  long durationSeconds, String scope) {
        announceStart(players, boosterId, name, multiplier, durationSeconds, scope);
    }
    public static void broadcastStart(Collection<? extends Player> players, String boosterId, String name, double multiplier,
                                      long durationSeconds, String scope) {
        announceStart(players, boosterId, name, multiplier, durationSeconds, scope);
    }
    public static void broadcastStop(Collection<? extends Player> players, String boosterId, String name, String scope) {
        announceStop(players, boosterId, name, scope);
    }
    public static void start(Collection<? extends Player> players, String boosterId, String name, double multiplier,
                             long durationSeconds, String scope) {
        announceStart(players, boosterId, name, multiplier, durationSeconds, scope);
    }
    public static void start(Player player, String boosterId, String name, double multiplier, long durationSeconds) {
        announceStart(player, boosterId, name, multiplier, durationSeconds);
    }
    public static void stop(Collection<? extends Player> players, String boosterId, String name, String scope) {
        announceStop(players, boosterId, name, scope);
    }
    public static void stop(Player player, String boosterId, String name) {
        announceStop(player, boosterId, name);
    }
    public static void sendStart(Collection<? extends Player> players, String boosterId, String name, double multiplier,
                                 long durationSeconds, String scope) {
        announceStart(players, boosterId, name, multiplier, durationSeconds, scope);
    }
    public static void sendStop(Collection<? extends Player> players, String boosterId, String name, String scope) {
        announceStop(players, boosterId, name, scope);
    }
    public static void sendCountdown(Collection<? extends Player> players, String boosterId, String name,
                                     long secondsRemaining) {
        countdown(players, boosterId, name, secondsRemaining);
    }
    public static void sendCountdown(Player player, String boosterId, String name, long secondsRemaining) {
        countdown(player, boosterId, name, secondsRemaining);
    }
    public static void titleCountdown(Collection<? extends Player> players, String boosterId, String name,
                                     long secondsRemaining) {
        countdown(players, boosterId, name, secondsRemaining);
    }
    public static void announcementStart(Collection<? extends Player> players, String boosterId, String name,
                                         double multiplier, long durationSeconds, String scope) {
        announceStart(players, boosterId, name, multiplier, durationSeconds, scope);
    }
    public static void announcementStop(Collection<? extends Player> players, String boosterId, String name, String scope) {
        announceStop(players, boosterId, name, scope);
    }
    public static void announcementCountdown(Collection<? extends Player> players, String boosterId, String name,
                                            long secondsRemaining) {
        countdown(players, boosterId, name, secondsRemaining);
    }
}