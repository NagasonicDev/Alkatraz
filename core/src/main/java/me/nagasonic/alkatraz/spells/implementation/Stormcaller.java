package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.AttackSpell;
import me.nagasonic.alkatraz.spells.types.BarrierSpell;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ChannelRegistry;
import me.nagasonic.alkatraz.spells.vfx.WindUp;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Circle 8 AIR utility: channel a storm that lifts the caster into controlled flight. */
public class Stormcaller extends AttackSpell implements Listener {

    private static final Color CAST_COLOR = Color.fromRGB(225, 255, 245);
    private static final Color FLIGHT_TINT = Color.fromRGB(160, 255, 210);
    private static final Particle.DustOptions FLIGHT_DUST = new Particle.DustOptions(FLIGHT_TINT, 1.0F);
    private static final Map<UUID, FlightState> FLIGHT_STATES = new ConcurrentHashMap<>();

    private StormcallerConfig config;

    public Stormcaller(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/stormcaller.yml");
        Alkatraz.getInstance().saveConfig("spells/stormcaller_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/stormcaller.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        config = StormcallerConfig.from(spellConfig);
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead() || !caster.isOnline()) return;
        UUID uuid = caster.getUniqueId();
        if (ChannelRegistry.isChanneling(uuid) || FLIGHT_STATES.containsKey(uuid)) return;
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        if (profile == null) return;

        int channelTicks = StormcallerConfig.channelTicksOf(
                getModifiedStat(caster, "channel_duration", config.channelDuration()),
                profile.getSpellMastery(this), getMaxMastery());
        WindUp windUp = WindUp.begin(this, CAST_COLOR, caster, channelTicks);
        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (caster.isDead() || !caster.isOnline()) {
                    cancel();
                    ChannelRegistry.unregister(uuid);
                    return;
                }
                windUp.tick();
                if (windUp.isCancelled() || windUp.isComplete()) {
                    cancel();
                    ChannelRegistry.unregister(uuid);
                    if (windUp.isComplete()) beginFlight(caster,
                            getModifiedStat(caster, "flight_duration", config.flightDuration()),
                            getModifiedStat(caster, "flight_speed", config.flightSpeed()));
                }
            }
        };
        ChannelRegistry.register(uuid, windUp, runnable::cancel);
        runnable.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void beginFlight(Player caster, double durationSeconds, double speed) {
        UUID uuid = caster.getUniqueId();
        if (caster.isDead() || !caster.isOnline() || FLIGHT_STATES.containsKey(uuid)) return;
        FlightState state = new FlightState(caster.getAllowFlight(), caster.isFlying(), caster.getFlySpeed());
        FLIGHT_STATES.put(uuid, state);
        caster.setAllowFlight(true);
        caster.setFlying(true);
        caster.setFlySpeed(clampFlySpeed(speed));
        World world = caster.getWorld();
        if (world != null) world.playSound(caster.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 1.0f, 0.7f);

        final int life = Math.max(1, (int) Math.round(durationSeconds * 20.0));
        new BukkitRunnable() {
            int tick;
            @Override
            public void run() {
                if (FLIGHT_STATES.get(uuid) != state || caster.isDead() || !caster.isOnline()
                        || tick >= life) {
                    cancel();
                    restoreFlight(caster);
                    return;
                }
                if (tick % 20 == 0) {
                    caster.setFlying(true);
                    drawFlightTell(caster);
                }
                tick++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    private void drawFlightTell(Player caster) {
        World world = caster.getWorld();
        if (world == null) return;
        Location at = caster.getLocation().add(0, 1.0, 0);
        ParticleCaster.spawn(caster, world, Utils.DUST, at, 14, FLIGHT_DUST);
        ParticleCaster.spawn(caster, world, Utils.CLOUD, at, 5, 0.35, 0.5, 0.35, 0.01);
    }

    private void restoreFlight(Player caster) {
        FlightState state = FLIGHT_STATES.remove(caster.getUniqueId());
        if (state == null) return;
        caster.setFlySpeed(state.flySpeed());
        caster.setAllowFlight(state.allowFlight());
        caster.setFlying(state.flying());
        World world = caster.getWorld();
        if (world != null) world.playSound(caster.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.6f, 1.4f);
    }

    private static float clampFlySpeed(double speed) {
        if (Double.isNaN(speed)) return 0.05F;
        return (float) Math.max(-1.0, Math.min(1.0, speed));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // This utility is player flight; mobs have no Bukkit flight attributes.
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { restoreFlight(event.getPlayer()); }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) { restoreFlight(event.getEntity()); }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) { restoreFlight(event.getPlayer()); }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() != Alkatraz.getInstance()) return;
        for (UUID uuid : FLIGHT_STATES.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) restoreFlight(player);
        }
        FLIGHT_STATES.clear();
    }

    @Override
    public void onHitBarrier(BarrierSpell barrier, Location location, LivingEntity caster) {
        ParticleCaster.spawn(caster, location.getWorld(), Utils.EXPLOSION_EMITTER, location, 1);
        location.getWorld().playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.7f);
    }

    @Override
    public void onCountered(Location location) {
        ParticleCaster.spawn(null, location.getWorld(), Utils.CLOUD, location, 16, 0.5, 0.5, 0.5, 0.01);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.8f);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent event) {
        return CastCircle.start(this, caster, event, 3.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.stormcaller.book_name"))
                .addCustomLoreLine(lang().get("spells.stormcaller.lore1"))
                .addCustomLoreLine(lang().get("spells.stormcaller.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 8))
                .build();
    }

    private record FlightState(boolean allowFlight, boolean flying, float flySpeed) { }
}
