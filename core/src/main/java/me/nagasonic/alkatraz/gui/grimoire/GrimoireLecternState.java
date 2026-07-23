package me.nagasonic.alkatraz.gui.grimoire;

import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class GrimoireLecternState {

    private static final Map<UUID, GrimoireLecternState> activeLecterns = new ConcurrentHashMap<>();

    private final UUID playerUuid;
    private final ItemStack grimoireStack;
    private final MagicItemInstance instance;
    private final ItemDefinition definition;
    private final int totalPages;
    private int currentPage;
    private String castToken;

    public GrimoireLecternState(Player player, ItemStack grimoireStack, MagicItemInstance instance,
                                ItemDefinition definition, int totalPages, int startPage, String castToken) {
        this.playerUuid = player.getUniqueId();
        this.grimoireStack = grimoireStack;
        this.instance = instance;
        this.definition = definition;
        this.totalPages = totalPages;
        this.currentPage = startPage;
        this.castToken = castToken;
    }

    public static GrimoireLecternState get(Player player) {
        return activeLecterns.get(player.getUniqueId());
    }

    public static void put(Player player, GrimoireLecternState state) {
        activeLecterns.put(player.getUniqueId(), state);
    }

    public static void remove(Player player) {
        activeLecterns.remove(player.getUniqueId());
    }

    public static boolean isActive(Player player) {
        return activeLecterns.containsKey(player.getUniqueId());
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public ItemStack getGrimoireStack() { return grimoireStack; }
    public MagicItemInstance getInstance() { return instance; }
    public ItemDefinition getDefinition() { return definition; }
    public int getTotalPages() { return totalPages; }
    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int page) { this.currentPage = page; }
    public String getCastToken() { return castToken; }
    public void setCastToken(String token) { this.castToken = token; }
}
