package me.nagasonic.alkatraz.api.spells;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.api.events.SpellPrepareEvent;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Abstract base class for all spells in the Alkatraz magic system.
 * <p>
 * This class follows the <b>Template Method</b> pattern: subclasses provide
 * concrete implementations of the abstract methods ({@link #loadConfiguration()},
 * {@link #castAction(Player, ItemStack)}, {@link #mobCastAction(Mob, ItemStack)},
 * {@link #circleAction(LivingEntity, SpellPrepareEvent)}, {@link #getSpellBook()})
 * while inheriting common spell metadata and behaviour from this base.
 * <p>
 * Spell instances are typically loaded from configuration and managed by the
 * Alkatraz spell registry.
 */
public abstract class Spell {
    protected final String type;
    protected String id;
    protected String displayName;
    protected String code;
    protected Element element;
    protected int cost;
    protected int requiredCircle;
    protected boolean enabled;
    protected List<String> description;
    protected BarColor masteryBarColor;
    protected ItemStack guiItem;
    protected long cooldown;
    protected double castTime;
    protected int level;
    protected int maxMastery;

    /**
     * Constructs a new spell with the given type identifier.
     *
     * @param type the unique type key of this spell, used for registration and lookup
     */
    public Spell(String type) {
        this.type = type;
    }

    /**
     * Loads this spell's configuration from the plugin's config files.
     * <p>
     * Implementations should populate all fields defined in {@link Spell}
     * (id, displayName, element, cost, etc.) from the appropriate configuration source.
     */
    public abstract void loadConfiguration();

    /**
     * Executes the spell's effect when cast by a player.
     *
     * @param caster the player casting the spell
     * @param wand   the wand item used to cast the spell
     */
    public abstract void castAction(Player caster, ItemStack wand);

    /**
     * Executes the spell's effect when cast by a mob.
     *
     * @param caster the mob casting the spell
     * @param wand   the wand item used to cast the spell
     */
    public abstract void mobCastAction(Mob caster, ItemStack wand);

    /**
     * Performs the spell's circle action during the preparation phase.
     * <p>
     * This is invoked while a spell is being charged or prepared, and may
     * modify the preparation event.
     *
     * @param caster the living entity casting the spell
     * @param event  the spell preparation event to modify
     * @return an integer result code (interpretation depends on the implementation)
     */
    public abstract int circleAction(LivingEntity caster, SpellPrepareEvent event);

    /**
     * Returns the item representation of this spell's spell book.
     *
     * @return the spell book {@link ItemStack}
     */
    public abstract ItemStack getSpellBook();

    /**
     * Hook for subclasses to initialise configurable spell options.
     * <p>
     * The default implementation does nothing; override to register option values.
     */
    protected void setupOptions() {
    }

    /**
     * Determines whether this spell can be cast by a mob.
     *
     * @param mob the mob attempting to cast
     * @return {@code true} if the mob may cast this spell, {@code false} otherwise
     */
    public boolean canMobCast(Mob mob) {
        return true;
    }

    /**
     * Cancels an in-progress spell cast for the given player.
     *
     * @param caster the player whose cast should be cancelled
     */
    protected void cancelCast(Player caster) {
        Set<UUID> cancelled = getCancelledPlayers();
        if (cancelled != null) {
            cancelled.add(caster.getUniqueId());
        }
    }

    /**
     * Returns the set of players whose spell cast has been cancelled.
     * <p>
     * Subclasses that support cancellation should override this to return
     * their tracking set. The default implementation returns {@code null}.
     *
     * @return the set of cancelled player UUIDs, or {@code null} if not supported
     */
    protected Set<UUID> getCancelledPlayers() {
        return null;
    }

    /**
     * Returns the unique type identifier of this spell.
     *
     * @return the spell type key
     */
    public String getType() { return type; }

    /**
     * Returns the configuration id of this spell.
     *
     * @return the spell id
     */
    public String getId() { return id; }

    /**
     * Returns the human-readable display name of this spell.
     *
     * @return the display name
     */
    public String getDisplayName() { return displayName; }

    /**
     * Returns the element associated with this spell.
     *
     * @return the spell element
     */
    public Element getElement() { return element; }

    /**
     * Returns the mana cost of this spell.
     *
     * @return the cost
     */
    public int getCost() { return cost; }

    /**
     * Returns the minimum magic circle level required to use this spell.
     *
     * @return the required circle level
     */
    public int getRequiredCircle() { return requiredCircle; }

    /**
     * Returns whether this spell is currently enabled.
     *
     * @return {@code true} if the spell is enabled
     */
    public boolean isEnabled() { return enabled; }

    /**
     * Returns the configuration code of this spell.
     *
     * @return the spell code
     */
    public String getCode() { return code; }

    /**
     * Returns the description lines displayed in the spell's GUI tooltip.
     *
     * @return an unmodifiable list of description lines
     */
    public List<String> getDescription() { return description; }

    /**
     * Returns the cooldown duration in milliseconds.
     *
     * @return the cooldown
     */
    public long getCooldown() { return cooldown; }

    /**
     * Returns the cast time in seconds.
     *
     * @return the cast time
     */
    public double getCastTime() { return castTime; }

    /**
     * Returns the maximum mastery level attainable for this spell.
     *
     * @return the maximum mastery level
     */
    public int getMaxMastery() { return maxMastery; }

    /**
     * Returns the current spell level.
     *
     * @return the spell level
     */
    public int getLevel() { return level; }

    /**
     * Alias for {@link #getRequiredCircle()}.
     *
     * @return the required circle level
     */
    public int getRequiredCircleLevel() { return getRequiredCircle(); }

    /**
     * Returns the boss-bar colour used to display mastery progress.
     *
     * @return the mastery bar colour
     */
    public BarColor getMasteryBarColor() { return masteryBarColor; }

    /**
     * Returns the item displayed for this spell in the GUI.
     *
     * @return the GUI item
     */
    public ItemStack getGuiItem() { return guiItem; }
}
