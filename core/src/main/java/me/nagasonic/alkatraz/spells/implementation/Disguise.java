package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.playerdata.PlayerData;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.*;

import static me.nagasonic.alkatraz.util.ColorFormat.format;

public class Disguise extends Spell implements Listener {
    public Disguise(String type) {
        super(type);
    }
    private boolean selected = false;
    private Player caster;
    private Inventory gui;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/disguise.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/disguise.yml").get();

        loadCommonConfig(spellConfig);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        caster = p;
        openGUI(p);
    }

    @Override
    public int circleAction(Player p) {
        int d = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            Location playerLoc = p.getEyeLocation(); // Player eye location
            float yaw = playerLoc.getYaw();
            float pitch = playerLoc.getPitch();

            // Calculate offset vector pointing forward relative to player orientation
            Vector forward = playerLoc.getDirection().normalize().multiply(1.5); // 1.5 blocks in front

            // Call magicCircle with proper center, yaw, pitch and offset
            List<Location> magicCirclePoints = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 2, 0);

            // Spawn particles at all calculated points
            for (int i = 0; i < 100; i++){
                for (Location loc : magicCirclePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0, new Particle.DustOptions(Color.WHITE, 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }

    private Map<Integer, List<Player>> guiPages = new HashMap<>();

    public void openGUI(Player player){
        Collection<Spell> spells = SpellRegistry.getAllSpells().values();
        int i = 1;
        int p = 1;
        int pageNumbers = (int) Math.ceil((double) spells.size() / 36);
        List<Player> pagePlayers = new ArrayList<>();
        for (Player p1 : Bukkit.getServer().getOnlinePlayers()){
            if (i < 36){
                pagePlayers.add(p1);
                i++;
            }else{
                guiPages.put(p, pagePlayers);
                i = 1;
                p++;
                pagePlayers.clear();
            }
        }
        guiPages.put(p, pagePlayers);
        createGUI(1, player, pageNumbers);
    }

    @SuppressWarnings("deprecation")
    public void createGUI(int page, Player player, int totalPages){
        gui = Bukkit.createInventory(null, 54, "Disguise Target");
        for (int i = 0; i < 9; i++){
            gui.setItem(i, Utils.getBlank());
        }
        for (int i = 45; i <54; i++){
            gui.setItem(i, Utils.getBlank());
        }
        if (page < totalPages){
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            meta.setDisplayName(format("&fNext Page"));
            List<String> lore = new ArrayList<>();
            lore.add(format("&ePage " + (page + 1)));
            meta.setLore(lore);
            next.setItemMeta(meta);
            NBT.modify(next, nbt -> {
                nbt.setInteger("page", page + 1);
                nbt.setString("player", player.getUniqueId().toString());
                nbt.setInteger("total_pages", totalPages);
            });
            gui.setItem(53, next);
        }
        if (page > 1){
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta meta = prev.getItemMeta();
            meta.setDisplayName(format("&fPrevious Page"));
            List<String> lore = new ArrayList<>();
            lore.add(format("&ePage " + (page - 1)));
            meta.setLore(lore);
            prev.setItemMeta(meta);
            NBT.modify(prev, nbt -> {
                nbt.setInteger("page", page - 1);
                nbt.setString("player", player.getUniqueId().toString());
                nbt.setInteger("total_pages", totalPages);
            });
            gui.setItem(45, prev);
        }
        List<Player> players = guiPages.get(page);
        int s = 0;
        for (int i = 9; i < 45; i++){
            if (s < players.size()){
                Player p = players.get(s);
                ItemStack item;
                item = ItemUtils.headFromUuid(p.getUniqueId().toString());
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(p.getName());
                List<String> lore = new ArrayList<>();
                lore.add(format("&aClick to disguise as " + p.getName()));
                meta.setLore(lore);
                item.setItemMeta(meta);
                gui.setItem(i, item);
                s++;
            }
        }
        player.openInventory(gui);
    }

    // Page Changing Listeners
    @SuppressWarnings("deprecation")
    @EventHandler
    private void onInventoryClick(InventoryClickEvent e){
        if (e.getInventory().equals(gui)){
            e.setCancelled(true);
            ItemStack item = e.getCurrentItem();
            if (item != null) {
                if (item.getType() != Material.AIR && item.getAmount() > 0){
                    ItemMeta meta = item.getItemMeta();
                    if (meta.getDisplayName().equals(format("&fNext Page"))){
                        int newPage = NBT.get(item, nbt -> (Integer) nbt.getInteger("page"));
                        Player p = Bukkit.getPlayer(UUID.fromString(NBT.get(item, nbt -> (String) nbt.getString("player"))));
                        int totalPages = NBT.get(item, nbt -> (Integer) nbt.getInteger("total_pages"));
                        createGUI(newPage, p, totalPages);
                    }else if (meta.getDisplayName().equals(format("&fPrevious Page"))){
                        int newPage = NBT.get(item, nbt -> (Integer) nbt.getInteger("page"));
                        Player p = Bukkit.getPlayer(UUID.fromString(NBT.get(item, nbt -> (String) nbt.getString("player"))));
                        int totalPages = NBT.get(item, nbt -> (Integer) nbt.getInteger("total_pages"));
                        createGUI(newPage, p, totalPages);
                    } else if (item.getType().equals(Material.PLAYER_HEAD)) {
                        Player target = Bukkit.getPlayer(meta.getDisplayName());
                        Player p = (Player) e.getView().getPlayer();
                        PlayerData data = DataManager.getPlayerData(p);
                        if (target != null){
                            if (!Objects.equals(target.getUniqueId().toString(), data.getString("disguise"))){
                                data.setString("disguise", target.getUniqueId().toString());
                                p.setDisplayName(target.getName());
                                Skin skin = Skin.fromURL("https://sessionserver.mojang.com/session/minecraft/profile/" + target.getUniqueId() + "?unsigned=false");
                                Alkatraz.getNms().changeSkin(p, List.copyOf(Bukkit.getOnlinePlayers()), skin);
                                selected = true;
                                p.closeInventory();
                            }else{
                                p.sendMessage(format("&cYou are already disguised as this player."));
                            }
                        }else{
                            p.sendMessage(format("&cCouldn't find this player. Player must be online."));
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    private void onInventoryClose(InventoryCloseEvent e){
        if (e.getInventory().equals(gui)){
            if (e.getPlayer() == caster){
                if (!selected){
                    caster.sendMessage(format("&cNo player was chosen, cancelling casting..."));
                    DataManager.addMana(caster, getCost());
                }
            }
        }
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent e){
        Player p = e.getPlayer();
        for (Player other : Bukkit.getOnlinePlayers()){
            if (other != p){
                PlayerData odata = DataManager.getPlayerData(other);
                if (odata.getString("disguise") != null){
                    UUID uuid = UUID.fromString(odata.getString("disguise"));
                    if (Bukkit.getPlayer(uuid) != null){
                        Alkatraz.getNms().changeSkinElse(other, List.of(p), Skin.fromURL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false"));
                    }
                }else{
                    odata.setString("disguise", p.getUniqueId().toString());
                }
            }
        }
    }
}
