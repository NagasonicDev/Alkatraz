package me.nagasonic.alkatraz.api.dom;

/**
 * A generic functional interface representing a supplier that retrieves a value of type {@code T}.
 *
 * @param <T> the type of value provided by this fetcher
 */
public interface Fetcher<T> {

    /**
     * Retrieves and returns the value.
     *
     * @return the fetched value
     */
    T get();
}
