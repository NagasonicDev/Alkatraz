package me.nagasonic.alkatraz.items.magic.barrier;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.items.magic.condition.implementation.CooldownCondition;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.components.BarrierWallComponent;
import me.nagasonic.alkatraz.spells.components.SpellComponentHandler;
import me.nagasonic.alkatraz.spells.types.BarrierType;
import me.nagasonic.alkatraz.spells.types.DamageableBarrier;
import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
import me.nagasonic.alkatraz.util.StatUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BarrierSession extends SpellProperties implements DamageableBarrier {

    private static final int REGEN_INTERVAL_TICKS = 20;

    private final Player caster;
    private final MagicItemInstance sourceItem;
    private final EquipmentSlot slot;
    private final BarrierConfig config;
    private final String itemKey;
    private final NamespacedKey engravingKey;
    private final float initialYaw;
    private final String titleKey;

    private final double initialHitpoints;
    private double hitpoints;
    private boolean broken;
    private boolean alreadyHandled;
    private boolean disposed;
    private BossBar healthBar;
    private Location anchor;
    private int ticksAlive;
    private final Set<UUID> activeComponents = new HashSet<>();

    public BarrierSession(Player caster, MagicItemInstance sourceItem, EquipmentSlot slot,
                          BarrierConfig config, String itemKey, NamespacedKey engravingKey, double startHp) {
        super(caster, caster.getLocation().clone().add(0, config.verticalOffset(), 0));
        this.caster = caster;
        this.sourceItem = sourceItem;
        this.slot = slot;
        this.config = config;
        this.itemKey = itemKey;
        this.engravingKey = engravingKey;
        this.initialYaw = caster.getLocation().getYaw();
        this.anchor = caster.getLocation().clone().add(0, config.verticalOffset(), 0);
        this.initialHitpoints = computeMaxHitpoints();
        this.hitpoints = Math.max(0.0, Math.min(initialHitpoints, startHp));
        this.titleKey = config.healthBar().titleLangKey() != null
                ? config.healthBar().titleLangKey()
                : "spells.barrier.health_bar";
        this.healthBar = Bukkit.createBossBar(title(), config.healthBar().color(), config.healthBar().style());
        this.healthBar.setProgress(1.0);
        if (config.healthBar().enabled() && config.healthBar().visibleTo().contains("CASTER")) {
            this.healthBar.addPlayer(caster);
        }
    }

    public void start() {
        recomputeAnchor();
        playFx(config.sfx().onSummon(), anchor);
    }

    public void tick() {
        if (disposed || broken || alreadyHandled) return;
        ticksAlive++;
        if (config.maxDurationTicks() > 0 && ticksAlive > config.maxDurationTicks()) {
            dispose(false);
            return;
        }
        if (config.holdMode() == BarrierConfig.HoldMode.SNEAKING && !caster.isSneaking()) {
            dispose(false);
            return;
        }
        if (config.equipCheckIntervalTicks() > 0 && ticksAlive % config.equipCheckIntervalTicks() == 0) {
            if (!stillEquipped()) {
                dispose(false);
                BarrierManager.resetPersistedIfResetOn(caster, itemKey, BarrierConfig.ResetOn.UNEQUIP, config);
                return;
            }
        }
        recomputeAnchor();
        pruneComponents();
        if (config.renderIntervalTicks() > 0 && ticksAlive % config.renderIntervalTicks() == 0) {
            renderBarrier();
        }
        if (ticksAlive % REGEN_INTERVAL_TICKS == 0) {
            regenStep();
        }
    }

    public void refresh() {
        ticksAlive = 0;
        recomputeAnchor();
    }

    public void dispose(boolean shatter) {
        if (disposed) return;
        disposed = true;
        for (UUID id : new ArrayList<>(activeComponents)) {
            SpellComponentHandler.remove(id);
        }
        activeComponents.clear();
        if (!shatter && healthBar != null) {
            healthBar.removeAll();
            healthBar = null;
        }
        if (!shatter) {
            playFx(config.sfx().onEnd(), anchor);
        }
        BarrierManager.onDisposed(this, shatter);
    }

    @Override
    public void damage(double amount) {
        if (disposed || broken || alreadyHandled) return;
        hitpoints = Math.max(0.0, hitpoints - amount);
        updateBar();
        if (hitpoints <= 0.0) {
            handleShatter();
        }
    }

    @Override
    public void onBreak(Location center) {
        if (disposed || broken || alreadyHandled) return;
        handleShatter();
    }

    @Override
    public double hitpoints() {
        return hitpoints;
    }

    @Override
    public double initialHitpoints() {
        return initialHitpoints;
    }

    @Override
    public boolean isBroken() {
        return broken;
    }

    @Override
    public BarrierType type() {
        return config.barrierType();
    }

    @Override
    public Set<SpellProperties> collided() {
        return getCollided();
    }

    @Override
    public double barrierRadius() {
        return config.radius();
    }

    @Override
    public void setCastLocation(Location location) {
        this.castLocation = location;
        if (location != null) {
            this.anchor = location.clone();
        }
    }

    public boolean isDisposed() {
        return disposed;
    }

    public Player getCasterPlayer() {
        return caster;
    }

    public UUID actorId() {
        return caster.getUniqueId();
    }

    public String getItemKey() {
        return itemKey;
    }

    public BarrierConfig getConfig() {
        return config;
    }

    private void handleShatter() {
        if (alreadyHandled) return;
        alreadyHandled = true;
        broken = true;
        hitpoints = 0.0;
        updateBar();
        scheduleBarClear();
        playFx(config.sfx().onShatter(), anchor);
        BarrierManager.onShattered(this);
    }

    private void scheduleBarClear() {
        BossBar bar = healthBar;
        if (bar == null) return;
        long delay = Math.max(1L, config.healthBar().clearDelayTicks());
        new BukkitRunnable() {
            @Override
            public void run() {
                if (bar != null) {
                    bar.removeAll();
                    healthBar = null;
                }
            }
        }.runTaskLater(Alkatraz.getInstance(), delay);
    }

    private void regenStep() {
        MagicProfile profile = ProfileManager.getProfile(caster.getUniqueId(), MagicProfile.class);
        if (profile == null) return;
        double mana = profile.getMana();
        BarrierRegen.RegenResult result = BarrierRegen.step(
                config.regen().enabled(), true, hitpoints, initialHitpoints,
                config.regen().hpPerSecond(), config.regen().manaCostPerHp(), mana,
                config.regen().partialOnLowMana());
        if (result.hpGained() > 0) {
            hitpoints = Math.min(initialHitpoints, hitpoints + result.hpGained());
            StatUtils.subMana(caster, result.manaSpent());
            updateBar();
        }
    }

    private void renderBarrier() {
        if (anchor == null || anchor.getWorld() == null) return;
        BarrierConfig.ParticleConfig particleConfig = config.particles();
        List<Vector> vectors;
        if (config.shape() == BarrierConfig.Shape.CAP) {
            BarrierConfig.CapConfig cap = config.cap();
            double pitchMin = cap.pitchMinDegrees();
            double pitchMax = cap.pitchMaxDegrees();
            if (config.follow().pitch()) {
                float rawPitch = caster.getLocation().getPitch();
                pitchMin += rawPitch;
                pitchMax += rawPitch;
            }
            vectors = BarrierGeometry.capVectors(config.radius(), particleConfig.countPerRender(),
                    cap.yawArcDegrees(), pitchMin, pitchMax, facingYaw());
        } else {
            vectors = BarrierGeometry.sphereVectors(config.radius(), particleConfig.countPerRender());
        }
        for (Vector vector : vectors) {
            Location loc = anchor.clone().add(vector);
            if (loc.getWorld() == null) continue;
            int lifeTicks = Math.max(1, particleConfig.componentLifeTicks());
            BarrierWallComponent component =
                    new BarrierWallComponent(this, caster, loc, particleConfig.collisionRadius(), lifeTicks);
            SpellComponentHandler.register(component);
            activeComponents.add(component.getComponentID());
            spawnParticleAt(loc);
        }
    }

    private float facingYaw() {
        return config.follow().facing() ? caster.getLocation().getYaw() : initialYaw;
    }

    private void pruneComponents() {
        activeComponents.removeIf(id -> SpellComponentHandler.getActiveComponent(id) == null);
    }

    private void recomputeAnchor() {
        if (!config.follow().player()) return;
        Location raw = caster.getLocation();
        if (raw == null || raw.getWorld() == null) {
            dispose(false);
            return;
        }
        Location base = raw.clone().add(0, config.verticalOffset(), 0);
        this.anchor = base;
        this.castLocation = base;
    }

    private boolean stillEquipped() {
        if (slot == null) return true;
        org.bukkit.inventory.EquipmentSlot vanilla = slot.vanillaSlot();
        if (vanilla == null) return true;
        ItemStack current = caster.getInventory().getItem(vanilla);
        if (current == null || current.getType().isAir()) return false;
        Optional<MagicItemInstance> read = MagicItemStack.readInstance(current);
        if (read.isEmpty()) return false;
        MagicItemInstance instance = read.get();
        if (engravingKey != null) {
            return instance.engravings().stream().anyMatch(e -> e.engravingKey().equals(engravingKey));
        }
        return CooldownCondition.stableItemKey(instance).equals(itemKey);
    }

    private void updateBar() {
        if (healthBar == null || !config.healthBar().enabled()) return;
        healthBar.setTitle(title());
        healthBar.setProgress(hitpoints <= 0.0 ? 0.0 : Math.min(1.0, hitpoints / initialHitpoints));
    }

    private String title() {
        return Alkatraz.getLangManager().get(titleKey,
                "health", String.valueOf((int) Math.ceil(hitpoints)),
                "initHealth", String.valueOf((int) Math.ceil(initialHitpoints)));
    }

    private double computeMaxHitpoints() {
        double max = config.maxHitpoints();
        BarrierConfig.AffinityScaling affinity = config.affinityScaling();
        if (affinity.enabled()) {
            MagicProfile profile = ProfileManager.getProfile(caster.getUniqueId(), MagicProfile.class);
            double value = profile != null ? profile.getAffinity(affinity.element()) : 0;
            max += Math.floor(value / 10) * affinity.bonusPer10Affinity();
        }
        return max;
    }

    private void playFx(BarrierConfig.SfxConfig.Fx fx, Location at) {
        if (fx == null || fx.none()) return;
        if (fx.particle() != null) {
            Particle particle = parseParticle(fx.particle());
            if (particle != null && at != null && at.getWorld() != null) {
                if (particle == Utils.DUST) {
                    at.getWorld().spawnParticle(particle, at, Math.max(0, fx.count()),
                            new Particle.DustOptions(colorOf(fx.color(), Color.WHITE),
                                    (float) Math.max(0.05, fx.size())));
                } else {
                    at.getWorld().spawnParticle(particle, at, Math.max(0, fx.count()), 0, 0, 0, 0);
                }
            }
        }
        if (fx.sound() != null) {
            try {
                caster.playSound(caster.getLocation(),
                        Sound.valueOf(fx.sound().trim().toUpperCase()), 1.0f, 1.0f);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void spawnParticleAt(Location loc) {
        if (loc.getWorld() == null) return;
        Particle type = config.particles().type();
        if (type == null) return;
        if (type == Utils.DUST) {
            loc.getWorld().spawnParticle(type, loc, 0,
                    new Particle.DustOptions(colorOf(config.particles().color(), Color.WHITE),
                            (float) Math.max(0.05, config.particles().size())));
        } else {
            loc.getWorld().spawnParticle(type, loc, 0, 0, 0, 0);
        }
    }

    private static Particle parseParticle(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return Particle.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Color colorOf(String hex, Color fallback) {
        if (hex == null) return fallback;
        String clean = hex.replace("#", "").trim();
        if (clean.length() != 6) return fallback;
        try {
            return Color.fromRGB(Integer.parseInt(clean, 16));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}