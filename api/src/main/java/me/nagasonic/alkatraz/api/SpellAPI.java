package me.nagasonic.alkatraz.api;

import me.nagasonic.alkatraz.api.playerdata.MagicProfileView;
import me.nagasonic.alkatraz.api.playerdata.ProfileProvider;
import me.nagasonic.alkatraz.api.spells.Spell;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Static facade providing access to the spell registry and player magic profiles.
 * <p>
 * Internally delegates to injected {@link SpellRegistryProvider} and {@link ProfileProvider}
 * implementations. Provider instances must be set via the corresponding setter methods
 * before any other methods are called, otherwise all lookups return {@code null} or empty results.
 */
public final class SpellAPI {

    private static SpellRegistryProvider spellRegistry;
    private static ProfileProvider profileProvider;

    private SpellAPI() {}

    // ── Spell Registry ────────────────────────────────────────────────

    /**
     * Checks whether the given player is allowed to cast the specified spell.
     * A player can cast a spell if it is enabled and the player's circle level
     * meets or exceeds the spell's required circle.
     *
     * @param player the player to check
     * @param spell  the spell to check against
     * @return {@code true} if the player meets the casting requirements
     */
    public static boolean canCast(Player player, Spell spell) {
        if (spell == null) return false;
        if (!spell.isEnabled()) return false;
        return player.getLevel() >= spell.getRequiredCircle();
    }

    /**
     * Injects the spell registry implementation used by this facade.
     *
     * @param provider the spell registry provider implementation
     */
    public static void setSpellRegistryProvider(SpellRegistryProvider provider) {
        spellRegistry = provider;
    }

    /**
     * Registers a spell with the underlying registry.
     *
     * @param spell the spell to register
     */
    public static void registerSpell(Spell spell) {
        if (spellRegistry != null) {
            spellRegistry.registerSpell(spell);
        }
    }

    /**
     * Retrieves a spell by its unique identifier.
     *
     * @param id the spell's unique identifier
     * @return the spell, or {@code null} if not found or the registry is not initialised
     */
    @Nullable
    public static Spell getSpell(String id) {
        if (spellRegistry == null) return null;
        return spellRegistry.getSpell(id);
    }

    /**
     * Retrieves a spell by its short code.
     *
     * @param code the spell's short code
     * @return the spell, or {@code null} if not found or the registry is not initialised
     */
    @Nullable
    public static Spell getSpellByCode(String code) {
        if (spellRegistry == null) return null;
        return spellRegistry.getSpellByCode(code);
    }

    /**
     * Returns all registered spells as an unmodifiable map of spell ID to spell.
     *
     * @return an unmodifiable map of all registered spells, or an empty map if the registry is not initialised
     */
    public static Map<String, Spell> getAllSpells() {
        if (spellRegistry == null) return Collections.emptyMap();
        return spellRegistry.getAllSpells();
    }

    // ── Profile Provider ──────────────────────────────────────────────

    /**
     * Injects the profile provider implementation used by this facade.
     *
     * @param provider the profile provider implementation
     */
    public static void setProfileProvider(ProfileProvider provider) {
        profileProvider = provider;
    }

    /**
     * Retrieves the magic profile for the given offline player.
     *
     * @param player the offline player whose profile to retrieve
     * @return the player's magic profile, or {@code null} if not found or the provider is not initialised
     */
    @Nullable
    public static MagicProfileView getProfile(OfflinePlayer player) {
        if (profileProvider == null) return null;
        return profileProvider.getProfile(player);
    }

    /**
     * Retrieves the magic profile for the player with the given UUID.
     *
     * @param uuid the UUID of the player whose profile to retrieve
     * @return the player's magic profile, or {@code null} if not found or the provider is not initialised
     */
    @Nullable
    public static MagicProfileView getProfile(UUID uuid) {
        if (profileProvider == null) return null;
        return profileProvider.getProfile(uuid);
    }

    /**
     * Persists the given magic profile through the underlying provider.
     *
     * @param profile the magic profile to save
     */
    public static void saveProfile(MagicProfileView profile) {
        if (profileProvider != null) {
            profileProvider.saveProfile(profile);
        }
    }
}
