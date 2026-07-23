package me.nagasonic.alkatraz.api.dom;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple generic key-value pair container.
 *
 * @param <T> the type of the first element (key)
 * @param <E> the type of the second element (value)
 */
public class Pair<T, E>{
    private T one;
    private E two;

    /**
     * Constructs a new pair with the given values.
     *
     * @param one the first value (key)
     * @param two the second value (value)
     */
    public Pair(T one, E two){
        this.one = one;
        this.two = two;
    }

    /** Returns the second value. */
    public E getTwo() { return two; }
    /** Returns the first value. */
    public T getOne() { return one; }
    /** Sets the first value. */
    public void setOne(T one) { this.one = one; }
    /** Sets the second value. */
    public void setTwo(E two) { this.two = two; }

    /**
     * Converts this pair and additional pairs into a {@link Map}.
     * This pair is included first, then all pairs in the collection are added,
     * potentially overwriting earlier entries with duplicate keys.
     *
     * @param pairs additional pairs to include in the map
     * @return a map containing all key-value pairs
     */
    public Map<T, E> map(Collection<Pair<T, E>> pairs){
        Map<T, E> map = new HashMap<>();
        map.put(one, two);
        pairs.forEach(p -> map.put(p.getOne(), p.getTwo()));
        return map;
    }
}
