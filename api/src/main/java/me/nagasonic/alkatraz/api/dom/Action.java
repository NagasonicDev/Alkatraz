package me.nagasonic.alkatraz.api.dom;

/**
 * A generic functional interface representing an operation to be performed on a target object.
 *
 * @param <T> the type of the target object this action operates on
 */
public interface Action<T>{

    /**
     * Performs this action on the given target.
     *
     * @param on the target object to act upon
     */
    void act(T on);
}
