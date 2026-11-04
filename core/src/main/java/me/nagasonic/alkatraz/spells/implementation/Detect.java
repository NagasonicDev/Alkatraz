package me.nagasonic.alkatraz.spells.implementation;

import com.google.common.util.concurrent.AtomicDouble;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import org.bukkit.Color;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Detect extends Spell {

    /**
     * This spell's colour, handed to every shared VFX builder it calls.
     *
     * <p>Seeded with the `NONE` element's core shade so the spell looks unchanged, but
     * it is now a value you own: edit this one line to re-tint the whole spell, and the
     * two supporting shades follow automatically.
     */
    private static final Color CAST_COLOR = Color.fromRGB(180, 180, 180);
    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    /**
     * The single NONE palette. The scan ring was hardcoded white, which was indistinguishable
     * from every other white ring in the plugin. It now takes {@code core} for the leading
     * edge and {@code deep} for the ground shadow the ring lays underneath itself.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.NONE);

    /** Ring density; {@link SpellVfx#ring} takes a fixed point count, not a frequency. */
    private static final int RING_POINTS = 40;
    /** Ground-shadow offset for the counter-rotating shadow ring, in blocks. */
    private static final double RING_SHADOW_OFFSET = 0.35;
    /** Largest scan radius, in blocks; drives how far the revealing ring travels. */
    private static final int SCAN_MAX_RADIUS = 20;
    /** Radius step per tick, in blocks. */
    private static final double SCAN_STEP = 0.5;

    public Detect(String type){
        super(type);
    }
    private Map<Integer, Double> ranges = new HashMap<>();
    private final Map<UUID, List<LivingEntity>> entitiesByCaster = new ConcurrentHashMap<>();
    private long duration;

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/detect.yml");
        Alkatraz.getInstance().saveConfig("spells/detect_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/detect.yml").get();
        for (int i = 2; i <= 9; i++){
            ranges.put(i, spellConfig.getDouble("range.circle_" + i));
        }
        duration = spellConfig.getLong("detect_duration");
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()){
            AtomicDouble l = new AtomicDouble(1);
            Location a = p.getLocation();
            MagicProfile data = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
            double range = ranges.get(data.getCircleLevel()) * (Double) getOption("scan_range").getSelectedValue(p).getValue();
            long activeDuration = Math.round((Double) getOption("glow_duration").getSelectedValue(p).getValue());
            int r = SCAN_MAX_RADIUS;
            final List<LivingEntity> revealed = new ArrayList<>();
            final Set<UUID> seen = new HashSet<>();
            entitiesByCaster.put(p.getUniqueId(), revealed);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!p.isOnline() || p.isDead()) {
                        cancel();
                        return;
                    }
                    if (l.get() < r){
                        double ringRadius = l.get();
                        // The scan ring runs EVERY tick, not on circle_ticks, and expands to the
                        // max radius before the task stops. That faster cadence is the whole
                        // tell of this spell - it reads as a sweep, not a pulse.
                        SpellVfx.ring(CAST_COLOR, p, a, ringRadius, RING_POINTS,
                                ringRadius * 0.3D, RING_SHADOW_OFFSET);
                        l.addAndGet(SCAN_STEP);
                        if (l.get() == (double) r / 2){
                            for (Entity entity : a.getWorld().getNearbyEntities(a, range, range, range)){
                                if (!entity.isDead() && entity != p && entity instanceof LivingEntity le){
                                    ChatColor color = ChatColor.WHITE;
                                    if (le instanceof Monster){
                                        color = ChatColor.RED;
                                    } else if (le instanceof Player) {
                                        color = ChatColor.YELLOW;
                                    }
                                    if (le instanceof Player target){
                                        MagicProfile tdata = ProfileManager.getProfile(target.getUniqueId(), MagicProfile.class);
                                        if (tdata == null) continue;
                                        if (data.getCircleLevel() >= tdata.getCircleLevel()){
                                            if (tdata.isStealth()){
                                                Alkatraz.getNms().setGlowing(target, p, ChatColor.DARK_GRAY);
                                            }else{
                                                Alkatraz.getNms().setGlowing(target, p, color);
                                            }
                                            if (seen.add(le.getUniqueId())){
                                                revealed.add(le);
                                            }
                                        }
                                    }else{
                                        Alkatraz.getNms().setGlowing(le, p, color);
                                        if (seen.add(le.getUniqueId())){
                                            revealed.add(le);
                                        }
                                    }
                                }
                            }
                        }
                    }else { cancel();}
                }
            }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
                entitiesByCaster.remove(p.getUniqueId(), revealed);
                for (LivingEntity le : revealed){
                    Alkatraz.getNms().unsetGlowing(le, p);
                }
            }, activeDuration * 20);
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Mob Detect is an announce sweep only - the player path's glow reveal is
        // per-viewer NMS state, which a mob caster has no use for.
        Location a = caster.getLocation();
        AtomicDouble l = new AtomicDouble(1);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!caster.isValid() || caster.isDead()) {
                    cancel();
                    return;
                }
                if (l.get() < SCAN_MAX_RADIUS) {
                    SpellVfx.ring(CAST_COLOR, caster, a, l.get(), RING_POINTS,
                            l.get() * 0.3D, RING_SHADOW_OFFSET);
                    l.addAndGet(SCAN_STEP);
                } else {
                    cancel();
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.detect.book_name"))
                .addCustomLoreLine(lang().get("spells.detect.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 2))
                .build();
    }
}
