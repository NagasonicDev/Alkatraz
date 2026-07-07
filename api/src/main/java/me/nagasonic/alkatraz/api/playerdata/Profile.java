package me.nagasonic.alkatraz.api.playerdata;

import org.bukkit.entity.Player;

import java.util.*;

public abstract class Profile {
    protected UUID owner;
    protected Collection<String> allStatNames = new HashSet<>();
    protected Map<String, NumberHolder<Integer>> ints = new HashMap<>();
    protected Map<String, NumberHolder<Double>> doubles = new HashMap<>();
    protected Map<String, NumberHolder<Float>> floats = new HashMap<>();
    protected Map<String, NumberHolder<Long>> longs = new HashMap<>();
    protected Map<String, BooleanHolder> bools = new HashMap<>();
    protected Map<String, StringHolder> strings = new HashMap<>();
    protected Map<String, Collection<String>> stringSets = new HashMap<>();

    public Profile(UUID owner) {
        if (owner == null) return;
        this.owner = owner;
    }

    public UUID getOwner() { return owner; }
    public Collection<String> getAllStatNames() { return allStatNames; }
    public Collection<String> getInts() { return ints.keySet(); }
    public Collection<String> getDoubles() { return doubles.keySet(); }
    public Collection<String> getFloats() { return floats.keySet(); }
    public Collection<String> getLongs() { return longs.keySet(); }
    public Collection<String> getBools() { return bools.keySet(); }
    public Collection<String> getStrings() { return strings.keySet(); }
    public Collection<String> getStringSets() { return stringSets.keySet(); }

    public boolean isInt(String stat) { return ints.containsKey(stat); }
    public int getInt(String stat) { return holder(ints, stat).getValue(); }
    public int getDefaultInt(String stat) { return holder(ints, stat).getDefault(); }
    public void setInt(String stat, int value) { holder(ints, stat).setValue(value); }

    public boolean isDouble(String stat) { return doubles.containsKey(stat); }
    public double getDouble(String stat) { return holder(doubles, stat).getValue(); }
    public double getDefaultDouble(String stat) { return holder(doubles, stat).getDefault(); }
    public void setDouble(String stat, double value) { holder(doubles, stat).setValue(value); }

    public boolean isFloat(String stat) { return floats.containsKey(stat); }
    public float getFloat(String stat) { return holder(floats, stat).getValue(); }
    public float getDefaultFloat(String stat) { return holder(floats, stat).getDefault(); }
    public void setFloat(String stat, float value) { holder(floats, stat).setValue(value); }

    public boolean isLong(String stat) { return longs.containsKey(stat); }
    public long getLong(String stat) { return holder(longs, stat).getValue(); }
    public long getDefaultLong(String stat) { return holder(longs, stat).getDefault(); }
    public void setLong(String stat, long value) { holder(longs, stat).setValue(value); }

    public boolean isBool(String stat) { return bools.containsKey(stat); }
    public boolean getBool(String stat) { return holder(bools, stat).getValue(); }
    public boolean getDefaultBool(String stat) { return holder(bools, stat).getDefault(); }
    public void setBool(String stat, boolean value) { holder(bools, stat).setValue(value); }

    public boolean isString(String stat) { return strings.containsKey(stat); }
    public String getString(String stat) { return holder(strings, stat).getValue(); }
    public String getDefaultString(String stat) { return holder(strings, stat).getDefault(); }
    public void setString(String stat, String value) { holder(strings, stat).setValue(value); }

    public boolean isStringSet(String stat) { return stringSets.containsKey(stat); }
    public Collection<String> getStringSet(String stat) { return holder(stringSets, stat); }
    public void setStringSet(String stat, Collection<String> values) { stringSets.put(stat, values); }

    public void intStat(String name) { intStat(name, 0); }
    public void intStat(String name, int def) {
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        ints.put(name, new NumberHolder<>(def, def));
    }

    public void doubleStat(String name) { doubleStat(name, 0); }
    public void doubleStat(String name, double def) {
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        doubles.put(name, new NumberHolder<>(def, def));
    }

    public void floatStat(String name) { floatStat(name, 0); }
    public void floatStat(String name, float def) {
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        floats.put(name, new NumberHolder<>(def, def));
    }

    public void longStat(String name) { longStat(name, 0); }
    public void longStat(String name, long def) {
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        longs.put(name, new NumberHolder<>(def, def));
    }

    public void boolStat(String name) { boolStat(name, false); }
    public void boolStat(String name, boolean def) {
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        bools.put(name, new BooleanHolder(def, def));
    }

    public void stringStat(String name) { stringStat(name, ""); }
    public void stringStat(String name, String def) {
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        strings.put(name, new StringHolder(def, def));
    }

    public void stringSetStat(String name) { stringSetStat(name, new HashSet<>()); }
    public void stringSetStat(String name, Set<String> def) {
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        stringSets.put(name, def);
    }

    public Profile getBlankProfile(Player player) {
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
        for (Map.Entry<String, NumberHolder<Long>> entry : profile.longs.entrySet()) {
            this.longs.put(entry.getKey(), entry.getValue().copy());
        }
        for (Map.Entry<String, Collection<String>> entry : profile.stringSets.entrySet()) {
            this.stringSets.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        for (Map.Entry<String, StringHolder> entry : profile.strings.entrySet()) {
            this.strings.put(entry.getKey(), entry.getValue().copy());
        }
        for (Map.Entry<String, BooleanHolder> entry : profile.bools.entrySet()) {
            this.bools.put(entry.getKey(), entry.getValue().copy());
        }
    }

    private static <T> T holder(Map<String, T> map, String key) {
        T h = map.get(key);
        if (h == null) throw new IllegalArgumentException("Stat " + key + " not found");
        return h;
    }

    protected static class StringHolder {
        private String value, def;
        public StringHolder(String value, String def) { this.value = value; this.def = def; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getDefault() { return def; }
        public StringHolder copy() { return new StringHolder(value, def); }
    }

    protected static class NumberHolder<T extends Number> {
        private T value, def;
        public NumberHolder(T value, T def) { this.value = value; this.def = def; }
        public T getValue() { return value; }
        public void setValue(T value) { this.value = value; }
        public T getDefault() { return def; }
        public NumberHolder<T> copy() { return new NumberHolder<>(value, def); }
    }

    protected static class BooleanHolder {
        private boolean value, def;
        public BooleanHolder(boolean value, boolean def) { this.value = value; this.def = def; }
        public boolean getValue() { return value; }
        public void setValue(boolean value) { this.value = value; }
        public boolean getDefault() { return def; }
        public BooleanHolder copy() { return new BooleanHolder(value, def); }
    }
}
