# Third-Party Recipe Integration

Alkatraz exposes its recipe system to other plugins through `RecipeManagerAPI`. Obtain the manager at
runtime and drive unlock state, queries, custom requirements and custom crafting stations.

## Dependency

`RecipeManagerAPI` lives in the `alkatraz-core` artifact (the API surface references core types such
as `AlkatrazRecipe`, so it cannot live in the `api` module). Depend on the core plugin's jar and
access the manager through the main class:

```java
RecipeManagerAPI api = Alkatraz.getRecipeManager();
```

## Querying recipes

```java
Optional<AlkatrazRecipe> recipe = api.getRecipe(key);   // NamespacedKey -> Optional
Collection<AlkatrazRecipe> recipes = api.getRecipes();  // everything registered
```

## Unlock state

```java
boolean unlocked = api.isUnlocked(player, key);
api.unlock(player, key);   // idempotent; fires RecipeUnlockedEvent + notifications
api.lock(player, key);     // idempotent; silent
```

Recipes without requirements are always unlocked. `api.canCraft(player, key)` reports whether the
player may craft right now (unlocked, or currently satisfying every requirement).

## Listening for unlocks

`RecipeUnlockedEvent` (core, `me.nagasonic.alkatraz.events`) is a public, non-cancellable Bukkit
event. Register a listener as usual:

```java
@EventHandler
public void onUnlock(RecipeUnlockedEvent event) {
    Player player = event.getPlayer();
    String key = event.getRecipeKey();
}
```

## Custom requirement types

1. Implement `RecipeRequirement` (from the `api` module, depends only on Bukkit).
2. Register it through the manager with `RecipeRequirementAdapter`:

```java
api.registerRequirementType("my_type", (spell, section) ->
        new RecipeRequirementAdapter(new MyRecipeRequirement(section)));
```

3. Use the type in recipe YAML: `requirements: [{ type: my_type, ... }]`.

`RecipeRequirementAdapter` bridges the third-party contract into the core requirement used by the
recipe book, detail menu and gating.

## Custom crafting stations

Extend `CustomCraftingAdapter` (see the [Custom Crafting Adapter](custom-crafting-adapter.md) guide)
and register it:

```java
api.registerCustomAdapter(new MyStationAdapter());
```

Custom recipes are declared with `type: custom` and are never registered with Bukkit.

## Reloading

`api.reload()` re-reads the `magic/recipes` YAML files and re-registers native Bukkit recipes. Use it
after changing recipe files at runtime.
