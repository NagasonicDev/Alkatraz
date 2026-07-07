# Alkatraz Spells API - Quick Start Guide

## Introduction
Welcome to the Alkatraz Spells API! This guide will help you create custom spells for your Minecraft server.

## Quick Start

### 1. Creating a Simple Spell

The easiest way to create a spell is using the `QuickSpell` class:

```java
package com.example.spells;

import me.nagasonic.alkatraz.api.spells.QuickSpell;
import me.nagasonic.alkatraz.api.Element;

public class Firebolt extends QuickSpell {
    public Firebolt() {
        super("firebolt", "Firebolt", Element.FIRE, () -> {
            // Your spell effect here
            System.out.println("Firebolt fired!");
        });
    }
}
```

### 2. Using the Builder Pattern

For more control over your spell, use the SpellBuilder:

```java
package com.example.spells;

import me.nagasonic.alkatraz.api.builders.SpellBuilder;
import me.nagasonic.alkatraz.api.Element;

public class CustomSpell {
    public static QuickSpell createExampleSpell() {
        return SpellBuilder.createQuick()
            .setId("example_spell")
            .setName("Example Spell")
            .setElement(Element.LIGHT)
            .setEffect(() -> {
                // Spell logic here
                System.out.println("Spell effect applied!");
            })
            .build();
    }
}
```

### 3. Registering Your Spells

Register your custom spells with the SpellAPI:

```java
package com.example.spells;

import me.nagasonic.alkatraz.api.SpellAPI;

public class SpellLoader {
    public static void loadSpells(SpellAPI api) {
        // Register a quick spell
        api.registerSpell(Firebolt.class);
        api.registerSpell(ExampleQuickSpell.class);
    }
}
```

### 4. Complete Example: Fireball Spell

Here's a complete example of a Fireball spell:

```java
package com.example.spells;

import me.nagasonic.alkatraz.api.spells.QuickSpell;
import me.nagasonic.alkatraz.api.Element;
import org.bukkit.Particle;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class Fireball extends QuickSpell {
    public Fireball() {
        super("fireball", "Fireball", Element.FIRE, () -> {
            Location location = this.caster.getEyeLocation();
            location.getWorld().spawnParticle(Particle.FLAME, location, 20);
            // Additional logic for fireball trajectory
        });
    }
}
```

## Advanced Spell Creation

### 1. Creating an Advanced Spell

Use `AdvancedSpell` for more complex spells with components:

```java
package com.example.spells;

import me.nagasonic.alkatraz.api.spells.AdvancedSpell;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.api.components.StatModifierComponent;
import java.util.Arrays;
import java.util.List;

public class PowerSpell extends AdvancedSpell {
    private static final List<String> STAT_COMPONENTS = Arrays.asList("damage", "healing", "defense");
    
    public PowerSpell() {
        super("power_spell", "Power Spell", Element.LIGHT, 
            Arrays.asList(
                new StatModifierComponent("damage_boost", "damage", 50.0, true),
                new StatModifierComponent("healing_boost", "healing", 25.0, true)
            )
        );
    }
}
```

### 2. Event-Based Spells

Use events to create spells that react to game conditions:

```java
package com.example.spells;

import me.nagasonic.alkatraz.api.Spell;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.api.events.SpellEvent;
import me.nagasonic.alkatraz.api.events.SpellEventManager;

public class DefensiveSpell extends Spell {
    public DefensiveSpell() {
        super("defensive", "Defensive Spell", Element.LIGHT, null);
        
        // Register event handlers
        eventManager.registerHandler(SpellPrepareEvent.class, this::onPrepare);
        eventManager.registerConditionalHandler(
            SpellCastEvent.class,
            context -> context.getTarget() instanceof LivingEntity && 
                       ((LivingEntity) context.getTarget()).getHealth() < 10.0,
            this::onHealTarget
        );
    }
    
    private void onPrepare(SpellEvent event) {
        // Pre-cast logic
        event.getCaster().sendMessage("Preparing defensive spell...");
    }
    
    private void onHealTarget(SpellEvent event) {
        LivingEntity target = (LivingEntity) event.getTarget();
        target.setHealth(Math.min(target.getHealth() + 5, target.getMaxHealth()));
    }
}
```

