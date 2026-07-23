package me.nagasonic.alkatraz.tutorial;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class FirstJoinTutorial implements Listener {

    private static LangManager lang() { return Alkatraz.getLangManager(); }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!(boolean) Configs.FIRST_JOIN_TUTORIAL.get()) return;

        Player player = event.getPlayer();
        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);

        if (profile.getBool("tutorialSeen")) return;

        profile.setBool("tutorialSeen", true);
        Alkatraz.logHigh("Starting first-join tutorial for " + player.getName());
        scheduleSteps(player);
    }

    private void scheduleSteps(Player player) {
        int delay = 0;
        int gap = 100;

        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            sendTitle(player, lang().get("tutorial.welcome_title"), lang().get("tutorial.welcome_subtitle"), 80, 20);
        }, delay += 20);

        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            sendTitle(player, lang().get("tutorial.step1_title"), lang().get("tutorial.step1_subtitle"), 60, 10);
            sendMessage(player, lang().get("tutorial.step1_chat"));
        }, delay += gap);

        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            sendTitle(player, lang().get("tutorial.step2_title"), lang().get("tutorial.step2_subtitle"), 60, 10);
            sendMessage(player, lang().get("tutorial.step2_chat"));
        }, delay += gap);

        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            sendTitle(player, lang().get("tutorial.step3_title"), lang().get("tutorial.step3_subtitle"), 60, 10);
            sendMessage(player, lang().get("tutorial.step3_chat"));
        }, delay += gap);

        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            sendTitle(player, lang().get("tutorial.step4_title"), lang().get("tutorial.step4_subtitle"), 60, 10);
            sendMessage(player, lang().get("tutorial.step4_chat"));
        }, delay += gap);

        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            sendTitle(player, lang().get("tutorial.step5_title"), lang().get("tutorial.step5_subtitle"), 60, 10);
            sendMessage(player, lang().get("tutorial.step5_chat"));
        }, delay += gap);

        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            sendTitle(player, lang().get("tutorial.done_title"), lang().get("tutorial.done_subtitle"), 80, 20);
        }, delay += gap);
    }

    private void sendTitle(Player player, String title, String subtitle, int duration, int fade) {
        player.sendTitle(title, subtitle, fade, duration, fade);
    }

    private void sendMessage(Player player, String message) {
        player.sendMessage(message);
    }
}
