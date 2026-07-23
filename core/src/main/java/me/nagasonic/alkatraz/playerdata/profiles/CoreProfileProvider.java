package me.nagasonic.alkatraz.playerdata.profiles;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.api.playerdata.MagicProfileView;
import me.nagasonic.alkatraz.api.playerdata.ProfileProvider;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.OfflinePlayer;

import java.util.Collection;
import java.util.UUID;

public class CoreProfileProvider implements ProfileProvider {

    @Override
    public MagicProfileView getProfile(OfflinePlayer player) {
        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
        return profile == null ? null : new MagicProfileAdapter(profile);
    }

    @Override
    public MagicProfileView getProfile(UUID uuid) {
        MagicProfile profile = ProfileManager.getProfile(uuid, MagicProfile.class);
        return profile == null ? null : new MagicProfileAdapter(profile);
    }

    @Override
    public void saveProfile(MagicProfileView profile) {
        if (profile instanceof MagicProfileAdapter adapter) {
            ProfileManager.saveProfile(adapter.delegate);
        }
    }

    private static Element toApiElement(me.nagasonic.alkatraz.spells.Element core) {
        if (core == null) return Element.NONE;
        return switch (core) {
            case FIRE -> Element.FIRE;
            case WATER -> Element.WATER;
            case EARTH -> Element.EARTH;
            case AIR -> Element.AIR;
            case LIGHT -> Element.LIGHT;
            case DARK -> Element.DARK;
            case NONE -> Element.NONE;
        };
    }

    private static me.nagasonic.alkatraz.spells.Element toCoreElement(Element api) {
        if (api == null) return me.nagasonic.alkatraz.spells.Element.NONE;
        return switch (api) {
            case FIRE -> me.nagasonic.alkatraz.spells.Element.FIRE;
            case WATER -> me.nagasonic.alkatraz.spells.Element.WATER;
            case EARTH -> me.nagasonic.alkatraz.spells.Element.EARTH;
            case AIR -> me.nagasonic.alkatraz.spells.Element.AIR;
            case LIGHT -> me.nagasonic.alkatraz.spells.Element.LIGHT;
            case DARK -> me.nagasonic.alkatraz.spells.Element.DARK;
            case NONE -> me.nagasonic.alkatraz.spells.Element.NONE;
        };
    }

    private static class MagicProfileAdapter implements MagicProfileView {
        final MagicProfile delegate;

        MagicProfileAdapter(MagicProfile delegate) {
            this.delegate = delegate;
        }

        @Override public int getCircleLevel() { return delegate.getCircleLevel(); }
        @Override public void setCircleLevel(int value) { delegate.setCircleLevel(value); }
        @Override public int getStatPoints() { return delegate.getStatPoints(); }
        @Override public void setStatPoints(int value) { delegate.setStatPoints(value); }
        @Override public int getResetTokens() { return delegate.getResetTokens(); }
        @Override public void setResetTokens(int value) { delegate.setResetTokens(value); }

        @Override public int getPoints(Element element) { return delegate.getPoints(toCoreElement(element)); }
        @Override public double getMaxMana() { return delegate.getMaxMana(); }
        @Override public void setMaxMana(double value) { delegate.setMaxMana(value); }
        @Override public double getMana() { return delegate.getMana(); }
        @Override public void setMana(double value) { delegate.setMana(value); }
        @Override public double getManaRegeneration() { return delegate.getManaRegeneration(); }
        @Override public void setManaRegeneration(double value) { delegate.setManaRegeneration(value); }
        @Override public double getExperience() { return delegate.getExperience(); }
        @Override public void setExperience(double value) { delegate.setExperience(value); }
        @Override public double getArcaneKnowledge() { return delegate.getArcaneKnowledge(); }
        @Override public void setArcaneKnowledge(double value) { delegate.setArcaneKnowledge(value); }
        @Override public int getResearchPoints() { return delegate.getResearchPoints(); }
        @Override public void setResearchPoints(int value) { delegate.setResearchPoints(value); }

        @Override public double getMagicAffinity() { return delegate.getMagicAffinity(); }
        @Override public void setMagicAffinity(double value) { delegate.setMagicAffinity(value); }
        @Override public double getMagicResistance() { return delegate.getMagicResistance(); }
        @Override public void setMagicResistance(double value) { delegate.setMagicResistance(value); }

        @Override public double getAffinity(Element element) { return delegate.getAffinity(toCoreElement(element)); }
        @Override public double getResistance(Element element) { return delegate.getResistance(toCoreElement(element)); }

        @Override public boolean canCast() { return delegate.canCast(); }
        @Override public void setCanCast(boolean value) { delegate.setCanCast(value); }
        @Override public boolean isCasting() { return delegate.isCasting(); }
        @Override public void setCasting(boolean value) { delegate.setCasting(value); }
        @Override public boolean isStealth() { return delegate.isStealth(); }
        @Override public void setStealth(boolean value) { delegate.setStealth(value); }

        @Override public String getDisguise() { return delegate.getDisguise(); }
        @Override public void setDisguise(String value) { delegate.setDisguise(value); }
        @Override public String getCastMode() { return delegate.getCastMode(); }
        @Override public void setCastMode(String value) { delegate.setCastMode(value); }

        @Override public boolean hasDiscoveredSpell(String spellType) {
            return delegate.getStringSet("discoveredSpells").contains(spellType != null ? spellType.toLowerCase() : "");
        }
        @Override public Collection<String> getAllDiscoveredSpellTypes() { return delegate.getAllDiscoveredSpellTypes(); }

        @Override public boolean hasCompletedResearch(String researchId) { return delegate.hasCompletedResearch(researchId); }
        @Override public Collection<String> getCompletedResearchIds() { return delegate.getCompletedResearchIds(); }
        @Override public boolean hasStartedResearch(String researchId) { return delegate.hasStartedResearch(researchId); }

        @Override public int getSpellMastery(String spellId) {
            if (spellId == null) return 0;
            String key = "mastery_" + spellId;
            return delegate.isInt(key) ? delegate.getInt(key) : 0;
        }
        @Override public void setSpellMastery(String spellId, int mastery) {
            if (spellId == null) return;
            delegate.setInt("mastery_" + spellId, mastery);
        }

        @Override public Long getCooldown(String spellId) {
            if (spellId == null) return null;
            String id = spellId + "_cooldown";
            return delegate.longs.containsKey(id) ? delegate.getLong(id) : null;
        }
        @Override public void setCooldown(String spellId, Long cooldown) {
            if (spellId == null) return;
            String id = spellId + "_cooldown";
            if (!delegate.longs.containsKey(id)) {
                delegate.longStat(id, cooldown);
            } else {
                delegate.setLong(id, cooldown);
            }
        }
    }
}
