package me.nagasonic.alkatraz.api.ai.navigation;

import me.nagasonic.alkatraz.api.ai.AiSpec;

/**
 * Describes a mob's navigation. Wrapping {@link NavigationKind} in a spec lets
 * a profile express "keep the base class's default navigation untouched"
 * via {@code Default()}, consistent with the omit-to-keep-default rule.
 */
public sealed interface NavigationSpec extends AiSpec
        permits NavigationSpec.Default, NavigationSpec.FixedType {

    /** Keep the vanilla/default navigation of the base mob class. */
    record Default() implements NavigationSpec {}

    /** Force a specific navigation kind. */
    record FixedType(NavigationKind kind) implements NavigationSpec {
        public FixedType {
            if (kind == null) throw new NullPointerException("kind");
        }
    }
}