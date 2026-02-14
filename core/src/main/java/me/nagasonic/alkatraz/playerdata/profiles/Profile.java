package me.nagasonic.alkatraz.playerdata.profiles;

import org.bukkit.entity.Player;

import java.util.*;

public abstract class Profile {
    protected UUID owner;
    protected Collection<String> allStatNames = new HashSet<>();

    protected Map<String, NumberHolder<Integer>> ints = new HashMap<>();
    protected Map<String, NumberHolder<Double>> doubles = new HashMap<>();
    protected Map<String, NumberHolder<Float>> floats = new HashMap<>();
    protected Map<String, BooleanHolder> bools = new HashMap<>();
    protected Map<String, StringHolder> strings = new HashMap<>();
    protected Map<String, Collection<String>> stringSets = new HashMap<>();

    public Profile(UUID owner) {
        if (owner == null) return;
        this.owner = owner;
    }

    public UUID getOwner() {
        return owner;
    }

    public Collection<String> getAllStatNames() {
        return allStatNames;
    }

    public Collection<String> getInts() {
        return ints.keySet();
    }

    public Collection<String> getDoubles() {
        return doubles.keySet();
    }

    public Collection<String> getFloats() {
        return floats.keySet();
    }

    public Collection<String> getBools() {
        return bools.keySet();
    }

    public Collection<String> getStrings() { return strings.keySet(); }

    public Collection<String> getStringSets() {
        return stringSets.keySet();
    }

    public boolean isInt(String stat) {
        return ints.containsKey(stat);
    }

    public int getInt(String stat) {
        NumberHolder<Integer> intHolder = ints.get(stat);
        if (intHolder == null) throw new IllegalArgumentException("Int stat " + stat + " not found");
        return intHolder.getValue();
    }

    public int getDefaultInt(String stat) {
        NumberHolder<Integer> intHolder = ints.get(stat);
        if (intHolder == null) throw new IllegalArgumentException("Int stat " + stat + " not found");
        return intHolder.getDefault();
    }

    public void setInt(String stat, int value) {
        NumberHolder<Integer> intHolder = ints.get(stat);
        if (intHolder == null) throw new IllegalArgumentException("Int stat " + stat + " not found");
        intHolder.setValue(value);
    }

    public boolean isDouble(String stat) {
        return doubles.containsKey(stat);
    }

    public double getDouble(String stat) {
        NumberHolder<Double> doubleHolder = doubles.get(stat);
        if (doubleHolder == null) throw new IllegalArgumentException("Double stat " + stat + " not found");
        return doubleHolder.getValue();
    }

    public double getDefaultDouble(String stat) {
        NumberHolder<Double> doubleHolder = doubles.get(stat);
        if (doubleHolder == null) throw new IllegalArgumentException("Double stat " + stat + " not found");
        return doubleHolder.getDefault();
    }

    public void setDouble(String stat, double value) {
        NumberHolder<Double> doubleHolder = doubles.get(stat);
        if (doubleHolder == null) throw new IllegalArgumentException("Double stat " + stat + " not found");
        doubleHolder.setValue(value);
    }

    public boolean isFloat(String stat) {
        return floats.containsKey(stat);
    }

    public float getFloat(String stat) {
        NumberHolder<Float> floatHolder = floats.get(stat);
        if (floatHolder == null) throw new IllegalArgumentException("Float stat " + stat + " not found");
        return floatHolder.getValue();
    }

    public float getDefaultFloat(String stat) {
        NumberHolder<Float> floatHolder = floats.get(stat);
        if (floatHolder == null) throw new IllegalArgumentException("Float stat " + stat + " not found");
        return floatHolder.getDefault();
    }

    public void setFloat(String stat, float value) {
        NumberHolder<Float> floatHolder = floats.get(stat);
        if (floatHolder == null) throw new IllegalArgumentException("Float stat " + stat + " not found");
        floatHolder.setValue(value);
    }

    public boolean isBool(String stat) {
        return bools.containsKey(stat);
    }

    public boolean getBool(String stat) {
        BooleanHolder boolHolder = bools.get(stat);
        if (boolHolder == null) throw new IllegalArgumentException("Bool stat " + stat + " not found");
        return boolHolder.getValue();
    }

    public boolean getDefaultBool(String stat) {
        BooleanHolder boolHolder = bools.get(stat);
        if (boolHolder == null) throw new IllegalArgumentException("Bool stat " + stat + " not found");
        return boolHolder.getDefault();
    }

