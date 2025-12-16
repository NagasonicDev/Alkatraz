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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
    private Player caster;
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
            caster = p;
            PlayerData data = DataManager.getPlayerData(p);
            if (data.getBoolean("stealth")){
                data.setBoolean("stealth", false);
                DataManager.addMana(p, getCost()); //Give cost to reverse canceling
                for (Player player : Bukkit.getOnlinePlayers()){
                    Alkatraz.getNms().setInvisible(p, false);
                    Alkatraz.getNms().fakeArmor(p, player, p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots());
                    Alkatraz.getNms().setTransparent(p, player, false);
                }
            }else{
                data.setBoolean("stealth", true);
                for (Player player : Bukkit.getOnlinePlayers()){
                    if (player != p){
                        PlayerData td = DataManager.getPlayerData(player);
                        if (td.getInt("circle") < data.getInt("circle")){
                            Alkatraz.getNms().setInvisible(p, true);
                            Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
                        }else{
                            Alkatraz.getNms().setInvisible(p, true);
                            Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
                            Alkatraz.getNms().setTransparent(p, player, true);
                        }
                    }else {
                        Alkatraz.getNms().setInvisible(p, true);
                        Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
                        Alkatraz.getNms().setTransparent(p, player, true);
                    }
                }
                taskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
                    if (!data.getBoolean("stealth")){
                        stop();
                    }
                    if (!p.isDead()){
                        double cost = costs.get(data.getInt("circle"));
                        if (data.getDouble("mana") >= cost){
                            DataManager.subMana(p, cost);
                            for (Player player : Bukkit.getOnlinePlayers()){
                                if (player != p){
                                    PlayerData td = DataManager.getPlayerData(player);
                                    if (td.getInt("circle") <= data.getInt("circle")){
                                        player.spawnParticle(Particle.ASH, p.getLocation(), td.getInt("circle") - data.getInt("circle") + 11);
                                    }
                                }
                            }
                        }else{
                            data.setBoolean("stealth", false);
                            for (Player player : Bukkit.getOnlinePlayers()){
                                Alkatraz.getNms().setInvisible(p, false);
                                Alkatraz.getNms().fakeArmor(p, player, p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots());
                                Alkatraz.getNms().setTransparent(p, player, false);
                            }
                            stop();
                        }
                    }else {
                        data.setBoolean("stealth", false);
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
        if (data.getBoolean("stealth")){
            for (Player player : Bukkit.getOnlinePlayers()){
                Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
            }
        }
    }

    @EventHandler
    private void onInventoryClick(InventoryClickEvent e){
        Player p = (Player) e.getWhoClicked();
        PlayerData data = DataManager.getPlayerData(p);
        if (data.getBoolean("stealth")){
            for (Player player : Bukkit.getOnlinePlayers()){
                Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
            }
        }
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent e){
        if (caster != null){
            Player p = e.getPlayer();
            PlayerData td = DataManager.getPlayerData(caster);
            if (td.getBoolean("stealth")){
                PlayerData data = DataManager.getPlayerData(p);
                if (p != caster){
                    if (td.getInt("circle") < data.getInt("circle")){
                        Alkatraz.getNms().setInvisible(caster, true);
                        Alkatraz.getNms().fakeArmor(caster, p, null, null, null, null);
                    }else{
                        Alkatraz.getNms().setInvisible(caster, true);
                        Alkatraz.getNms().fakeArmor(caster, p, null, null, null, null);
                        Alkatraz.getNms().setTransparent(caster, p, true);
                    }
                }else {
                    Alkatraz.getNms().setInvisible(caster, true);
                    Alkatraz.getNms().fakeArmor(caster, caster, null, null, null, null);
                    Alkatraz.getNms().setTransparent(caster, caster, true);
                }
            }
        }
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent e){
        if (caster != null){
            Player player = e.getPlayer();
            PlayerData data = DataManager.getPlayerData(player);
            if (data.getBoolean("stealth")){
                data.setBoolean("stealth", false);
                Alkatraz.getNms().setInvisible(player, false);
                Alkatraz.getNms().fakeArmor(player, player, player.getInventory().getHelmet(), player.getInventory().getChestplate(), player.getInventory().getLeggings(), player.getInventory().getBoots());
                Alkatraz.getNms().setTransparent(player, player, false);
            }
        }
    }

    @EventHandler
    private void onWalk(PlayerMoveEvent e){
        if (e.getPlayer() == caster){
            PlayerData data = DataManager.getPlayerData(caster);
            if (data.getBoolean("stealth")){
                for (Player other : Bukkit.getOnlinePlayers()){
                    PlayerData odata = DataManager.getPlayerData(other);
                    if (odata.getInt("circle") >= data.getInt("circle")){
                        other.spawnParticle(Utils.DUST, caster.getLocation().add(0, 0.3, 0), 5, new Particle.DustOptions(Color.fromRGB(36, 36, 36), 0.5F));
                    }
                }
            }
        }
    }
}
