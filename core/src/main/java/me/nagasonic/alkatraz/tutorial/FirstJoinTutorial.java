package me.nagasonic.alkatraz.tutorial;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.config.VerbosityLevel;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FirstJoinTutorial implements Listener {

    private static final Map<UUID, List<BukkitTask>> pendingTasks = new ConcurrentHashMap<>();

    private static LangManager lang() { return Alkatraz.getLangManager(); }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!(boolean) Configs.FIRST_JOIN_TUTORIAL.get()) return;

        Player player = event.getPlayer();
        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);

        if (profile == null) return;
        if (profile.getBool("tutorialSeen")) return;

        profile.setBool("tutorialSeen", true);
        if (Alkatraz.getVerbosity().isAtLeast(VerbosityLevel.HIGH)) {
            Alkatraz.logHigh("Starting first-join tutorial for " + player.getName());
        }
        scheduleSteps(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelPending(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() != Alkatraz.getInstance()) return;
        for (UUID uuid : List.copyOf(pendingTasks.keySet())) {
            cancelPending(uuid);
        }
    }

    private static void cancelPending(UUID uuid) {
        List<BukkitTask> tasks = pendingTasks.remove(uuid);
        if (tasks == null) return;
        for (BukkitTask task : tasks) {
            task.cancel();
        }
    }

    private void scheduleSteps(Player player) {
        UUID uuid = player.getUniqueId();
        cancelPending(uuid);
        List<BukkitTask> tasks = new ArrayList<>(7);
        pendingTasks.put(uuid, tasks);

        int delay = 0;
        int gap = 100;

        tasks.add(step(player, () -> {
            sendTitle(player, lang().get("tutorial.welcome_title"), lang().get("tutorial.welcome_subtitle"), 80, 20);
        }, delay += 20));

        tasks.add(step(player, () -> {
            sendTitle(player, lang().get("tutorial.step1_title"), lang().get("tutorial.step1_subtitle"), 60, 10);
            sendMessage(player, lang().get("tutorial.step1_chat"));
        }, delay += gap));

        tasks.add(step(player, () -> {
            sendTitle(player, lang().get("tutorial.step2_title"), lang().get("tutorial.step2_subtitle"), 60, 10);
            sendMessage(player, lang().get("tutorial.step2_chat"));
        }, delay += gap));

        tasks.add(step(player, () -> {
            sendTitle(player, lang().get("tutorial.step3_title"), lang().get("tutorial.step3_subtitle"), 60, 10);
            sendMessage(player, lang().get("tutorial.step3_chat"));
        }, delay += gap));

        tasks.add(step(player, () -> {
            sendTitle(player, lang().get("tutorial.step4_title"), lang().get("tutorial.step4_subtitle"), 60, 10);
            sendMessage(player, lang().get("tutorial.step4_chat"));
        }, delay += gap));

        tasks.add(step(player, () -> {
            sendTitle(player, lang().get("tutorial.step5_title"), lang().get("tutorial.step5_subtitle"), 60, 10);
            sendMessage(player, lang().get("tutorial.step5_chat"));
        }, delay += gap));

        tasks.add(step(player, () -> {
            sendTitle(player, lang().get("tutorial.done_title"), lang().get("tutorial.done_subtitle"), 80, 20);
            pendingTasks.remove(uuid);
        }, delay += gap));
    }

    private static BukkitTask step(Player player, Runnable body, int delayTicks) {
        return Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            if (!player.isOnline()) return;
            body.run();
        }, delayTicks);
    }

    private void sendTitle(Player player, String title, String subtitle, int duration, int fade) {
        player.sendTitle(title, subtitle, fade, duration, fade);
    }

    private void sendMessage(Player player, String message) {
        player.sendMessage(message);
    }
}
