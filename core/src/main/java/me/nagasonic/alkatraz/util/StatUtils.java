package me.nagasonic.alkatraz.util;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.items.wands.Wand;
import me.nagasonic.alkatraz.playerdata.SpellHotbarManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.progression.ProgressionService;
import me.nagasonic.alkatraz.progression.circle.CircleDefinition;
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
            if (Wand.isWand(item) || SpellHotbarManager.isActive(p)){
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
            if (Wand.isWand(item) || SpellHotbarManager.isActive(p)) {
                Alkatraz.getNms().fakeExp(
                        p,
                        (float) (profile.getMana() / profile.getMaxMana()),
                        (int) profile.getMana(),
                        1
                );
            }
        }
    }

    public static void addSpellMastery(Player p, Spell spell, int mastery){
        MagicProfile profile = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
        if (profile.getSpellMastery(spell) == -1 && mastery > 0){
            profile.setSpellMastery(spell, 0);
        }
        if (profile.getSpellMastery(spell) + mastery < 0){
            profile.setSpellMastery(spell, 0);
        }else if (profile.getSpellMastery(spell) + mastery >= spell.getMaxMastery()){
            profile.setSpellMastery(spell, spell.getMaxMastery());
            profile.setStatPoints(profile.getStatPoints() + getStatPointsMastery(spell.getLevel()));
        }else {
            profile.setSpellMastery(spell, profile.getSpellMastery(spell) + mastery);
        }
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

    public static int getStatPointsMastery(int circle){
        return switch (circle) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 4;
            case 4 -> 5;
            case 5 -> 6;
            case 6 -> 7;
            case 7 -> 8;
            case 8 -> 9;
            case 9 -> 10;
            default -> 0;
        };
    }

    public static Long requiredExperience(int circle){
        long casts = Math.round(150.0 * Math.pow(Math.pow(800 / 150, 1.0 / (9 - 1)), circle - 1));
        long xpPerCast = Math.round(2.0 * Math.pow(1.9, circle - 1));
        long deltaXP = casts * xpPerCast;
        return deltaXP;
    }

    public static void addExperience(OfflinePlayer p, double exp) {
        addArcaneKnowledge(p, exp);
    }

    public static void addArcaneKnowledge(OfflinePlayer p, double amount) {
        ProgressionService.addArcaneKnowledge(p, amount);
    }

    public static void addArcaneKnowledge(OfflinePlayer p, String sourceId) {
        ProgressionService.addArcaneKnowledge(p, sourceId);
    }

    public static void addArcaneKnowledge(OfflinePlayer p, String sourceId, int circle) {
        ProgressionService.addArcaneKnowledge(p, sourceId, circle);
    }

    public static int getStatPoints(int circle) {
        CircleDefinition definition = ProgressionService.getCircleDefinition(circle);
        if (definition != null) return definition.getStatPoints();
        return switch (circle){
            case 1 -> 5;
            case 2 -> 5;
            case 3 -> 7;
            case 4 -> 10;
            case 5 -> 10;
            case 6 -> 15;
            case 7 -> 15;
            case 8 -> 20;
            case 9 -> 25;
            default -> 0;
        };
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
        CircleDefinition definition = ProgressionService.getCircleDefinition(circle);
        if (definition != null) return definition.getManaRegeneration();
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
        CircleDefinition definition = ProgressionService.getCircleDefinition(circle);
        if (definition != null) return (int) definition.getMaxMana();
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
