package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.modifier.AppliedModifier;
import me.nagasonic.alkatraz.spells.modifier.PooledModifierSpellSupport;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.util.ColorFormat;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 8 LIGHT, and the element's identity statement rather than its damage statement: a
 * transformation, not a weapon.
 *
 * <p>You stand still while the light climbs you, and while it holds your own spells cost
 * less and hit harder. {@code role: 'UTILITY'}, bare {@link Spell}, and <b>no {@code power}
 * key</b> - it deals no damage of its own, which is what
 * {@code SpellBudgetTest.utilitySpellsCarryNoDamageKey} asserts.
 *
 * <h2>Why bare {@code Spell} is correct here</h2>
 * Damage-contract rule 4 is a requirement <em>on damage spells</em>. Extending
 * {@code AttackSpell} would hand this class a {@code getBasePower()} reading a
 * {@code power:} key that must not exist and force two abstract methods
 * ({@code onHitBarrier}, {@code onCountered}) that can never be reached.
 *
 * <h2>Cleanup</h2>
 * {@link #endTransformation} is the single exit path and is reached from exactly three
 * places - the aura runnable's expiry branch, {@code onQuit}/{@code onDeath}, and
 * {@code onPluginDisable} - so there is no way to leak a BossBar, an attribute modifier or
 * a scheduled task. The potion effects are removed explicitly because they are applied
 * directly rather than through the pooled-modifier system.
 */
public class Apotheosis extends Spell implements Listener {

    private static final String APOTHEOSIS_GROUP_ID = "configure_apotheosis";

    /** Violet-white - the only violet among the five light spells. */
    private static final Color ASCENDANT_COLOR = Color.fromRGB(215, 180, 255);
    private static final Particle.DustOptions ASCENDANT_DUST = new Particle.DustOptions(ASCENDANT_COLOR, 1.0F);
    private static final Particle.DustOptions ASCENDANT_FADE = new Particle.DustOptions(ASCENDANT_COLOR, 0.4F);

    /** Wind-up repaint cadence: one rising ring per tick, plus a totem every {@code #TOTEM_EVERY}. */
    private static final int TOTEM_EVERY = 4;
    /** Aura repaint period. 2, so the column draws every other tick and the bar steps visibly. */
    private static final long AURA_PERIOD = 2L;
    /** Aura totem cadence. */
    private static final int AURA_TOTEM_EVERY = 8;
    /** Fallback repaint period if {@code circle_ticks} is not a number. */
    private static final int DEFAULT_CIRCLE_TICKS = 10;

    private static final Map<UUID, ChannelData> channels = new ConcurrentHashMap<>();
    private static final Map<UUID, Ascendant> ascendants = new ConcurrentHashMap<>();
    private final Map<UUID, List<AppliedModifier>> activeModifiers = new ConcurrentHashMap<>();

    /** One caster's in-progress channel. The runnable is cancelled on every abort path. */
    private record ChannelData(Player caster, BukkitRunnable runnable) {
    }

    /** One live transformation: its BossBar and the task that draws it. */
    private record Ascendant(BossBar bar, BukkitTask task) {
    }

    private ApotheosisConfig config = ApotheosisConfig.fromConfig(null);

    public Apotheosis(String type) {
        super(type);
    }

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/apotheosis.yml");
        Alkatraz.getInstance().saveConfig("spells/apotheosis_options.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/apotheosis.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        this.config = ApotheosisConfig.fromConfig(spellConfig.getValues(false));
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player caster, ItemStack wand) {
        if (caster.isDead()) return;
        MagicProfile profile = ProfileManager.getProfile(caster, MagicProfile.class);
        if (profile == null) return;
        UUID uuid = caster.getUniqueId();
        if (channels.containsKey(uuid)) return;
        if (ascendants.containsKey(uuid)) {
            Utils.sendActionBar(caster, lang().get("spells.apotheosis.already_active"));
            cancelCast(caster);
            return;
        }

        int channelTicks = config.channelTicks(profile.getSpellMastery(this), getMaxMastery());
        int toneEvery = circleTicks();

        BukkitRunnable runnable = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= channelTicks) {
                    cancel();
                    channels.remove(uuid);
                    completeTransformation(caster, wand);
                    return;
                }
                if (caster.isDead() || !caster.isOnline()) {
                    cancel();
                    abortChannel(uuid, false);
                    return;
                }
                animate(caster, ticks, channelTicks, toneEvery);
                ticks++;
            }
        };

        channels.put(uuid, new ChannelData(caster, runnable));
        runnable.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // A mob cannot hold a transformation: the whole point of apotheosis is that the CASTER
        // is changed, and a channel requires a player profile to read mastery from.
    }

    /** The rising column, one tick at a time. */
    private void animate(Player caster, int ticks, int channelTicks, int toneEvery) {
        double progress = ticks / (double) Math.max(1, channelTicks);
        Location base = caster.getLocation();
        World world = base.getWorld();
        if (world == null) return;

        for (int i = 0; i < 3; i++) {
            double a = ticks * 0.25 + i * (2 * Math.PI / 3);
            Location point = base.clone().add(Math.cos(a) * 0.6, progress * 3.0, Math.sin(a) * 0.6);
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 1, ASCENDANT_DUST);
        }
        for (int h = 0; h <= 12; h++) {
            Location point = base.clone().add(0, h * 0.25, 0);
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 1, ASCENDANT_FADE);
        }
        if (ticks % TOTEM_EVERY == 0) {
            Location eye = caster.getEyeLocation();
            ParticleCaster.spawn(caster, world, Utils.TOTEM, eye, 1, 0.1, 0.1, 0.1, 0.0);
        }
        if (ticks % toneEvery == 0) {
            world.playSound(caster.getEyeLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.4f,
                    0.6f + 0.8f * (float) progress);
        }
    }

    /** The channel landed: apply the pooled modifiers, the vanilla effects, and the aura. */
    private void completeTransformation(Player caster, ItemStack wand) {
        if (caster.isDead() || !caster.isOnline()) return;

        // One resolution of the `duration` stat: the pooled-modifier path works in whole
        // seconds, the direct potion effects and the aura work in ticks.
        int buffTicks = Math.max(1, (int) getModifiedStat(caster, "duration", config.buffTicks()));
        int seconds = Math.max(1, Math.round(buffTicks / 20.0f));

        // The pooled modifiers. Both slots default to a real value rather than `none`, so
        // applyConfiguredModifiers cannot silently no-op. Its expiry message is empty on
        // purpose: that path schedules its own expiry task at the same instant as the aura, and
        // endTransformation already owns the one 'expired' message the player should see.
        String casterMsg = lang().get("spells.apotheosis.active", "duration", String.valueOf(seconds));
        boolean applied = PooledModifierSpellSupport.applyConfiguredModifiers(
                caster, caster, this, APOTHEOSIS_GROUP_ID, seconds, activeModifiers,
                ASCENDANT_COLOR, casterMsg, "", "");
        if (!applied) {
            // applyConfiguredModifiers already sent pooled.no_effects to the caster.
            cancelCast(caster);
            return;
        }

        // The vanilla half of the empowerment. These are NOT pooled, so they are removed
        // explicitly by endTransformation.
        caster.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, buffTicks, 1, false, true));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, buffTicks, 0, false, true));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, buffTicks, 0, false, true));

        startAura(caster, buffTicks, seconds);
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 1.1f);
    }

    /** The BossBar plus the column of light that follows the caster for the hold's length. */
    private void startAura(Player caster, int buffTicks, int seconds) {
        BossBar bar = Bukkit.createBossBar(
                lang().get("spells.apotheosis.boss_bar", "duration", String.valueOf(seconds)),
                BarColor.PURPLE, BarStyle.SEGMENTED_10);
        bar.setProgress(1.0);
        bar.addPlayer(caster);

        BukkitRunnable runnable = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= buffTicks || caster.isDead() || !caster.isOnline()) {
                    endTransformation(caster.getUniqueId(), caster);
                    cancel();
                    return;
                }
                tickAura(caster, ticks, buffTicks);
                bar.setProgress(Math.max(0.0, 1.0 - (ticks / (double) buffTicks)));
                ticks++;
            }
        };
        BukkitTask task = runnable.runTaskTimer(Alkatraz.getInstance(), 0L, AURA_PERIOD);
        ascendants.put(caster.getUniqueId(), new Ascendant(bar, task));
    }

    /** The aura's own repaint: a climbing column plus a ring at {@code aura_radius}. */
    private void tickAura(Player caster, int ticks, int buffTicks) {
        double progress = ticks / (double) Math.max(1, buffTicks);
        Location base = caster.getLocation();
        World world = base.getWorld();
        if (world == null) return;

        for (int h = 0; h <= 16; h++) {
            Location point = base.clone().add(0, h * 0.2, 0);
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 1, ASCENDANT_DUST);
        }
        int ringPoints = Math.max(1, (int) config.auraRadius());
        for (int i = 0; i < ringPoints; i++) {
            double a = ticks * 0.15 + i * 0.9;
            Location point = base.clone().add(
                    Math.cos(a) * config.auraRadius(), 0.2 + progress * 1.5, Math.sin(a) * config.auraRadius());
            ParticleCaster.spawn(caster, world, Utils.DUST, point, 1, ASCENDANT_FADE);
        }
        if (ticks % AURA_TOTEM_EVERY == 0) {
            Location eye = caster.getEyeLocation();
            ParticleCaster.spawn(caster, world, Utils.TOTEM, eye, 1, 0.15, 0.15, 0.15, 0.0);
        }
    }

    /**
     * The single exit path: BossBar, task, pooled modifiers and the direct potion effects all
     * come down here. Called from exactly three places, so nothing can leak.
     *
     * @param uuid   the transformed caster's id
     * @param caster that caster
     */
    private void endTransformation(UUID uuid, Player caster) {
        Ascendant ascendant = ascendants.remove(uuid);
        if (ascendant != null) {
            ascendant.bar().removeAll();
            ascendant.task().cancel();
        }
        PooledModifierSpellSupport.clearActiveModifiers(caster, Apotheosis.this, activeModifiers);
        if (!caster.isValid()) return;
        caster.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
        caster.removePotionEffect(PotionEffectType.REGENERATION);
        caster.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
        if (caster.isOnline() && !caster.isDead()) {
            Utils.sendActionBar(caster, ColorFormat.format(lang().get("spells.apotheosis.expired")));
        }
    }

    /**
     * Cancels an in-progress channel. The map entry is removed first, so a second interrupt on
     * the same tick is a no-op rather than a second cancel.
     *
     * @param uuid     the channeling caster's id
     * @param announce whether to tell them why
     */
    private void abortChannel(UUID uuid, boolean announce) {
        ChannelData data = channels.remove(uuid);
        if (data == null) return;
        data.runnable().cancel();
        if (!announce) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline() && !player.isDead()) {
            Utils.sendActionBar(player, lang().get("spells.apotheosis.interrupted"));
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 0.5f);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        if (!channels.containsKey(uuid)) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        // A world change is a break even when the block distance reads as zero.
        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())
                || from.distance(to) > config.moveCancelThreshold()) {
            abortChannel(uuid, true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player victim && channels.containsKey(victim.getUniqueId())) {
            abortChannel(victim.getUniqueId(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        abortChannel(uuid, false);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            endTransformation(uuid, player);
            return;
        }
        // Already off the online list: drop the bar and the task without touching a stale player.
        Ascendant ascendant = ascendants.remove(uuid);
        if (ascendant != null) {
            ascendant.bar().removeAll();
            ascendant.task().cancel();
        }
        activeModifiers.remove(uuid);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        UUID uuid = e.getEntity().getUniqueId();
        abortChannel(uuid, false);
        endTransformation(uuid, e.getEntity());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Alkatraz.getInstance()) return;
        for (ChannelData channel : channels.values()) {
            channel.runnable().cancel();
        }
        channels.clear();
        for (Ascendant ascendant : ascendants.values()) {
            ascendant.bar().removeAll();
            ascendant.task().cancel();
        }
        ascendants.clear();
        activeModifiers.clear();
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 3.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.apotheosis.book_name"))
                .addCustomLoreLine(lang().get("spells.apotheosis.lore1"))
                .addCustomLoreLine(lang().get("spells.apotheosis.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 8))
                .build();
    }

    private static int circleTicks() {
        Object ticks = Configs.CIRCLE_TICKS.get();
        return ticks instanceof Number number ? Math.max(1, number.intValue()) : DEFAULT_CIRCLE_TICKS;
    }
}
