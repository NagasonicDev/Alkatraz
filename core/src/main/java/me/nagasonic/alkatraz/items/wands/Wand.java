package me.nagasonic.alkatraz.items.wands;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public abstract class Wand implements Listener {
    protected final String type;
    protected String id;
    protected Material material;
    protected String name;
    protected List<String> lore;
    protected List<String> recipeShape;
    protected List<String> recipeValues;
    protected double power;
    protected double castTime;

    //Added Damage Multipliers (damage += (power + elementalDamage))
    protected double fireDamage;
    protected double airDamage;
    protected double earthDamage;
    protected double waterDamage;
    protected double lightDamage;
    protected double darkDamage;


    public Wand(String type){
        this.type = type;
    }

    public abstract void loadConfiguration();

    public abstract Recipe getRecipe();

    public void loadCommonConfig(YamlConfiguration wandConfig) {
        this.id = wandConfig.getString("id");
        this.name = wandConfig.getString("item_name");
        this.material = Utils.materialFromString(wandConfig.getString("material"));
        this.lore = wandConfig.getStringList("lore");
        this.power = wandConfig.getDouble("power");
        this.castTime = wandConfig.getDouble("cast_time");
        this.fireDamage = wandConfig.getDouble("fire_damage");
        this.airDamage = wandConfig.getDouble("air_damage");
        this.earthDamage = wandConfig.getDouble("earth_damage");
        this.waterDamage = wandConfig.getDouble("water_damage");
        this.lightDamage = wandConfig.getDouble("light_damage");
        this.darkDamage = wandConfig.getDouble("dark_damage");
        this.recipeShape = wandConfig.getStringList("recipe.shape");
        this.recipeValues = wandConfig.getStringList("recipe.values");
    }

    public ItemStack getItem() {
        ItemStack item = new ItemStack(getMaterial());
        ItemMeta meta = item.getItemMeta();
        Alkatraz.logInfo(getName());
        meta.setDisplayName(ColorFormat.format(getName()));
        List<String> lore = new ArrayList<>();
        lore.addAll(getFormmattedLore());
        lore.addAll(createAttributeLore());
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        setNBT(item);
        return item;
    }

    public List<String> getFormmattedLore() {
        List<String> lore = getLore();
        List<String> formattedLore = new ArrayList<>();
        for (String str : lore) {
            formattedLore.add(ColorFormat.format(str));
        }
        return formattedLore;
    }

    private List<String> createAttributeLore() {
        List<String> lore = new ArrayList<>();
        lore.add(ColorFormat.format("&9Magic Power: " + getPower()));
        lore.add(ColorFormat.format("&9Casting Time: " + getCastTime()));
        if (getFireDamage() != 0) { lore.add(ColorFormat.format( Element.FIRE.getColor() + "Fire Damage: " + getFireDamage())); }
        if (getAirDamage() != 0) { lore.add(ColorFormat.format(Element.AIR.getColor() + "Air Damage: " + getAirDamage())); }
        if (getEarthDamage() != 0) { lore.add(ColorFormat.format(Element.EARTH.getColor() + "Earth Damage: " + getEarthDamage())); }
        if (getWaterDamage() != 0) { lore.add(ColorFormat.format(Element.WATER.getColor() + "Water Damage: " + getWaterDamage())); }
        if (getLightDamage() != 0) { lore.add(ColorFormat.format(Element.LIGHT.getColor() + "Light Damage: " + getLightDamage())); }
        if (getDarkDamage() != 0) { lore.add(ColorFormat.format(Element.DARK.getColor() + "Dark Damage: " + getDarkDamage())); }
        return lore;
    }

    private void setNBT(ItemStack item) {
        NBT.modify(item, nbt -> {
            nbt.setBoolean("wand", true);
            nbt.setString("cast_code", "");
            nbt.setDouble("magic_power", getPower());
            nbt.setDouble("casting_time", getCastTime());
            if (getFireDamage() != 0) { nbt.setDouble("fire_damage", getFireDamage()); }
            if (getAirDamage() != 0) { nbt.setDouble("air_damage", getAirDamage()); }
            if (getEarthDamage() != 0) { nbt.setDouble("earth_damage", getEarthDamage()); }
            if (getWaterDamage() != 0) { nbt.setDouble("water_damage", getWaterDamage()); }
            if (getLightDamage() != 0) { nbt.setDouble("light_damage", getLightDamage()); }
            if (getDarkDamage() != 0) { nbt.setDouble("dark_damage", getDarkDamage()); }
        });
    }


    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public double getPower() {
        return power;
    }

    public double getCastTime() {
        return castTime;
    }

    public double getFireDamage() {
        return fireDamage;
    }

    public double getAirDamage() {
        return airDamage;
    }

    public double getEarthDamage() {
        return earthDamage;
    }

    public double getWaterDamage() {
        return waterDamage;
    }

    public double getLightDamage() {
        return lightDamage;
    }

    public double getDarkDamage() {
        return darkDamage;
    }

    public static boolean isWand(ItemStack item) {
        return NBT.get(item, nbt -> (Boolean) nbt.getBoolean("wand"));
    }

    public List<String> getRecipeShape() {
        return recipeShape;
    }

    public List<String> getRecipeValues() {
        return recipeValues;
    }
}
