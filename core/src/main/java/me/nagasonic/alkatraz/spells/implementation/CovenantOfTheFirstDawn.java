package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.HarmfulEffects;
import me.nagasonic.alkatraz.spells.util.SpellTargeting;
import me.nagasonic.alkatraz.spells.util.SustainEffect;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 9 LIGHT SUSTAIN: heals a team, cleanses harmful effects, and leaves a 45-second dawn
 * ward that turns one lethal hit into 1 HP. Void and administrative damage deliberately bypass it.
 */
public class CovenantOfTheFirstDawn extends Spell implements Listener {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.LIGHT);
    private static final int MAX_RECIPIENTS = 10;

    private record Ward(long expiresAt, UUID caster) {
    }

    /** player -> ward (expiry and owning caster). */
    private static final Map<UUID, Ward> WARDS = new ConcurrentHashMap<>();
    /** caster -> warded players, so the sustain teardown can drop the whole party at once. */
    private static final Map<UUID, Set<UUID>> WARDED_BY = new ConcurrentHashMap<>();
    private volatile boolean listenerRegistered;

    private int radius;
    private int wardSeconds;
    private double heal;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public CovenantOfTheFirstDawn(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/covenant_of_the_first_dawn_options.yml");
        Alkatraz.getInstance().save("spells/covenant_of_the_first_dawn.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/covenant_of_the_first_dawn.yml").get();
        loadCommonConfig(spellConfig);
        this.radius = spellConfig.getInt("radius", 24);
        this.wardSeconds = spellConfig.getInt("ward_seconds", 45);
        this.heal = spellConfig.getDouble("heal", 10.0);
        loadOptions();
        registerListenerOnce();
    }

    private void registerListenerOnce() {
        if (listenerRegistered) return;
        listenerRegistered = true;
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead() || !p.isOnline()) {
            cancelCast(p);
            return;
        }
        int reach = Math.max(4, (int) Math.round(
                getModifiedStat(p, "covenant_radius", option(p, "radius", radius))));
        int seconds = Math.max(5, (int) Math.round(
                getModifiedStat(p, "covenant_ward", option(p, "ward", wardSeconds))));
        double restored = Math.max(0, getModifiedStat(p, "covenant_heal", heal));

        List<Player> recipients = new ArrayList<>();
        recipients.add(p);
        recipients.addAll(SpellTargeting.allies(p, p.getLocation(), reach, MAX_RECIPIENTS - 1));

        UUID ownerId = p.getUniqueId();
        long expires = System.currentTimeMillis() + seconds * 1000L;
        SustainEffect.Toggle toggle = SustainEffect.toggle(p, 0.0, this,
                (caster, elapsedTicks) -> System.currentTimeMillis() < expires,
                caster -> settle(ownerId));
        if (toggle == SustainEffect.Toggle.BLOCKED) {
            Utils.sendActionBar(p, lang().get("spells.sustain.blocked"));
            cancelCast(p);
            return;
        }
        // A second cast toggles the ward off; the teardown already dropped every ward.
        if (toggle == SustainEffect.Toggle.CLOSED) return;

        Set<UUID> warded = new HashSet<>();
        for (Player ally : recipients) {
            ally.setHealth(Math.min(ally.getHealth() + restored, ally.getMaxHealth()));
            HarmfulEffects.clear(ally, 64);
            WARDS.put(ally.getUniqueId(), new Ward(expires, ownerId));
            warded.add(ally.getUniqueId());
            SpellVfx.selfAura(PALETTE.core(), p, ally, 1.1, 12, ally.getUniqueId().hashCode() * 0.01);
            Utils.sendActionBar(ally, lang().get("spells.covenantofthefirstdawn.aided_by")
                    .replace("{caster}", p.getName())
                    .replace("{seconds}", String.valueOf(seconds)));
        }
        WARDED_BY.put(ownerId, warded);
        SpellVfx.nova(PALETTE.accent(), p, p.getLocation(), 6.0, 40);
    }

    /** Idempotent: drops every ward the caster handed out. */
    private void settle(UUID ownerId) {
        Set<UUID> warded = WARDED_BY.remove(ownerId);
        if (warded == null) return;
        for (UUID id : warded) {
            Ward ward = WARDS.get(id);
            if (ward != null && ownerId.equals(ward.caster())) WARDS.remove(id, ward);
        }
    }

    /** Drops one ward and its owner's bookkeeping entry. */
    private static void forget(UUID id) {
        Ward ward = WARDS.remove(id);
        if (ward == null) return;
        Set<UUID> warded = WARDED_BY.get(ward.caster());
        if (warded != null) warded.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID id = player.getUniqueId();
        Ward ward = WARDS.get(id);
        if (ward == null) return;
        if (ward.expiresAt() < System.currentTimeMillis()) {
            forget(id);
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID || "KILL".equals(cause.name())) return;
        if (player.getHealth() - event.getFinalDamage() > 0) return;
        event.setCancelled(true);
        forget(id);
        player.setHealth(1.0);
        player.setFireTicks(0);
        Utils.sendActionBar(player, lang().get("spells.covenantofthefirstdawn.ward_spent"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        forget(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
    }

    @Override
    public void cancelTasks() {
        WARDED_BY.clear();
        WARDS.clear();
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 0.2, 0), scale * 2.4, 36, frame * 3, 0.1);
        SpellVfx.column(palette.accent(), caster, origin, 4.0, scale * 0.8, 4, 8, 0.6);
    }

    @Override
    public void drawAftermath(LivingEntity caster, Location origin, int tick, int totalTicks,
                              ElementPalette.Palette palette) {
        double progress = totalTicks <= 0 ? 1.0 : (double) tick / totalTicks;
        if (tick % 6 == 0) {
            SpellVfx.nova(palette.core(), caster, origin, 1.5 + progress * 6.0, 32);
            SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 1.0 + progress * 2.0, 0),
                    1.0 + progress * 4.0, 30, -tick * 2, 0.15);
        }
        if (tick % 10 == 0) {
            ParticleCaster.spawn(caster, origin.getWorld(), Utils.END_ROD,
                    origin.clone().add(0, 1.0, 0), 6, 0.6, 0.6, 0.6, 0.01);
        }
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
                .setDisplayName(lang().get("spells.covenantofthefirstdawn.book_name"))
                .addCustomLoreLine(lang().get("spells.covenantofthefirstdawn.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 9))
                .build();
    }
}
