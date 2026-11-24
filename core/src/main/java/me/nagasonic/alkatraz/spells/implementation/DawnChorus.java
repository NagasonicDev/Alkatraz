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
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Circle 7 LIGHT SUSTAIN: sings the team back to health, then keeps mending them for a few
 * seconds. The follow-up heal is a task, so it stops early if nobody is left standing.
 */
public class DawnChorus extends Spell {

    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.LIGHT);
    /** Curses lifted per ally at the chorus itself. */
    private static final int CLEANSED = 2;
    /** One hit point per second for six seconds. */
    private static final int REGEN_SECONDS = 6;
    private static final int REGEN_PERIOD = 20;

    private static final Map<UUID, BukkitTask> REGEN = new HashMap<>();
    private static final Map<UUID, List<UUID>> CHORUS = new HashMap<>();

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public DawnChorus(String type) {
        super(type);
    }

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().saveConfig("spells/dawn_chorus_options.yml");
        Alkatraz.getInstance().save("spells/dawn_chorus.yml");
        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/dawn_chorus.yml").get();
        loadCommonConfig(spellConfig);
        loadOptions();
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (p.isDead()) {
            cancelCast(p);
            return;
        }
        double radius = getModifiedStat(p, "chorus_radius", option(p, "radius", 18));
        int cap = (int) Math.round(getModifiedStat(p, "chorus_allies", 8));
        double heal = Math.max(0.0, getModifiedStat(p, "healing", option(p, "heal", 8)));
        Location origin = p.getLocation();

        List<LivingEntity> healed = new ArrayList<>();
        healed.add(p);
        healed.addAll(SpellTargeting.allies(p, origin, radius, Math.max(0, cap - 1)));
        for (LivingEntity target : healed) {
            mend(target, heal);
            HarmfulEffects.clear(target, CLEANSED);
            if (target instanceof Player ally) {
                Utils.sendActionBar(ally, lang().get("spells.dawnchorus.aided_by")
                        .replace("{caster}", p.getName()));
            }
        }

        SpellVfx.column(PALETTE.core(), p, origin, 3.0, 1.4, 4, 10, 0.5);
        SpellVfx.ring(PALETTE.accent(), p, origin, radius * 0.6, 40, 0, 0.2);

        UUID casterId = p.getUniqueId();
        cancelRegen(casterId);
        List<UUID> chorus = CHORUS.computeIfAbsent(casterId, key -> new ArrayList<>());
        for (LivingEntity target : healed) {
            chorus.add(target.getUniqueId());
        }
        World world = p.getWorld();
        REGEN.put(casterId, new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks += REGEN_PERIOD;
                if (ticks > REGEN_SECONDS * 20) {
                    cancel();
                    REGEN.remove(casterId);
                    CHORUS.remove(casterId);
                    return;
                }
                for (UUID id : chorus) {
                    var entity = Alkatraz.getInstance().getServer().getEntity(id);
                    if (!(entity instanceof LivingEntity living) || living.isDead()) continue;
                    if (living.getWorld() != world) continue;
                    mend(living, 1.0);
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), REGEN_PERIOD, REGEN_PERIOD));
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        mend(caster, 8);
    }

    private void mend(LivingEntity target, double heal) {
        target.setHealth(Math.min(target.getMaxHealth(), target.getHealth() + heal));
    }

    private void cancelRegen(UUID casterId) {
        BukkitTask task = REGEN.remove(casterId);
        if (task != null) {
            task.cancel();
        }
        CHORUS.remove(casterId);
    }

    @Override
    public void cancelTasks() {
        for (UUID id : new ArrayList<>(REGEN.keySet())) {
            cancelRegen(id);
        }
        CHORUS.clear();
    }

    @Override
    public void drawCastMotif(LivingEntity caster, Location origin, int frame, double scale,
                              ElementPalette.Palette palette) {
        double phase = (frame % 24) / 24.0;
        SpellVfx.ring(palette.accent(), caster, origin.clone().add(0, 0.4 + phase * 2.4, 0),
                scale * (1.4 - phase * 0.6), 24, frame * 3, 0.2);
        SpellVfx.column(palette.core(), caster, origin, 3.0, scale * 0.6, 4, 8, 0.6);
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
                .setDisplayName(lang().get("spells.dawnchorus.book_name"))
                .addCustomLoreLine(lang().get("spells.dawnchorus.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 7))
                .build();
    }
}
