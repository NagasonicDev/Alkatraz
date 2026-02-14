package me.nagasonic.alkatraz.spells.implementation;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.PlayerSpellPrepareEvent;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.util.ParticleUtils;
import me.nagasonic.alkatraz.util.StatUtils;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Stealth extends Spell implements Listener {
    public Stealth(String type){
        super(type);
    }
    private Map<Integer, Double> costs = new HashMap<>();
    private List<Player> casters = new ArrayList<>();
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
            casters.add(p);
            MagicProfile data = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
            if (data.isStealth()){
                data.setStealth(false);
                StatUtils.addMana(p, getCost()); //Give cost to reverse canceling
                for (Player player : Bukkit.getOnlinePlayers()){
                    Alkatraz.getNms().setInvisible(p, false);
                    Alkatraz.getNms().fakeArmor(p, player, p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots());
                    Alkatraz.getNms().setTransparent(p, player, false);
                }
            }else{
                data.setStealth(true);
                for (Player player : Bukkit.getOnlinePlayers()){
                    if (player != p){
                        MagicProfile td = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
                        if (td.getCircleLevel() < data.getCircleLevel()){
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
                    if (!data.isStealth()){
                        stop();
                    }
                    if (!p.isDead()){
                        double cost = costs.get(data.getCircleLevel());
                        if (data.getMana() >= cost){
                            StatUtils.subMana(p, cost);
                            for (Player player : Bukkit.getOnlinePlayers()){
                                if (player != p){
                                    MagicProfile td = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
                                    if (td.getCircleLevel() <= data.getCircleLevel()){
                                        player.spawnParticle(Particle.ASH, p.getLocation(), td.getCircleLevel() - data.getCircleLevel() + 11);
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
    public int circleAction(Player p, PlayerSpellPrepareEvent e) {
        int d = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
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
        MagicProfile data = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
        if (data.isStealth()){
            for (Player player : Bukkit.getOnlinePlayers()){
                Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
            }
        }
    }

    @EventHandler
    private void onInventoryClick(InventoryClickEvent e){
        Player p = (Player) e.getWhoClicked();
        MagicProfile data = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
        if (data.isStealth()){
            for (Player player : Bukkit.getOnlinePlayers()){
                Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
            }
        }
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent e){
        if (casters == null) return;
        for (Player caster : casters) {
            if (caster != null){
                Player p = e.getPlayer();
                MagicProfile td = ProfileManager.getProfile(caster.getUniqueId(), MagicProfile.class);
                if (td.isStealth()){
                    MagicProfile data = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
                    if (p != caster){
                        if (td.getCircleLevel() < data.getCircleLevel()){
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
                }else casters.remove(caster);
            }
        }
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent e){
        Player player = e.getPlayer();
        MagicProfile data = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
        if (data.isStealth()){
            data.setStealth(false);
            Alkatraz.getNms().setInvisible(player, false);
            Alkatraz.getNms().fakeArmor(player, player, player.getInventory().getHelmet(), player.getInventory().getChestplate(), player.getInventory().getLeggings(), player.getInventory().getBoots());
            Alkatraz.getNms().setTransparent(player, player, false);
        }
    }

    @EventHandler
    private void onWalk(PlayerMoveEvent e){
        if (casters == null) return;
        if (casters.contains(e.getPlayer())){
            MagicProfile data = ProfileManager.getProfile(e.getPlayer().getUniqueId(), MagicProfile.class);
            if (data.isStealth()){
                for (Player other : Bukkit.getOnlinePlayers()){
                    MagicProfile odata = ProfileManager.getProfile(other.getUniqueId(), MagicProfile.class);
                    if (odata.getCircleLevel() >= data.getCircleLevel()){
                        other.spawnParticle(Utils.DUST, e.getPlayer().getLocation().add(0, 0.3, 0), 5, new Particle.DustOptions(Color.fromRGB(36, 36, 36), 0.5F));
                    }
                }
            }
        }
    }
}
