package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.util.SpellMarks;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circle 8 DARK UTILITY: returns the caster to their most recent death site and calls their
 * own dropped stacks back into the inventory. Other players' drops are never touched, and the
 * cast refuses instead of charging when the trail is cold, too far or unloaded.
 */
public class SoulReclamation extends Spell implements Listener {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.DARK);

    /** Death timestamps, because Bukkit exposes the location but not its age. */
    private static final Map<UUID, Long> DEATH_AT = new ConcurrentHashMap<>();
    private volatile boolean listenerRegistered;

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public SoulReclamation(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/soul_reclamation_options.yml");
        Alkatraz.getInstance().save("spells/soul_reclamation.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/soul_reclamation.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
        registerListenerOnce();
    }

    private synchronized void registerListenerOnce() {
        if (listenerRegistered) return;
        Bukkit.getPluginManager().registerEvents(this, Alkatraz.getInstance());
        listenerRegistered = true;
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead() || !p.isOnline()) {
            cancelCast(p);
            return;
        }
        UUID id = p.getUniqueId();
        Location death = p.getLastDeathLocation();
        if (death == null || death.getWorld() == null) {
            refuse(p, "no_death");
            return;
        }
        World world = death.getWorld();
        if (world != p.getWorld()) {
            refuse(p, "wrong_world");
            return;
        }

        double range = getModifiedStat(p, "reclaim_range", option(p, "range", 128));
        double minutes = getModifiedStat(p, "reclaim_minutes", option(p, "minutes", 10));
        double pullRadius = Math.max(1.0, ConfigManager.getConfig("spells/soul_reclamation.yml")
                .get().getDouble("pull_radius", 10.0));
        if (p.getLocation().distance(death) > range) {
            refuse(p, "too_far");
            return;
        }
        Long diedAt = DEATH_AT.get(id);
        if (diedAt == null || System.currentTimeMillis() - diedAt > (long) (minutes * 60_000L)) {
            DEATH_AT.remove(id);
            refuse(p, "too_old");
            return;
        }
        if (!world.isChunkLoaded(death.getBlockX() >> 4, death.getBlockZ() >> 4)) {
            refuse(p, "chunk_unloaded");
            return;
        }

        SpellMarks.mark(p, death, PALETTE, lang().get("spells.soulreclamation.mark"));
        SpellVfx.ring(PALETTE.core(), p, p.getLocation(), 1.6, 24, 0, 1.0);
        p.teleport(death.clone().add(0, 0.1, 0));
        p.setFallDistance(0.0f);
        SpellVfx.column(PALETTE.accent(), p, death, 2.4, 0.6, 3, 8, 0.6);

        int recovered = pullDrops(p, death, world, pullRadius);
        DEATH_AT.remove(id);
        if (recovered > 0) {
            Utils.sendActionBar(p, lang().get("spells.soulreclamation.reclaimed")
                    .replace("{count}", String.valueOf(recovered)));
        } else {
            Utils.sendActionBar(p, lang().get("spells.soulreclamation.empty"));
        }
    }

    private void refuse(Player p, String reason) {
        Utils.sendActionBar(p, lang().get("spells.soulreclamation." + reason));
        cancelCast(p);
    }

    private int pullDrops(Player p, Location death, World world, double radius) {
        int recovered = 0;
        for (Entity entity : world.getNearbyEntities(death, radius, radius, radius)) {
            if (!(entity instanceof Item item) || item.isDead()) continue;
            if (!ownedBy(item, p.getUniqueId())) continue;
            ItemStack stack = item.getItemStack();
            if (stack == null || stack.getType().isAir()) continue;
            Map<Integer, ItemStack> leftover = p.getInventory().addItem(stack.clone());
            if (leftover.isEmpty()) {
                item.remove();
                recovered++;
            } else {
                item.setItemStack(leftover.values().iterator().next());
            }
        }
        return recovered;
    }

    private static boolean ownedBy(Item item, UUID owner) {
        try {
            return owner.equals(item.getOwner());
        } catch (RuntimeException notDroppedByPlayer) {
            return false;
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
    }

    /** Idempotent: drops any mark left for this caster. */
    private void settle(UUID casterId) {
        Player p = Bukkit.getPlayer(casterId);
        if (p != null) SpellMarks.clear(p);
    }

    @Override
    public void cancelTasks() {
        for (UUID id : new ArrayList<>(DEATH_AT.keySet())) {
            DEATH_AT.remove(id);
            settle(id);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        DEATH_AT.put(e.getEntity().getUniqueId(), System.currentTimeMillis());
        settle(e.getEntity().getUniqueId());
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 30) / 30.0;
        SpellVfx.ring(palette.core(), caster, origin.clone().add(0, 0.3 + phase * 1.6, 0),
                scale * (1.4 - phase * 0.6), 24, -frame * 4, 0.0);
        SpellVfx.column(palette.deep(), caster, origin, 3.0, scale * 0.4, 3, 6, 0.7);
    }

    @Override
    public void drawAftermath(LivingEntity caster, Location origin, int tick, int totalTicks,
                              ElementPalette.Palette palette) {
        double progress = totalTicks <= 0 ? 1.0 : (double) tick / totalTicks;
        double radius = 4.0 - progress * 3.6;
        if (tick % 4 == 0) {
            SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.2, 0),
                    Math.max(0.4, radius), 20, -tick * 5, 0.0);
            SpellVfx.column(palette.deep(), caster, origin, 2.6 * (1.0 - progress), 0.4,
                    2, 5, 0.6);
        }
        if (tick % 8 == 0) {
            ParticleCaster.spawn(caster, origin.getWorld(), Utils.PORTAL,
                    origin.clone().add(0, 1.2, 0), 8, 0.7, 0.9, 0.7, 0.02);
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
                .setDisplayName(lang().get("spells.soulreclamation.book_name"))
                .addCustomLoreLine(lang().get("spells.soulreclamation.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 8))
                .build();
    }
}
