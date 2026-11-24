package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.SpellTargeting;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 6 FIRE UTILITY: seals one ally (or the caster, when no ally is close) so the next
 * lethal blow leaves them standing on a few hit points instead of killing them. The seal
 * grants no invulnerability window, it only refuses the one hit that would have ended them.
 */
public class PhoenixReprieve extends Spell implements Listener {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.FIRE);
    /** The seal reaches this far, in blocks. */
    private static final double SEAL_RANGE = 20.0;
    /** Hit points a seal leaves after a lethal blow from a mob. */
    private static final double PVE_FLOOR = 4.0;
    /** Fallback PvP floor when the health option is unavailable. */
    private static final double DEFAULT_PVP_CAP = 4.0;
    private static final int FIRE_RESISTANCE_TICKS = 60;

    private record Seal(double floor, long expiresAt) {}

    private final Map<UUID, Seal> seals = new ConcurrentHashMap<>();
    /** Periodic sweep so expired seals vanish even when no cast and no lethal hit follows. */
    private BukkitTask sealSweep;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public PhoenixReprieve(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/phoenix_reprieve_options.yml");
        Alkatraz.getInstance().save("spells/phoenix_reprieve.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/phoenix_reprieve.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        pruneExpired();
        int seconds = (int) Math.round(getModifiedStat(p, "reprieve_duration", option(p, "duration", 45)));
        double pvpCap = ConfigManager.getConfig("spells/phoenix_reprieve_options.yml").get()
                .getDouble("pvp_damage_cap", DEFAULT_PVP_CAP);
        double floor = Math.max(1.0, getModifiedStat(p, "reprieve_health", option(p, "health", pvpCap)));

        LivingEntity target = p;
        List<Player> allies = SpellTargeting.allies(p, p.getLocation(), SEAL_RANGE, 1);
        if (!allies.isEmpty()) target = allies.get(0);

        seals.put(target.getUniqueId(), new Seal(floor, System.currentTimeMillis() + seconds * 1000L));
        startSealSweep();
        SpellVfx.selfAura(PALETTE.core(), p, target, 1.0, 16, 2);
        SpellVfx.ring(PALETTE.accent(), p, target.getLocation(), 1.4, 16, 0, 1.0);
        Utils.sendActionBar(p, lang().get("spells.phoenixreprieve.sealed")
                .replace("{seconds}", String.valueOf(seconds)));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        seals.put(caster.getUniqueId(), new Seal(PVE_FLOOR, System.currentTimeMillis() + 45_000L));
        startSealSweep();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        Seal seal = seals.get(victim.getUniqueId());
        if (seal == null) return;
        if (System.currentTimeMillis() > seal.expiresAt()) {
            seals.remove(victim.getUniqueId());
            return;
        }
        double remaining = victim.getHealth() - event.getFinalDamage();
        if (remaining > 0) return;

        seals.remove(victim.getUniqueId());
        event.setCancelled(true);
        double floor = seal.floor();
        victim.setHealth(Math.min(victim.getMaxHealth(), Math.max(1.0, floor)));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                FIRE_RESISTANCE_TICKS, 0, true, true, true));
        if (victim instanceof Player player) {
            Utils.sendActionBar(player, lang().get("spells.phoenixreprieve.burned"));
        }
        SpellVfx.nova(PALETTE.accent(), victim, victim.getLocation(), 1.6, 20);
    }

    /** Drops seals whose expiry passed while nobody hit the victim. */
    private void pruneExpired() {
        long now = System.currentTimeMillis();
        seals.values().removeIf(seal -> seal.expiresAt() <= now);
    }

    /** Starts the sweep task if it is not running yet. */
    private void startSealSweep() {
        if (sealSweep == null) {
            sealSweep = Alkatraz.getInstance().getServer().getScheduler()
                    .runTaskTimer(Alkatraz.getInstance(), this::pruneExpired, 200L, 200L);
        }
    }

    @Override
    public void cancelTasks() {
        if (sealSweep != null) {
            sealSweep.cancel();
            sealSweep = null;
        }
        seals.clear();
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 20) / 20.0;
        SpellVfx.column(palette.accent(), caster, origin, scale * (0.6 + phase * 1.4), scale * 0.7, 2, 8, 0.8);
    }

    /** Numeric option value, falling back when the option file is absent. */
    private double option(Player p, String id, double fallback) {
        if (getOption(id) == null) return fallback;
        Object value = getOption(id).getSelectedValue(p).getValue();
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.phoenixreprieve.book_name"))
                .addCustomLoreLine(lang().get("spells.phoenixreprieve.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 6))
                .build();
    }
}
