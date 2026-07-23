package me.nagasonic.alkatraz.spells;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ApiSpellAdapter extends Spell {
    private final me.nagasonic.alkatraz.api.spells.Spell apiSpell;

    public ApiSpellAdapter(me.nagasonic.alkatraz.api.spells.Spell apiSpell) {
        super(apiSpell.getType());
        this.apiSpell = apiSpell;
        syncFields();
    }

    private void syncFields() {
        this.id = apiSpell.getId();
        this.displayName = apiSpell.getDisplayName();
        this.description = apiSpell.getDescription();
        this.element = Element.valueOf(apiSpell.getElement().name());
        this.code = apiSpell.getCode();
        this.masteryBarColor = apiSpell.getMasteryBarColor();
        this.guiItem = apiSpell.getGuiItem();
        this.cooldown = apiSpell.getCooldown();
        this.cost = apiSpell.getCost();
        this.castTime = apiSpell.getCastTime();
        this.level = apiSpell.getLevel();
        this.requiredCircle = apiSpell.getRequiredCircle();
        this.enabled = apiSpell.isEnabled();
        this.maxMastery = apiSpell.getMaxMastery();
    }

    public me.nagasonic.alkatraz.api.spells.Spell getApiSpell() {
        return apiSpell;
    }

    @Override
    public void loadConfiguration() {
        apiSpell.loadConfiguration();
        syncFields();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        apiSpell.castAction(p, wand);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        apiSpell.mobCastAction(caster, wand);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        // Can't delegate directly because event types are incompatible
        return 0;
    }

    @Override
    public ItemStack getSpellBook() {
        return apiSpell.getSpellBook();
    }

    @Override
    public boolean canMobCast(Mob mob) {
        return apiSpell.canMobCast(mob);
    }
}