## Component System

### 1. Built-in Components

The API includes several pre-built components:

```java
import me.nagasonic.alkatraz.api.components.*;
import me.nagasonic.alkatraz.api.Element;

// Create various components
SpellComponent damage = new DamageComponent("fire_damage", Element.FIRE, 10.0);
SpellComponent heal = new HealComponent("heal", 5.0);
SpellComponent statBoost = new StatModifierComponent("strength", "strength", 2.0, true);
```

### 2. Component Example

```java
package com.example.components;

import me.nagasonic.alkatraz.api.components.AbstractSpellComponent;
import me.nagasonic.alkatraz.api.Spell;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.api.events.SpellContext;

public class ShieldComponent extends AbstractSpellComponent {
    private final double shieldValue;
    
    public ShieldComponent(String id, double shieldValue) {
        super(id, SpellType.SHIELD);
        this.shieldValue = shieldValue;
    }
    
    @Override
    public void apply(SpellContext context) {
        context.getSpell().onStart(context.getCaster(), context.getWand());
        // Apply shield logic
    }
    
    @Override
    public void remove(SpellContext context) {
        // Remove shield logic
    }
}
```

## Menu Integration

### 1. Creating Menus

Create spell menus that allow players to select and configure spells:

```java
package com.example.menus;

import me.nagasonic.alkatraz.api.ui.menus.AbstractPagedMenu;
import me.nagasonic.alkatraz.api.spells.Spell;
import org.bukkit.entity.Player;

public class SpellMenu extends AbstractPagedMenu<Spell> {
    public SpellMenu(List<Spell> spells) {
        super(spells, 9); // 9 spells per page
    }
    
    @Override
    public List<Spell> getItemsForPage(int page) {
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, items.size());
        return items.subList(start, end);
    }
    
    @Override
    protected void renderPage(Player player) {
        List<Spell> spellsForPage = getItemsForPage(currentPage);
        // Render the spells on the menu
    }
}
```

## Performance Tips

1. **Reuse Components**: Create component instances once and reuse them
2. **Batch Updates**: Update spell state in batches to reduce overhead
3. **Cache Lookups**: Cache frequent lookups like spell templates and component registries
4. **Optimize Events**: Use conditional event handlers to reduce unnecessary event processing

## Migration Guide

### From Old API to New API

If you're migrating from the old spells system:

```java
// Old way (372-line monolithic class)
public class OldSpell extends Spell {
    // All the casting logic in one huge method
}

// New way (clean, focused)
public class OldSpell extends QuickSpell {
    public OldSpell() {
        super("old_spell", "Old Spell", Element.FIRE, this::castLogic);
        // Or simpler: use Effect pattern
    }
    
    private void castLogic() {
        // Your cast logic here
    }
}
```

## Next Steps

1. **Read the full API documentation**: Check out the API_REFERENCE.md file
2. **Review the examples**: Look at the ExampleSpells.java for more patterns
3. **Check compatibility**: Ensure your plugin version is compatible
4. **Join the community**: Visit the Alkatraz Discord for support and discussion

## FAQ

### Q: What's the difference between QuickSpell and AdvancedSpell?
A: QuickSpell is for simple, event-based spells. AdvancedSpell is for complex spells with many components and custom logic.

### Q: Do I need to implement all methods?
A: No, QuickSpell provides sensible defaults. You only need to implement the abstract methods that make sense for your spell type.

### Q: How do I add spell options?
A: Use the SpellOption system in the configuration module. You can load options from YAML files or define them programmatically.

### Q: Can I create spells without modifying the API module?
A: Yes, the API module provides all the necessary interfaces and base classes. You can create your spells in any plugin that depends on the Alkatraz Spells API.

## Examples Directory

For more comprehensive examples, check out:
- `examples/BasicSpell.java`: A basic working spell
- `examples/CustomSpell.java`: Real-world example with all features
- `examples/ComponentSpell.java`: Advanced component usage