    public void setBool(String stat, boolean value) {
        BooleanHolder boolHolder = bools.get(stat);
        if (boolHolder == null) throw new IllegalArgumentException("Bool stat " + stat + " not found");
        boolHolder.setValue(value);
    }

    public boolean isString(String stat) { return strings.containsKey(stat); }

    public String getString(String stat) {
        StringHolder stringHolder = strings.get(stat);
        if (stringHolder == null) throw new IllegalArgumentException("String stat " + stat + " not found");
        return stringHolder.getValue();
    }

    public String getDefaultString(String stat) {
        StringHolder stringHolder = strings.get(stat);
        if (stringHolder == null) throw new IllegalArgumentException("String stat " + stat + " not found");
        return stringHolder.getDefault();
    }

    public void setString(String stat, String value) {
        StringHolder stringHolder = strings.get(stat);
        if (stringHolder == null) throw new IllegalArgumentException("String stat " + stat + " not found");
        stringHolder.setValue(value);
    }

    public boolean isStringSet(String stat) {
        return stringSets.containsKey(stat);
    }

    public Collection<String> getStringSet(String stat) {
        Collection<String> strings = stringSets.get(stat);
        if (strings == null) throw new IllegalArgumentException("StringSet stat " + stat + " not found");
        return strings;
    }

    public void setStringSet(String stat, Collection<String> strings) {
        if (!stringSets.containsKey(stat)) throw new IllegalArgumentException("StringSet stat " + stat + " not found");
        stringSets.put(stat, strings);
    }

    protected void intStat(String name) { intStat(name, 0); }
    protected void intStat(String name, int def){
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        ints.put(name, new NumberHolder<>(def, def));
    }

    protected void doubleStat(String name) { doubleStat(name, 0); }
    protected void doubleStat(String name, double def) {
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        doubles.put(name, new NumberHolder<>(def, def));
    }

    protected void floatStat(String name) { floatStat(name, 0); }
    protected void floatStat(String name, float def) {
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        floats.put(name, new NumberHolder<>(def, def));
    }

    protected void boolStat(String name) { boolStat(name, false); }
    protected void boolStat(String name, boolean def){
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        bools.put(name, new BooleanHolder(def, def));
    }

    protected void stringStat(String name) { stringStat(name, ""); }
    protected void stringStat(String name, String def){
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        strings.put(name, new StringHolder(def, def));
    }

    protected void stringSetStat(String name){
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        stringSets.put(name, new HashSet<>());
    }

    public Profile getBlankProfile(Player player){
        return getBlankProfile(player.getUniqueId());
    }
    public abstract Profile getBlankProfile(UUID owner);

    public void copyStats(Profile profile) {
        this.allStatNames.addAll(profile.allStatNames);
        for (Map.Entry<String, NumberHolder<Integer>> entry : profile.ints.entrySet()) {
            this.ints.put(entry.getKey(), entry.getValue().copy());
        }
        for (Map.Entry<String, NumberHolder<Float>> entry : profile.floats.entrySet()) {
            this.floats.put(entry.getKey(), entry.getValue().copy());
        }
        for (Map.Entry<String, NumberHolder<Double>> entry : profile.doubles.entrySet()) {
            this.doubles.put(entry.getKey(), entry.getValue().copy());
        }
        for (Map.Entry<String, Collection<String>> entry : profile.stringSets.entrySet()) {
            this.stringSets.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        for (Map.Entry<String, BooleanHolder> entry : profile.bools.entrySet()) {
            this.bools.put(entry.getKey(), entry.getValue().copy());
        }
    }

    protected static class StringHolder {
        private String value;
        private String def;
        public StringHolder(String value, String def) {
            this.value = value;
            this.def = def;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getDefault() {
            return def;
        }

        public StringHolder copy() {
            return new StringHolder(value, def);
        }
    }

    protected static class NumberHolder<T extends Number> {
        private T value;
        private T def;
        public NumberHolder(T value, T def) {
            this.value = value;
            this.def = def;
        }

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }

        public T getDefault() {
            return def;
        }

        public NumberHolder<T> copy() {
            return new NumberHolder<>(value, def);
        }
    }

    protected static class BooleanHolder {
        private boolean value;
        private boolean def;
        public BooleanHolder(boolean value, boolean def) {
            this.value = value;
            this.def = def;
        }

        public boolean getValue() {
            return value;
        }

        public void setValue(boolean value) {
            this.value = value;
        }

        public boolean getDefault() {
            return def;
        }

        public BooleanHolder copy() {
            return new BooleanHolder(value, def);
        }
    }

}
