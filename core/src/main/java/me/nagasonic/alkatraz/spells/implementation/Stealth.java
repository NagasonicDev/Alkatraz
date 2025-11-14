package me.nagasonic.alkatraz.spells.implementation;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.playerdata.DataManager;
import me.nagasonic.alkatraz.playerdata.PlayerData;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Stealth extends Spell implements Listener {
    public Stealth(String type){
        super(type);
    }
    private Map<Integer, Double> costs = new HashMap<>();
    private int taskID;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/stealth.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/stealth.yml").get();
        for (int i = 2; i <= 9; i++){
            costs.put(i, spellConfig.getDouble("cost_over_time.circle_" + i));
        }
        loadCommonConfig(spellConfig);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            PlayerData data = DataManager.getPlayerData(p);
            if (data.isStealth()){
                data.setStealth(false);
                DataManager.addMana(p, getCost()); //Give cost to reverse canceling
                for (Player player : Bukkit.getOnlinePlayers()){
                    Alkatraz.getNms().setInvisible(p, false);
                    Alkatraz.getNms().fakeArmor(p, player, p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots());
                    Alkatraz.getNms().setTransparent(p, player, false);
                }
            }else{
                data.setStealth(true);
                for (Player player : Bukkit.getOnlinePlayers()){
                    if (player != p){
                        PlayerData td = DataManager.getPlayerData(player);
                        if (td.getCircle() <= data.getCircle()){
                            Alkatraz.getNms().setInvisible(p, true);
                            Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
                        }else{
                            Alkatraz.getNms().setTransparent(p, player, true);
                        }
                    }else {
                        Alkatraz.getNms().setInvisible(p, true);
                        Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
                    }
                }
                taskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
                    if (!data.isStealth()){
                        stop();
                    }
                    if (!p.isDead()){
                        double cost = costs.get(data.getCircle());
                        if (data.getMana() >= cost){
                            DataManager.subMana(p, cost);
                            for (Player player : Bukkit.getOnlinePlayers()){
                                if (player != p){
                                    PlayerData td = DataManager.getPlayerData(player);
                                    if (td.getCircle() <= data.getCircle()){
                                        player.spawnParticle(Particle.ASH, p.getLocation(), td.getCircle() - data.getCircle() + 11);
                                    }
                                }
                            }
                        }else{
                            data.setStealth(false);
                            for (Player player : Bukkit.getOnlinePlayers()){
                                Alkatraz.getNms().setInvisible(p, false);
                                Alkatraz.getNms().fakeArmor(p, player, p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots());
                                Alkatraz.getNms().setTransparent(p, player, false);
                            }
                            stop();
                        }
                    }else {
                        data.setStealth(false);
                        for (Player player : Bukkit.getOnlinePlayers()){
                            Alkatraz.getNms().setInvisible(p, false);
                            Alkatraz.getNms().fakeArmor(p, player, p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots());
                            Alkatraz.getNms().setTransparent(p, player, false);
                        }
                        stop();
                    }
                }, 0L, 20L);
            }
        }
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
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0, new Particle.DustOptions(Color.GRAY, 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
        return d;
    }

    private void stop(){
        Bukkit.getServer().getScheduler().cancelTask(taskID);
    }

    @EventHandler
    private void onArmorEquip(PlayerArmorChangeEvent e){
        Player p = e.getPlayer();
        PlayerData data = DataManager.getPlayerData(p);
        if (data.isStealth()){
            for (Player player : Bukkit.getOnlinePlayers()){
                Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
            }
        }
    }

    @EventHandler
    private void onInventoryClick(InventoryClickEvent e){
        Player p = (Player) e.getWhoClicked();
        PlayerData data = DataManager.getPlayerData(p);
        if (data.isStealth()){
            for (Player player : Bukkit.getOnlinePlayers()){
                Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
            }
        }
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent e){
        Player player = e.getPlayer();
        PlayerData td = DataManager.getPlayerData(player);
        for (Player p : Bukkit.getOnlinePlayers()){
            PlayerData data = DataManager.getPlayerData(p);
            if (player != p){
                if (td.getCircle() <= data.getCircle()){
                    Alkatraz.getNms().setInvisible(p, true);
                    Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
                }else{
                    Alkatraz.getNms().setTransparent(p, player, true);
                }
            }else {
                Alkatraz.getNms().setInvisible(p, true);
                Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
            }
        }
    }
}
