package me.nagasonic.alkatraz.tutorial;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.util.ColorFormat;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class FirstJoinTutorial implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!(boolean) Configs.FIRST_JOIN_TUTORIAL.get()) return;

        Player player = event.getPlayer();
        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);

        if (profile.getBool("tutorialSeen")) return;

        profile.setBool("tutorialSeen", true);
        scheduleSteps(player);
    }

    private void scheduleSteps(Player player) {
        int delay = 0;
        int gap = 100;

        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            sendTitle(player, "&d&lAlkatraz", "&fYour magical journey begins...", 80, 20);
            sendMessage(player, "&d&l\u2728 Welcome to Alkatraz! \u2728");
            sendMessage(player, "&7You have awakened your magical potential.");
        }, delay += 20);

        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            sendTitle(player, "&fStep 1: Get a Wand", "&7Craft a Wooden Wand to begin", 60, 10);
            sendMessage(player, "");
            sendMessage(player, "&6&l\u2728 Crafting Your First Wand");
            sendMessage(player, "&7Place &bLapis Lazuli &7above a &fStick &7in a crafting table.");
            sendMessage(player, "&7Hold the wand in your main hand to channel magic.");
            sendMessage(player, "&7Your XP bar will show your &bMana &7while holding it.");
        }, delay += gap);

        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            sendTitle(player, "&fStep 2: Cast a Spell", "&7Input a 5-click code to cast", 60, 10);
            sendMessage(player, "");
            sendMessage(player, "&6&l\u2728 Casting Your First Spell");
            sendMessage(player, "&7While holding your wand, input &e5-click codes&7:");
            sendMessage(player, "  &f\u25c6 &7Right-click &8- &f\u25c6 &7(shown as &f\u25c6&7)");
            sendMessage(player, "  &f\u25c7 &7Left-click  &8- &f\u25c7 &7(shown as &f\u25c7&7)");
            sendMessage(player, "  &f\u2756 &7Swap (F)   &8- &f\u2756 &7(shown as &f\u2756&7)");
            sendMessage(player, "&7Try &eRRRRR &7for &bMagic Missile &7- your starter spell!");
        }, delay += gap);

        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            sendTitle(player, "&fStep 3: Understand Mana", "&7Your magical resource", 60, 10);
            sendMessage(player, "");
            sendMessage(player, "&6&l\u2728 Mana System");
            sendMessage(player, "&7Every spell costs &bMana&7. You start with &b100 Mana&7.");
            sendMessage(player, "&7Mana regenerates over time &7- faster at higher circles.");
            sendMessage(player, "&7Your &bMana &7is shown on your &fXP bar &7while holding a wand.");
            sendMessage(player, "&7If you run out of mana, you &ccannot cast &7until it regenerates.");
        }, delay += gap);

        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            sendTitle(player, "&fStep 4: Useful Commands", "&7/spells and /alkatraz", 60, 10);
            sendMessage(player, "");
            sendMessage(player, "&6&l\u2728 Commands");
            sendMessage(player, "  &e/spells &7- View all spells and configure options");
            sendMessage(player, "  &e/alkatraz stats &7- View and invest stat points");
            sendMessage(player, "  &e/alkatraz castmode hotbar &7- Switch to click-to-cast mode");
            sendMessage(player, "  &e/alkatraz castmode code &7- Switch back to code casting");
        }, delay += gap);

        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            sendTitle(player, "&fStep 5: Progress", "&7Advance through the Magic Circles", 60, 10);
            sendMessage(player, "");
            sendMessage(player, "&6&l\u2728 Progression");
            sendMessage(player, "&7Cast spells to earn &dArcane Knowledge &7and &aResearch Points&7.");
            sendMessage(player, "&7Right-click an &5Enchanting Table &7with a wand to open:");
            sendMessage(player, "  &d\u2726 Progression &7- Advance through 9 Magic Circles");
            sendMessage(player, "  &d\u2726 Research &7- Unlock permanent upgrades");
            sendMessage(player, "&7Higher circles grant more &bMana&7, &fStat Points&7, and new spells.");
        }, delay += gap);

        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            sendTitle(player, "&d&lGood Luck!", "&fMay your magic grow strong", 80, 20);
            sendMessage(player, "");
            sendMessage(player, "&d&l\u2728 You're ready, wizard! \u2728");
            sendMessage(player, "&7Begin by crafting a wand and casting &bMagic Missile&7.");
            sendMessage(player, "&7Type &e/spells &7to see all available spells.");
            sendMessage(player, "&7Visit the &dAlkatraz Wiki &7for more info:");
            sendClickableLink(player, "https://github.com/NagasonicDev/Alkatraz/wiki", "&d\u2726 Click to open the Alkatraz Wiki");
            sendMessage(player, "&7Have suggestions or found a bug? Join our &dDiscord&7:");
            sendClickableLink(player, "https://discord.gg/qZ5hHc5KcN", "&d\u2726 Click to join the Alkatraz Discord");
            sendMessage(player, "&d\u2726 &fHappy casting! &d\u2726");
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ColorFormat.format("&d\u2728 Alkatraz &7- type &e/spells &7to begin &d\u2728")));
        }, delay += gap);
    }

    private void sendTitle(Player player, String title, String subtitle, int duration, int fade) {
        player.sendTitle(ColorFormat.format(title), ColorFormat.format(subtitle), fade, duration, fade);
    }

    private void sendMessage(Player player, String message) {
        player.sendMessage(ColorFormat.format(message));
    }

    private void sendClickableLink(Player player, String url, String display) {
        TextComponent link = new TextComponent(ColorFormat.format(display));
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(ColorFormat.format("&7Click to open: &f" + url))));
        player.spigot().sendMessage(link);
    }
}
