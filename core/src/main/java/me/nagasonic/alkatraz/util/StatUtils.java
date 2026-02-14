package me.nagasonic.alkatraz.util;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.items.wands.Wand;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static me.nagasonic.alkatraz.util.ColorFormat.format;

public class StatUtils {
    public static void addMana(Player p, double amount) {
        MagicProfile profile = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);

        profile.setMana(profile.getMana() + amount);
        if (profile.getMana() > profile.getMaxMana()) {
            profile.setMana(profile.getMaxMana());
        }

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.AIR && item.getAmount() != 0) {
            if (Wand.isWand(item)) {
                Alkatraz.getNms().fakeExp(
                        p,
                        (float) (profile.getMana() / profile.getMaxMana()),
                        (int) profile.getMana(),
                        1
                );
            }
        }
    }

    public static void subMana(Player p, double amount) {
        MagicProfile profile = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);

        profile.setMana(profile.getMana() - amount);
        if (profile.getMana() < 0) {
            profile.setMana(0);
        }

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.AIR && item.getAmount() != 0) {
            if (Wand.isWand(item)) {
                Alkatraz.getNms().fakeExp(
                        p,
                        (float) (profile.getMana() / profile.getMaxMana()),
                        (int) profile.getMana(),
                        1
                );
            }
        }
    }

    public static void addSpellMastery(OfflinePlayer p, Spell spell, int mastery){
        MagicProfile profile = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
        if (profile.getSpellMastery(spell) == -1 && mastery > 0){
            profile.setSpellMastery(spell, 0);
        }
        if (profile.getSpellMastery(spell) + mastery < 0){
            profile.setSpellMastery(spell, 0);
        }else if (profile.getSpellMastery(spell) + mastery > spell.getMaxMastery()){
            profile.setSpellMastery(spell, spell.getMaxMastery());
        }else { profile.setSpellMastery(spell, profile.getSpellMastery(spell) + mastery); }
        if (p.isOnline()){
            Map<Spell, BossBar> masteryBars = profile.getMasteryBars();
            if (masteryBars.containsKey(spell)){
                BossBar bar = masteryBars.get(spell);
                bar.removePlayer(p.getPlayer());
                bar.setTitle(format(spell.getDisplayName() + " Mastery: " + profile.getSpellMastery(spell) + "/" + spell.getMaxMastery()));
                if (profile.getSpellMastery(spell) / spell.getMaxMastery() > 1){
                    bar.setProgress(1);
                }else if (profile.getSpellMastery(spell) / spell.getMaxMastery() < 0){
                    bar.setProgress(0);
                }else {bar.setProgress((double) profile.getSpellMastery(spell) / spell.getMaxMastery()); }
                bar.addPlayer(p.getPlayer());
                masteryBars.replace(spell, bar);
                profile.setMasteryBars(masteryBars);
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
                    if (bar.getProgress() == profile.getMasteryBars().get(spell).getProgress()){
                        bar.removePlayer(p.getPlayer());
                    }
                }, 100L);
            }else{
                BossBar bar = Bukkit.createBossBar(format(spell.getDisplayName() + ": " + profile.getSpellMastery(spell) + "/" + spell.getMaxMastery()), spell.getMasteryBarColor(), BarStyle.SOLID);
                if (profile.getSpellMastery(spell) / spell.getMaxMastery() > 1){
                    bar.setProgress(1);
                }else if (profile.getSpellMastery(spell) / spell.getMaxMastery() < 0){
                    bar.setProgress(0);
                }else {bar.setProgress((double) profile.getSpellMastery(spell) / spell.getMaxMastery()); }
                bar.addPlayer(p.getPlayer());
                masteryBars.put(spell, bar);
                profile.setMasteryBars(masteryBars);
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
                    if (bar.getProgress() == profile.getMasteryBars().get(spell).getProgress()){
                        bar.removePlayer(p.getPlayer());
                    }
                }, 100L);
            }
        }
    }

    public static Long requiredExperience(int circle){
        long casts = Math.round(150.0 * Math.pow(Math.pow(800 / 150, 1.0 / (9 - 1)), circle - 1));
        long xpPerCast = Math.round(2.0 * Math.pow(1.9, circle - 1));
        long deltaXP = casts * xpPerCast;
        return deltaXP;
    }

    public static void addExperience(OfflinePlayer p, double exp) {
        MagicProfile profile = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);

        profile.setExperience(profile.getExperience() + exp);

        if (p.isOnline()) {
            BossBar bar = profile.getExpBar();
            String max = profile.getCircleLevel() < 9
                    ? String.valueOf(requiredExperience(profile.getCircleLevel() + 1))
                    : "MAX";

            if (bar == null) {
                BossBar newbar = Bukkit.createBossBar(
                        format("&bMagic Experience: " + profile.getExperience() + "/" + max),
                        BarColor.WHITE,
                        BarStyle.SOLID
                );

                if (profile.getCircleLevel() < 9) {
                    double progress = profile.getExperience() /
                            requiredExperience(profile.getCircleLevel() + 1);

                    newbar.setProgress(Math.max(0, Math.min(1, progress)));
                } else {
                    newbar.setProgress(1);
                }

                newbar.addPlayer(p.getPlayer());
                profile.setExpBar(newbar);

                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                        Alkatraz.getInstance(),
                        () -> {
                            if (newbar.getProgress() == profile.getExpBar().getProgress()) {
                                newbar.removePlayer(p.getPlayer());
                            }
                        },
                        100L
                );
            } else {
                bar.removePlayer(p.getPlayer());
                bar.setTitle(format("&bMagic Experience: " + profile.getExperience() + "/" + max));

                if (profile.getCircleLevel() < 9) {
                    double progress = profile.getExperience() /
                            requiredExperience(profile.getCircleLevel() + 1);

                    bar.setProgress(Math.max(0, Math.min(1, progress)));
                } else {
                    bar.setProgress(1);
                }

                bar.addPlayer(p.getPlayer());
                profile.setExpBar(bar);

                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                        Alkatraz.getInstance(),
                        () -> {
                            if (bar.getProgress() == profile.getExpBar().getProgress()) {
                                bar.removePlayer(p.getPlayer());
                            }
                        },
                        100L
                );
            }
        }

        if (profile.getCircleLevel() < 9) {
            if (profile.getExperience() >= requiredExperience(profile.getCircleLevel() + 1)) {
                double experience = profile.getExperience();
                profile.setExperience(0);

                addCircle(p.getPlayer(), 1);

                if (p.isOnline()) {
                    p.getPlayer().sendMessage(
                            format("&e&lCIRCLE UP!"),
                            format("&bReached the " +
                                    StringUtils.toOrdinal(profile.getCircleLevel()) + " circle."),
                            format("&bYou are now able to use spells up to the " +
                                    StringUtils.toOrdinal(profile.getCircleLevel()) + " rank.")
                    );
                }

                addExperience(p,
                        experience - requiredExperience(profile.getCircleLevel()));
            }
        }
    }

    public static void addCircle(@NotNull Player p, int circle) {
        MagicProfile profile = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);

        int previousCircle = profile.getCircleLevel();

        profile.setMaxMana(
                profile.getMaxMana()
                        + (getMaxMana(circle + previousCircle)
                        - getMaxMana(previousCircle))
        );

        profile.setManaRegeneration(
                profile.getManaRegeneration()
                        + (getManaRegen(circle + previousCircle)
                        - getManaRegen(previousCircle))
        );

        profile.setCircleLevel(previousCircle + circle);
    }

    private static double getManaRegen(int circle){
        return switch (circle){
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 2.5;
            case 3 -> 3;
            case 4 -> 5;
            case 5 -> 6.25;
            case 6 -> 7.5;
            case 7 -> 10;
            case 8 -> 12.5;
            case 9 -> 15;
            default -> 0;
        };
    }

    private static int getMaxMana(int circle){
        return switch (circle){
            case 0 -> 100;
            case 1 -> 200;
            case 2 -> 400;
            case 3 -> 800;
            case 4 -> 1250;
            case 5 -> 2000;
            case 6 -> 2750;
            case 7 -> 4000;
            case 8 -> 7500;
            case 9 -> 10000;
            default -> 0;
        };
    }
}
