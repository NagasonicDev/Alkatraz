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

public final class SpellAPI {

    private static SpellRegistryProvider spellRegistry;
    private static ProfileProvider profileProvider;

    private SpellAPI() {}

    // ── Spell Registry ────────────────────────────────────────────────

    public static boolean canCast(Player player, Spell spell) {
        if (spell == null) return false;
        if (!spell.isEnabled()) return false;
        return player.getLevel() >= spell.getRequiredCircle();
    }

    public static void setSpellRegistryProvider(SpellRegistryProvider provider) {
        spellRegistry = provider;
    }

    public static void registerSpell(Spell spell) {
        if (spellRegistry != null) {
            spellRegistry.registerSpell(spell);
        }
    }

    @Nullable
    public static Spell getSpell(String id) {
        if (spellRegistry == null) return null;
        return spellRegistry.getSpell(id);
    }

    @Nullable
    public static Spell getSpellByCode(String code) {
        if (spellRegistry == null) return null;
        return spellRegistry.getSpellByCode(code);
    }

    public static Map<String, Spell> getAllSpells() {
        if (spellRegistry == null) return Collections.emptyMap();
        return spellRegistry.getAllSpells();
    }

    // ── Profile Provider ──────────────────────────────────────────────

    public static void setProfileProvider(ProfileProvider provider) {
        profileProvider = provider;
    }

    @Nullable
    public static MagicProfileView getProfile(OfflinePlayer player) {
        if (profileProvider == null) return null;
        return profileProvider.getProfile(player);
    }

    @Nullable
    public static MagicProfileView getProfile(UUID uuid) {
        if (profileProvider == null) return null;
        return profileProvider.getProfile(uuid);
    }

    public static void saveProfile(MagicProfileView profile) {
        if (profileProvider != null) {
            profileProvider.saveProfile(profile);
        }
    }
}
