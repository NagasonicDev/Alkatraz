# Custom Crafting Adapter — Third-Party Extension Point

`CustomCraftingAdapter` (abstract class, `me.nagasonic.alkatraz.items.magic.recipe.adapter`) is the
public extension point for custom, GUI-driven crafting stations. Recipes for a custom station are
declared as `type: custom` in `magic/recipes/*.yml` and are **never** registered with the Bukkit
recipe manager — all interaction is handled through `InventoryClickEvent`.

## Contract

| Method | Notes |
|---|---|
| `RecipeType type()` | final — always returns `CUSTOM`. |
| `void registerNative(AlkatrazRecipe)` | final no-op — custom recipes are not Bukkit recipes. |
| `void onPrepare(PrepareItemCraftEvent)` | final no-op — station events never reach custom recipes. |
| `void handleClick(InventoryClickEvent, Player)` | implement this. Called for every inventory click while the adapter is registered. `player` is `null` for non-player viewers. |

## Registration

```java
CraftingEventRouter.register(new MyStationAdapter());
```

Multiple custom adapters may be registered; each receives every `InventoryClickEvent` and decides
whether the clicked inventory belongs to it.

## Recipe definition

Custom recipes are ordinary `magic/recipes/*.yml` files with `type: custom`. They are stored in
`RecipeRegistry` under `RecipeType.CUSTOM`. Resolve them from the adapter, for example:

```java
Set<NamespacedKey> keys = RecipeRegistry.getByStation(RecipeType.CUSTOM);
```

## Gating

Apply requirements/permissions with the shared gate inside `handleClick` when the station produces a
recipe result:

```java
if (!RecipeGate.canCraft(player, recipe)) {
    event.setCancelled(true);
}
```

## Example skeleton

```java
public class MyStationAdapter extends CustomCraftingAdapter {
    @Override
    public void handleClick(InventoryClickEvent event, Player player) {
        // your GUI logic here
    }
}
```

## Notes

- There is no built-in custom crafting GUI in this iteration.
- `RecipeManagerAPI.registerCustomAdapter(...)` will expose registration through the public API.
