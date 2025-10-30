package me.nagasonic.alkatraz.spells;

import de.tr7zw.nbtapi.NBT;
import jdk.jshell.execution.Util;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.playerdata.PlayerData;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;

@SuppressWarnings("unused")
public abstract class Spell {
    protected final String type;
    protected String id;
    protected String displayName;
    protected String description;
    protected Element element;
    protected String code;
    protected BarColor masteryBarColor;
    protected Material guiItem;
    protected int cost;
    protected double castTime;
    protected int level;
    protected boolean enabled;
    protected int maxMastery;

    public Spell(String type) {
        this.type = type;
    }

    public abstract void loadConfiguration();

    public abstract void castAction(Player p, ItemStack wand);

    public abstract int circleAction(Player p);

    public void cast(Player p, ItemStack wand){
        Float castTime = getFullCastTime(wand, getCastTime());
        PlayerData data = DataManager.getPlayerData(p);
        if (data.getCircle() >= getLevel()){
            if (data.getMana() >= getCost()){
                if (!p.isDead()){
                    data.setCasting(true);
                    DataManager.subMana(p, getCost());
                    DataManager.addExperience(p, Utils.getExp(getLevel()));
                    Utils.sendActionBar(p, ColorFormat.format("Casted: " + getDisplayName()));
                    int d = circleAction(p);
                    Float v = castTime * 20;
                    Long finalCastTime = v.longValue();
                    if (data.getSpellMastery(this) >= getMaxMastery()){
                        finalCastTime = (long) (castTime * 1.25);
                    }
                    Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
                        data.setCasting(false);
                        Bukkit.getServer().getScheduler().cancelTask(d);
                        castAction(p, wand);
                    }, finalCastTime);
                    if (data.getSpellMastery(this) < getMaxMastery()){
                        DataManager.addSpellMastery(p, this, 1);
                    }
                }
            }else{
                Utils.sendActionBar(p, "&cNot Enough Mana");
            }
        }else{
            Utils.sendActionBar(p, "&cToo low Magic Circle");
        }
    }

    public void loadCommonConfig(YamlConfiguration spellConfig) {
        this.id = spellConfig.getString("id");
        this.displayName = spellConfig.getString("display_name");
        this.description = spellConfig.getString("description");
        this.element = Element.valueOf(spellConfig.getString("element"));
        this.code = spellConfig.getString("code");
        this.castTime = spellConfig.getDouble("cast_time");
        this.cost = spellConfig.getInt("mana_cost");
        this.level = spellConfig.getInt("level");
        this.enabled = spellConfig.getBoolean("enabled");
        this.maxMastery = spellConfig.getInt("maximum_mastery");
        this.masteryBarColor = BarColor.valueOf(spellConfig.getString("mastery_bar_color"));
        this.guiItem = Material.valueOf(spellConfig.getString("gui_item"));
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public Element getElement() { return element; }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getCode() {
        return code;
    }

    public int getCost() {
        return cost;
    }

    public double getCastTime() {
        return castTime;
    }

    public int getMaxMastery() {
        return maxMastery;
    }

    public int getLevel() {
        return level;
    }

    public BarColor getMasteryBarColor() {
        return masteryBarColor;
    }

    public Material getGuiItem() {
        return guiItem;
    }

    public boolean isEnabled() { return enabled; }

    public Float getFullCastTime(ItemStack wand, Double spellCastTime){
        Double wandCastTime = NBT.get(wand, nbt -> (Double) nbt.getDouble("casting_time"));
        Float wCastTime = wandCastTime.floatValue();
        return wCastTime * spellCastTime.floatValue();
    }

    public double calcDamage(double base, LivingEntity target, Player caster){
        PlayerData data = DataManager.getPlayerData(caster);
        double caffinity = data.getAffinity(getElement());
        if (getElement() != Element.NULL){
            caffinity += data.getMagicAffinity();
        }
        double tres = 0;
        if (target instanceof Player){
            Player t = (Player) target;
            PlayerData tdata = DataManager.getPlayerData(t);
            tres = tdata.getResistance(getElement());
            if (getElement() != Element.NULL){
                tres += tdata.getMagicResistance();
            }
        }else {
            tres = Utils.getEntityResistance(getElement(), target);
            if (getElement() != Element.NULL){
                tres += Utils.getEntityResistance(Element.NULL, target);
            }
        }
        return base * (1 + (caffinity - tres) / 100);
    }
}
