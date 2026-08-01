package me.nagasonic.alkatraz.items.magic.recipe;

import me.nagasonic.alkatraz.configuration.requirement.Requirement;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class AlkatrazRecipe {
    private final NamespacedKey key;
    private final RecipeType type;
    private final ItemStack result;
    private final int resultAmount;
    private final String[] shape;
    private final Map<Character, Ingredient> ingredientMap;
    private final List<Ingredient> ingredients;
    private final ItemStack input;
    private final ItemStack base;
    private final ItemStack addition;
    private final double experience;
    private final int cookingTime;
    private final List<Requirement> requirements;
    private final List<String> permissions;
    private final boolean hiddenWhenLocked;
    private final String unlockMessage;
    private final String displayName;
    private final boolean overrideVanilla;

    private AlkatrazRecipe(Builder builder) {
        this.key = builder.key;
        this.type = builder.type;
        this.result = builder.result;
        this.resultAmount = builder.resultAmount;
        this.shape = builder.shape;
        this.ingredientMap = builder.ingredientMap;
        this.ingredients = builder.ingredients;
        this.input = builder.input;
        this.base = builder.base;
        this.addition = builder.addition;
        this.experience = builder.experience;
        this.cookingTime = builder.cookingTime;
        this.requirements = List.copyOf(builder.requirements);
        this.permissions = List.copyOf(builder.permissions);
        this.hiddenWhenLocked = builder.hiddenWhenLocked;
        this.unlockMessage = builder.unlockMessage;
        this.displayName = builder.displayName;
        this.overrideVanilla = builder.overrideVanilla;
    }

    public static Builder builder() {
        return new Builder();
    }

    public NamespacedKey getKey() { return key; }
    public RecipeType getType() { return type; }
    public ItemStack getResult() { return result; }
    public int getResultAmount() { return resultAmount; }
    public String[] getShape() { return shape; }
    public Map<Character, Ingredient> getIngredientMap() { return ingredientMap; }
    public List<Ingredient> getIngredients() { return ingredients; }
    public ItemStack getInput() { return input; }
    public ItemStack getBase() { return base; }
    public ItemStack getAddition() { return addition; }
    public double getExperience() { return experience; }
    public int getCookingTime() { return cookingTime; }
    public List<Requirement> getRequirements() { return requirements; }
    public List<String> getPermissions() { return permissions; }
    public boolean isHiddenWhenLocked() { return hiddenWhenLocked; }
    public String getUnlockMessage() { return unlockMessage; }
    public String getDisplayName() { return displayName; }
    public boolean isOverrideVanilla() { return overrideVanilla; }

    public static final class Builder {
        private NamespacedKey key;
        private RecipeType type = RecipeType.SHAPED;
        private ItemStack result;
        private int resultAmount = 1;
        private String[] shape;
        private Map<Character, Ingredient> ingredientMap;
        private List<Ingredient> ingredients = List.of();
        private ItemStack input;
        private ItemStack base;
        private ItemStack addition;
        private double experience;
        private int cookingTime;
        private List<Requirement> requirements = List.of();
        private List<String> permissions = List.of();
        private boolean hiddenWhenLocked;
        private String unlockMessage;
        private String displayName;
        private boolean overrideVanilla;

        public Builder key(NamespacedKey key) { this.key = key; return this; }
        public Builder type(RecipeType type) { this.type = type; return this; }
        public Builder result(ItemStack result) { this.result = result; return this; }
        public Builder resultAmount(int resultAmount) { this.resultAmount = resultAmount; return this; }
        public Builder shape(String[] shape) { this.shape = shape; return this; }
        public Builder ingredientMap(Map<Character, Ingredient> ingredientMap) { this.ingredientMap = ingredientMap; return this; }
        public Builder ingredients(List<Ingredient> ingredients) { this.ingredients = ingredients; return this; }
        public Builder input(ItemStack input) { this.input = input; return this; }
        public Builder base(ItemStack base) { this.base = base; return this; }
        public Builder addition(ItemStack addition) { this.addition = addition; return this; }
        public Builder experience(double experience) { this.experience = experience; return this; }
        public Builder cookingTime(int cookingTime) { this.cookingTime = cookingTime; return this; }
        public Builder requirements(List<Requirement> requirements) { this.requirements = requirements; return this; }
        public Builder permissions(List<String> permissions) { this.permissions = permissions; return this; }
        public Builder hiddenWhenLocked(boolean hiddenWhenLocked) { this.hiddenWhenLocked = hiddenWhenLocked; return this; }
        public Builder unlockMessage(String unlockMessage) { this.unlockMessage = unlockMessage; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder overrideVanilla(boolean overrideVanilla) { this.overrideVanilla = overrideVanilla; return this; }

        public AlkatrazRecipe build() {
            if (key == null) throw new IllegalStateException("AlkatrazRecipe key is required");
            if (result == null) throw new IllegalStateException("AlkatrazRecipe result is required");
            return new AlkatrazRecipe(this);
        }
    }
}
