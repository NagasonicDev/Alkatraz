# Alkatraz Spells API Reference

## Overview

The Alkatraz Spells API provides a comprehensive framework for creating custom spells in Minecraft.

## Core Interfaces

### SpellAPI
Main interface for spell management and registration:

```java
public interface SpellAPI {
    <T extends Spell> T registerSpell(Class<T> spellClass);
    SpellTemplate getSpellTemplate(String id);
    List<SpellTemplate> getAvailableSpells();
    boolean canCast(Player player, String spellId);
    void castSpell(Player player, String spellId, ItemStack wand);
    void openSpellMenu(Player player);
    void openSpellOptionsMenu(Player player, Spell spell);
    void addComponent(Spell spell, SpellComponent component);
    List<SpellComponent> getComponents(Spell spell);
    void reloadSpells();
    void saveSpellConfig(Spell spell, SpellConfig config);
    SpellConfig getSpellConfig(String spellId);
}
```

### Spell
Abstract base class for all spells:

```java
public abstract class Spell {
    protected final SpellType type;
    protected String id;
    protected String displayName;
    protected String description;
    protected Element element;
    protected int level = 1;
    protected int cost = 10;
    protected long cooldown = 1000;
    protected int requiredCircle = 1;
    protected boolean enabled = true;
    protected double castTime = 1.0;
    protected int maxMastery = 10;
    
    public abstract void loadConfiguration();
    public abstract void castAction(Player caster, ItemStack wand);
    public abstract void mobCastAction(Mob caster, ItemStack wand);
    public abstract ItemStack getSpellBook();
    
    // Optional hooks
    public void onPrepare(Player caster, ItemStack wand) {}
    public void onStart(Player caster, ItemStack wand) {}
    public void onComplete(Player caster, ItemStack wand) {}
    public void onHit(Entity target, Player caster) {}
}
```

### SpellTemplate
Immutable spell definition:

```java
public class SpellTemplate {
    private final String id;
    private final String name;
    private final Element element;
    private final int level;
    private final int manaCost;
    private final long cooldown;
    private final int requiredCircle;
    private final boolean enabled;
    private final String description;
    
    // Getters for all properties
}
```

### SpellComponent
Base interface for spell components:

```java
public interface SpellComponent {
    String getId();
    void apply(SpellContext context);
    void remove(SpellContext context);
}
```

## Spell Types

### SpellType Enum
Different categories of spells:

```java
public enum SpellType {
    QUICK("Quick/spontaneous magic"),
    SUSTAINED("Sustained effect magic"),
    ACTIVE("Active skill magic"),
    PASSIVE("Passive magic"),
    AOE("Area of effect magic"),
    PROJECTILE("Projectile magic"),
    HEALING("Healing magic"),
    BUFF("Buff magic"),
    DEBUFF("Debuff magic"),
    SHIELD("Shield magic");
}
```

### Element Enum
Magical elements:

```java
public enum Element {
    FIRE("Fire", "#ff8c00"),
    WATER("Water", "#0099cc"),
    EARTH("Earth", "#8b4513"),
    AIR("Air", "#87ceeb"),
    LIGHT("Light", "#ffff87"),
    DARK("Dark", "#8b008b"),
    NONE("None", "#808080");
}
```

## Component Types

### StatModifierComponent
Apply stat modifications:

```java
public class StatModifierComponent extends AbstractSpellComponent {
    private final String statName;
    private final double value;
    private final boolean stacks;
    
    public StatModifierComponent(String id, String statName, double value, boolean stacks) {
        super(id, SpellType.COMPONENT);
        this.statName = statName;
        this.value = value;
        this.stacks = stacks;
    }
}
```

### DamageComponent
Apply damage to targets:

```java
public class DamageComponent extends AbstractSpellComponent {
    private final double damage;
    private final Element element;
    
    public DamageComponent(String id, Element element, double damage) {
        super(id, SpellType.OFFENSE);
        this.element = element;
        this.damage = damage;
    }
}
```

### HealComponent
Heal targets:

```java
public class HealComponent extends AbstractSpellComponent {
    private final double healAmount;
    
    public HealComponent(String id, double healAmount) {
        super(id, SpellType.HEALING);
        this.healAmount = healAmount;
    }
}
```

## Event System

### SpellContext
Execution context for spells:

```java
public class SpellContext {
    private final Spell spell;
    private final Player caster;
    private final ItemStack wand;
    private final Entity target;
    private final Location location;
    
    // Getters and setters for context properties
}
```

### Event Types

```java
public class SpellPrepareEvent extends AbstractSpellEvent {
    public SpellPrepareEvent(Spell spell, Player caster, ItemStack wand)
}

public class SpellCastEvent extends AbstractSpellEvent {
    private final Entity target;
    
    public SpellCastEvent(Spell spell, Player caster, ItemStack wand, Entity target)
}
```

## Configuration System

### SpellConfig
Spell configuration model:

```java
public class SpellConfig {
    private String id;
    private String displayName;
    private String description;
    private String element;
    private int level;
    private int manaCost;
    private long cooldown;
    private int requiredCircle;
    private boolean enabled;
    private double castTime;
    private int maxMastery;
    private Map<String, Object> customProperties;
    
    // Getters and setters
}
```

## Builder Patterns

### SpellBuilder
Quick spell creation:

```java
public class SpellBuilder {
    public static QuickSpell.Builder createQuick()
    public static AdvancedSpell.Builder createAdvanced()
}

public class QuickSpell.Builder {
    public QuickSpell.Builder setId(String id)
    public QuickSpell.Builder setName(String name)
    public QuickSpell.Builder setElement(Element element)
    public QuickSpell.Builder setEffect(Runnable effect)
    public QuickSpell build()
}
```

## Menus and UI

### SpellMenu
Spell selection menu:

```java
public class SpellMenu extends AbstractPagedMenu<Spell> {
    public SpellMenu(List<Spell> spells, int itemsPerPage) {
        super(spells, itemsPerPage);
    }
    
    @Override
    public List<Spell> getItemsForPage(int page)
    @Override
    protected void renderPage(Player player)
}
```

### OptionMenu
Spell option configuration:

```java
public class OptionMenu extends AbstractPagedMenu<OptionValue<?>> {
    // Menu for selecting spell options
}
```

## Dependencies

### Required Libraries
- org.spigotmc:spigot-api
- de.tr7zw:nbtapi
- me.nagasonic.alkatraz:alkatraz-core

### Compile-Only Dependencies
- org.projectlombok:lombok
- org.jetbrains:annotations

## Versioning

### Semantic Versioning
- Major versions (1.x, 2.x) for API changes
- Minor versions (.0, .1, .2) for new features
- Patch versions (x.y.z) for bug fixes

### Compatibility
- API version 1.0.0 is compatible with Alkatraz 1.x
- Backwards-compatible changes only in minor/patch versions

## Performance Considerations

### Optimization Guidelines
1. **Component Caching**: Cache frequently used components
2. **Event Management**: Use conditional handlers to reduce unnecessary processing
3. **Memory Management**: Dispose of unused spell instances
4. **Thread Safety**: Use appropriate synchronization for shared resources

### Common Pitfalls
1. **Memory Leaks**: Ensure components and listeners are properly cleaned up
2. **Event Flooding**: Limit event firing frequency
3. **Configuration Loading**: Load configurations asynchronously

## License

This API is part of the Alkatraz project and is licensed under the terms specified in the parent project.
