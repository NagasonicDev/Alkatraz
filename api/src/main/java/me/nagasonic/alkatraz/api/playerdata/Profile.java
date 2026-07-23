package me.nagasonic.alkatraz.api.playerdata;

import org.bukkit.entity.Player;

import java.util.*;

/**
 * Abstract base class for player profile data storage.
 * Uses typed maps with holder wrappers to store arbitrary stats
 * as key-value pairs of various primitive types.
 */
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

    /**
     * Constructs a new Profile for the given owner.
     *
     * @param owner the UUID of the player this profile belongs to
     */
    public Profile(UUID owner) {
        if (owner == null) return;
        this.owner = owner;
    }

    /**
     * Returns the UUID of the player that owns this profile.
     *
     * @return the owner's UUID
     */
    public UUID getOwner() { return owner; }

    /**
     * Returns the names of all registered stats regardless of type.
     *
     * @return collection of all stat names
     */
    public Collection<String> getAllStatNames() { return allStatNames; }

    /**
     * Returns the names of all registered integer stats.
     *
     * @return collection of integer stat names
     */
    public Collection<String> getInts() { return ints.keySet(); }

    /**
     * Returns the names of all registered double stats.
     *
     * @return collection of double stat names
     */
    public Collection<String> getDoubles() { return doubles.keySet(); }

    /**
     * Returns the names of all registered float stats.
     *
     * @return collection of float stat names
     */
    public Collection<String> getFloats() { return floats.keySet(); }

    /**
     * Returns the names of all registered long stats.
     *
     * @return collection of long stat names
     */
    public Collection<String> getLongs() { return longs.keySet(); }

    /**
     * Returns the names of all registered boolean stats.
     *
     * @return collection of boolean stat names
     */
    public Collection<String> getBools() { return bools.keySet(); }

    /**
     * Returns the names of all registered string stats.
     *
     * @return collection of string stat names
     */
    public Collection<String> getStrings() { return strings.keySet(); }

    /**
     * Returns the names of all registered string-set stats.
     *
     * @return collection of string-set stat names
     */
    public Collection<String> getStringSets() { return stringSets.keySet(); }

    /**
     * Checks whether an integer stat with the given name exists.
     *
     * @param stat the stat name
     * @return {@code true} if the stat is registered
     */
    public boolean isInt(String stat) { return ints.containsKey(stat); }

    /**
     * Returns the current value of an integer stat.
     *
     * @param stat the stat name
     * @return the current value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public int getInt(String stat) { return holder(ints, stat).getValue(); }

    /**
     * Returns the default value of an integer stat.
     *
     * @param stat the stat name
     * @return the default value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public int getDefaultInt(String stat) { return holder(ints, stat).getDefault(); }

    /**
     * Sets the current value of an integer stat.
     *
     * @param stat the stat name
     * @param value the new value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public void setInt(String stat, int value) { holder(ints, stat).setValue(value); }

    /**
     * Checks whether a double stat with the given name exists.
     *
     * @param stat the stat name
     * @return {@code true} if the stat is registered
     */
    public boolean isDouble(String stat) { return doubles.containsKey(stat); }

    /**
     * Returns the current value of a double stat.
     *
     * @param stat the stat name
     * @return the current value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public double getDouble(String stat) { return holder(doubles, stat).getValue(); }

    /**
     * Returns the default value of a double stat.
     *
     * @param stat the stat name
     * @return the default value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public double getDefaultDouble(String stat) { return holder(doubles, stat).getDefault(); }

    /**
     * Sets the current value of a double stat.
     *
     * @param stat the stat name
     * @param value the new value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public void setDouble(String stat, double value) { holder(doubles, stat).setValue(value); }

    /**
     * Checks whether a float stat with the given name exists.
     *
     * @param stat the stat name
     * @return {@code true} if the stat is registered
     */
    public boolean isFloat(String stat) { return floats.containsKey(stat); }

    /**
     * Returns the current value of a float stat.
     *
     * @param stat the stat name
     * @return the current value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public float getFloat(String stat) { return holder(floats, stat).getValue(); }

    /**
     * Returns the default value of a float stat.
     *
     * @param stat the stat name
     * @return the default value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public float getDefaultFloat(String stat) { return holder(floats, stat).getDefault(); }

    /**
     * Sets the current value of a float stat.
     *
     * @param stat the stat name
     * @param value the new value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public void setFloat(String stat, float value) { holder(floats, stat).setValue(value); }

    /**
     * Checks whether a long stat with the given name exists.
     *
     * @param stat the stat name
     * @return {@code true} if the stat is registered
     */
    public boolean isLong(String stat) { return longs.containsKey(stat); }

    /**
     * Returns the current value of a long stat.
     *
     * @param stat the stat name
     * @return the current value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public long getLong(String stat) { return holder(longs, stat).getValue(); }

    /**
     * Returns the default value of a long stat.
     *
     * @param stat the stat name
     * @return the default value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public long getDefaultLong(String stat) { return holder(longs, stat).getDefault(); }

    /**
     * Sets the current value of a long stat.
     *
     * @param stat the stat name
     * @param value the new value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public void setLong(String stat, long value) { holder(longs, stat).setValue(value); }

    /**
     * Checks whether a boolean stat with the given name exists.
     *
     * @param stat the stat name
     * @return {@code true} if the stat is registered
     */
    public boolean isBool(String stat) { return bools.containsKey(stat); }

    /**
     * Returns the current value of a boolean stat.
     *
     * @param stat the stat name
     * @return the current value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public boolean getBool(String stat) { return holder(bools, stat).getValue(); }

    /**
     * Returns the default value of a boolean stat.
     *
     * @param stat the stat name
     * @return the default value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public boolean getDefaultBool(String stat) { return holder(bools, stat).getDefault(); }

    /**
     * Sets the current value of a boolean stat.
     *
     * @param stat the stat name
     * @param value the new value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public void setBool(String stat, boolean value) { holder(bools, stat).setValue(value); }

    /**
     * Checks whether a string stat with the given name exists.
     *
     * @param stat the stat name
     * @return {@code true} if the stat is registered
     */
    public boolean isString(String stat) { return strings.containsKey(stat); }

    /**
     * Returns the current value of a string stat.
     *
     * @param stat the stat name
     * @return the current value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public String getString(String stat) { return holder(strings, stat).getValue(); }

    /**
     * Returns the default value of a string stat.
     *
     * @param stat the stat name
     * @return the default value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public String getDefaultString(String stat) { return holder(strings, stat).getDefault(); }

    /**
     * Sets the current value of a string stat.
     *
     * @param stat the stat name
     * @param value the new value
     * @throws IllegalArgumentException if the stat is not registered
     */
    public void setString(String stat, String value) { holder(strings, stat).setValue(value); }

    /**
     * Checks whether a string-set stat with the given name exists.
     *
     * @param stat the stat name
     * @return {@code true} if the stat is registered
     */
    public boolean isStringSet(String stat) { return stringSets.containsKey(stat); }

    /**
     * Returns the current value of a string-set stat.
     *
     * @param stat the stat name
     * @return the current string collection
     * @throws IllegalArgumentException if the stat is not registered
     */
    public Collection<String> getStringSet(String stat) { return holder(stringSets, stat); }

    /**
     * Replaces the entire value of a string-set stat.
     *
     * @param stat the stat name
     * @param values the new string collection
     */
    public void setStringSet(String stat, Collection<String> values) { stringSets.put(stat, values); }

    /**
     * Registers a new integer stat with a default value of {@code 0}.
     *
     * @param name the stat name
     */
    public void intStat(String name) { intStat(name, 0); }

    /**
     * Registers a new integer stat with the given default value.
     * Duplicate names are silently ignored.
     *
     * @param name the stat name
     * @param def the default value
     */
    public void intStat(String name, int def) {
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        ints.put(name, new NumberHolder<>(def, def));
    }

    /**
     * Registers a new double stat with a default value of {@code 0}.
     *
     * @param name the stat name
     */
    public void doubleStat(String name) { doubleStat(name, 0); }

    /**
     * Registers a new double stat with the given default value.
     * Duplicate names are silently ignored.
     *
     * @param name the stat name
     * @param def the default value
     */
    public void doubleStat(String name, double def) {
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        doubles.put(name, new NumberHolder<>(def, def));
    }

    /**
     * Registers a new float stat with a default value of {@code 0}.
     *
     * @param name the stat name
     */
    public void floatStat(String name) { floatStat(name, 0); }

    /**
     * Registers a new float stat with the given default value.
     * Duplicate names are silently ignored.
     *
     * @param name the stat name
     * @param def the default value
     */
    public void floatStat(String name, float def) {
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        floats.put(name, new NumberHolder<>(def, def));
    }

    /**
     * Registers a new long stat with a default value of {@code 0}.
     *
     * @param name the stat name
     */
    public void longStat(String name) { longStat(name, 0); }

    /**
     * Registers a new long stat with the given default value.
     * Duplicate names are silently ignored.
     *
     * @param name the stat name
     * @param def the default value
     */
    public void longStat(String name, long def) {
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        longs.put(name, new NumberHolder<>(def, def));
    }

    /**
     * Registers a new boolean stat with a default value of {@code false}.
     *
     * @param name the stat name
     */
    public void boolStat(String name) { boolStat(name, false); }

    /**
     * Registers a new boolean stat with the given default value.
     * Duplicate names are silently ignored.
     *
     * @param name the stat name
     * @param def the default value
     */
    public void boolStat(String name, boolean def) {
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        bools.put(name, new BooleanHolder(def, def));
    }

    /**
     * Registers a new string stat with a default value of {@code ""}.
     *
     * @param name the stat name
     */
    public void stringStat(String name) { stringStat(name, ""); }

    /**
     * Registers a new string stat with the given default value.
     * Duplicate names are silently ignored.
     *
     * @param name the stat name
     * @param def the default value
     */
    public void stringStat(String name, String def) {
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        strings.put(name, new StringHolder(def, def));
    }

    /**
     * Registers a new string-set stat with an empty set as default.
     *
     * @param name the stat name
     */
    public void stringSetStat(String name) { stringSetStat(name, new HashSet<>()); }

    /**
     * Registers a new string-set stat with the given default set.
     * Duplicate names are silently ignored.
     *
     * @param name the stat name
     * @param def the default string set
     */
    public void stringSetStat(String name, Set<String> def) {
        if (allStatNames.contains(name)) return;
        allStatNames.add(name);
        stringSets.put(name, def);
    }

    /**
     * Returns a blank profile for the given player, delegating to
     * {@link #getBlankProfile(UUID)}.
     *
     * @param player the player
     * @return a new blank profile
     */
    public Profile getBlankProfile(Player player) {
        return getBlankProfile(player.getUniqueId());
    }

    /**
     * Creates and returns a new blank profile instance for the given owner UUID.
     *
     * @param owner the UUID of the player
     * @return a new blank profile
     */
    public abstract Profile getBlankProfile(UUID owner);

    /**
     * Copies all stat values and names from the given profile into this profile.
     *
     * @param profile the source profile to copy from
     */
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

    /**
     * Looks up a holder in the given map, throwing if the key is absent.
     *
     * @param map the stat map to search
     * @param key the stat name
     * @param <T> the holder type
     * @return the holder value
     * @throws IllegalArgumentException if the stat is not found
     */
    private static <T> T holder(Map<String, T> map, String key) {
        T h = map.get(key);
        if (h == null) throw new IllegalArgumentException("Stat " + key + " not found");
        return h;
    }

    /**
     * Holder wrapper for string stat values, tracking both the current and default values.
     */
    protected static class StringHolder {
        private String value, def;

        /**
         * @param value the current value
         * @param def the default value
         */
        public StringHolder(String value, String def) { this.value = value; this.def = def; }

        /** @return the current value */
        public String getValue() { return value; }

        /** @param value the new value */
        public void setValue(String value) { this.value = value; }

        /** @return the default value */
        public String getDefault() { return def; }

        /** @return a deep copy of this holder */
        public StringHolder copy() { return new StringHolder(value, def); }
    }

    /**
     * Generic holder wrapper for numeric stat values, tracking both the current and default values.
     *
     * @param <T> the numeric type (e.g. {@link Integer}, {@link Double})
     */
    protected static class NumberHolder<T extends Number> {
        private T value, def;

        /**
         * @param value the current value
         * @param def the default value
         */
        public NumberHolder(T value, T def) { this.value = value; this.def = def; }

        /** @return the current value */
        public T getValue() { return value; }

        /** @param value the new value */
        public void setValue(T value) { this.value = value; }

        /** @return the default value */
        public T getDefault() { return def; }

        /** @return a deep copy of this holder */
        public NumberHolder<T> copy() { return new NumberHolder<>(value, def); }
    }

    /**
     * Holder wrapper for boolean stat values, tracking both the current and default values.
     */
    protected static class BooleanHolder {
        private boolean value, def;

        /**
         * @param value the current value
         * @param def the default value
         */
        public BooleanHolder(boolean value, boolean def) { this.value = value; this.def = def; }

        /** @return the current value */
        public boolean getValue() { return value; }

        /** @param value the new value */
        public void setValue(boolean value) { this.value = value; }

        /** @return the default value */
        public boolean getDefault() { return def; }

        /** @return a deep copy of this holder */
        public BooleanHolder copy() { return new BooleanHolder(value, def); }
    }
}
