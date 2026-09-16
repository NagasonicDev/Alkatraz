package me.nagasonic.alkatraz.api.ai;

/**
 * Umbrella marker type for every declarative AI specification in the framework.
 * Every config-shaped spec implements (directly or through a sub-interface)
 * {@code AiSpec}, giving the framework one spec family and one dispatch story
 * in the future engine — mirroring how the goal-brain specs are dispatched
 * today, generalized to the whole framework.
 *
 * <p>This umbrella is intentionally <em>not</em> {@code sealed}: the api jar
 * compiles in the unnamed module, where Java forbids a sealed type from
 * permitting subtypes declared in different packages. Each spec family
 * (e.g. {@code NativeBehaviorSpec}, {@code Sensor}, {@code MoveControlSpec})
 * is still sealed over its own permitted subtypes to keep the closed-set
 * property at the family level.
 */
public interface AiSpec {
}