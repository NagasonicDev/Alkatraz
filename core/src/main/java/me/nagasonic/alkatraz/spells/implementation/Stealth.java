package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.effect.ActiveEffect;
import me.nagasonic.alkatraz.spells.effect.EffectApplicator;
import me.nagasonic.alkatraz.spells.effect.EffectKind;
import me.nagasonic.alkatraz.spells.effect.EffectRegistry;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.spells.vfx.SpellVfx;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.StatUtils;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Stealth extends Spell implements Listener {

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    /**
     * The single NONE palette. The old trail was hardcoded {@code Color.fromRGB(36, 36, 36)},
     * a near-black that read as a rendering artefact rather than as this spell. The ash trail
     * now takes the palette's darkest shade, which is grey rather than black.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.NONE);

    /** Radius of the aura rings shed at the caster's feet, in blocks. */
    private static final double AURA_RADIUS = 0.9;
    /** Particles per aura ring. */
    private static final int AURA_POINTS = 12;

    public Stealth(String type){
        super(type);
    }
    private Map<Integer, Double> costs = new HashMap<>();
    private final Map<UUID, Integer> stealthTasks = new HashMap<>();
    private final Map<UUID, Long> lastTrailTick = new HashMap<>();

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/stealth.yml");
        Alkatraz.getInstance().saveConfig("spells/stealth_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/stealth.yml").get();
        for (int i = 2; i <= 9; i++){
            costs.put(i, spellConfig.getDouble("cost_over_time.circle_" + i));
        }
        loadCommonConfig(spellConfig);
        loadOptions();
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        if (!p.isDead()) {
            MagicProfile data = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
            if (data == null) return;
            if (data.isStealth()){
                data.setStealth(false);
                StatUtils.addMana(p, getCost()); //Give cost to reverse canceling
                for (Player player : Bukkit.getOnlinePlayers()){
                    Alkatraz.getNms().setInvisible(p, false);
                    Alkatraz.getNms().fakeArmor(p, player, p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots());
                    Alkatraz.getNms().setTransparent(p, player, false);
                }
                unstampConcealment(p);
            }else{
                data.setStealth(true);
                boolean armorInvis = (boolean) getOption("armor_invis").getSelectedValue(p).getValue();
                for (Player player : Bukkit.getOnlinePlayers()){
                    if (player != p){
                        MagicProfile td = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
                        if (td == null) continue;
                        if (td.getCircleLevel() < data.getCircleLevel()){
                            Alkatraz.getNms().setInvisible(p, true);
                            if (armorInvis){
                                Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
                            }else Alkatraz.getNms().fakeArmor(p, player, p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots());
                        }else{
                            Alkatraz.getNms().setInvisible(p, true);
                            if (armorInvis){
                                Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
                            }else Alkatraz.getNms().fakeArmor(p, player, p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots());
                            Alkatraz.getNms().setTransparent(p, player, true);
                        }
                    }else {
                        Alkatraz.getNms().setInvisible(p, true);
                        if (armorInvis){
                            Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
                        }else Alkatraz.getNms().fakeArmor(p, player, p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots());
                        Alkatraz.getNms().setTransparent(p, player, true);
                    }
                }
                Integer previousTask = stealthTasks.remove(p.getUniqueId());
                if (previousTask != null) {
                    Bukkit.getServer().getScheduler().cancelTask(previousTask);
                }
                stealthTasks.put(p.getUniqueId(), Bukkit.getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
                    if (!data.isStealth()){
                        stop(p);
                        return;
                    }
                    if (!p.isDead()){
                        double cost = costs.get(data.getCircleLevel());
                        if (data.getMana() >= cost){
                            StatUtils.subMana(p, cost);
                            spawnTrail(p, data);
                        }else{
                            data.setStealth(false);
                            for (Player player : Bukkit.getOnlinePlayers()){
                                Alkatraz.getNms().setInvisible(p, false);
                                Alkatraz.getNms().fakeArmor(p, player, p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots());
                                Alkatraz.getNms().setTransparent(p, player, false);
                            }
                            stop(p);
                            unstampConcealment(p);
                        }
                    }else {
                        data.setStealth(false);
                        for (Player player : Bukkit.getOnlinePlayers()){
                            Alkatraz.getNms().setInvisible(p, false);
                            Alkatraz.getNms().fakeArmor(p, player, p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots());
                            Alkatraz.getNms().setTransparent(p, player, false);
                        }
                        stop(p);
                        unstampConcealment(p);
                    }
                }, 0L, 20L));
                EffectApplicator.applyConcealment(p, this, EffectApplicator.stealthHandle());
            }
        }
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        if (!caster.isDead()) {
            NBT.modifyPersistentData(caster, nbt -> {
                nbt.setBoolean("stealth", true);
            });
            for (Player player : Bukkit.getOnlinePlayers()) {
                Alkatraz.getNms().setInvisible(caster, true);
                if (caster instanceof HumanEntity e) {
                    Alkatraz.getNms().fakeArmor(e, player, null, null, null, null);
                }
            }
            Bukkit.getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
                if (!caster.isDead()) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        Alkatraz.getNms().setInvisible(caster, false);
                        if (caster instanceof HumanEntity e) {
                            Alkatraz.getNms().fakeArmor(e, player, e.getInventory().getHelmet(), e.getInventory().getChestplate(), e.getInventory().getLeggings(), e.getInventory().getBoots());
                        }
                    }
                    Bukkit.getScheduler().scheduleSyncDelayedTask(Alkatraz.getInstance(), () -> {
                        if (!caster.isDead()) {
                            NBT.modifyPersistentData(caster, nbt -> {
                                nbt.setBoolean("stealth", false);
                            });
                        }
                    }, 200L);
                }
            }, 600L);
        }
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.stealth.book_name"))
                .addCustomLoreLine(lang().get("spells.stealth.lore1"))
                .addCustomLoreLine(lang().get("spells.stealth.lore2"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 2))
                .build();
    }

    private void stop(Player caster){
        Integer task = stealthTasks.remove(caster.getUniqueId());
        if (task != null) {
            Bukkit.getServer().getScheduler().cancelTask(task);
        }
        lastTrailTick.remove(caster.getUniqueId());
    }

    /**
     * Test seam for {@link #dispel(Player)}.
     *
     * <p>{@link #stop(Player)} cancels the upkeep task only when one is actually
     * registered for the player, and {@code stealthTasks} is private with no
     * accessor, so a test cannot otherwise prove that dispel reaches the cancel.
     * Package-private, and named for what it is, so it cannot be mistaken for
     * production state manipulation.
     *
     * @param player the player to seed
     * @param taskId the task id {@code dispel} must go on to cancel
     */
    void seedStealthTaskForTest(UUID player, int taskId) {
        stealthTasks.put(player, taskId);
    }

    /**
     * Reverses an active Stealth on a victim who did not cast it. Called by
     * {@code Dispel} when the victim is not the caster.
     *
     * <p>Three separate things have to happen and none of them implies the
     * others:
     *
     * <ol>
     *   <li>{@code setStealth(false)} — stops the repeating task's own
     *       self-heal branch ({@code Stealth.java:121-124}), which does
     *       {@code stop(p); return;} and performs no NMS work at all.</li>
     *   <li>{@link #stop(Player)} — cancels the upkeep task and clears the trail
     *       tick, so the player stops paying the per-second mana cost.</li>
     *   <li>The three NMS calls — because the reset block at
     *       {@code Stealth.java:131-138} lives in the <em>mana-exhaustion</em>
     *       branch, not in the self-heal branch. Clearing the flag alone leaves
     *       the victim permanently invisible.</li>
     * </ol>
     *
     * <p><b>Every online viewer, not just the victim.</b> {@code onQuit} and
     * {@code onJoin} both pass {@code player, player}, so they only ever fix the
     * victim's own view; reusing either would leave everyone else looking at an
     * invisible ghost.
     *
     * @param victim the player whose stealth is being broken
     */
    public void dispel(Player victim) {
        MagicProfile data = ProfileManager.getProfile(victim.getUniqueId(), MagicProfile.class);
        if (data == null || !data.isStealth()) return;
        data.setStealth(false);
        stop(victim);
        unstampConcealment(victim);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Alkatraz.getNms().setInvisible(victim, false);
            Alkatraz.getNms().fakeArmor(victim, viewer,
                    victim.getInventory().getHelmet(), victim.getInventory().getChestplate(),
                    victim.getInventory().getLeggings(), victim.getInventory().getBoots());
            Alkatraz.getNms().setTransparent(victim, viewer, false);
        }
    }

    /**
     * Drops this spell's concealment stamp from a player whose stealth has just
     * ended by any route.
     *
     * <p>Concealment entries carry {@code Long.MAX_VALUE} and are never pruned,
     * because an illusion has no duration and must survive a restart. That means
     * every path that ends a stealth has to remove the stamp explicitly, or the
     * entry stays visible to the dispel forever and a dispel later reports a
     * cleared illusion that was never there.
     *
     * @param p the player whose concealment stamp should be dropped
     */
    private void unstampConcealment(Player p) {
        for (ActiveEffect effect : EffectRegistry.effectsOn(p.getUniqueId())) {
            if (effect.kind() == EffectKind.CONCEALMENT && EffectApplicator.isStealthHandle(effect.handle())) {
                EffectRegistry.remove(effect);
            }
        }
    }

    private void spawnTrail(Player p, MagicProfile data) {
        World world = p.getWorld();
        for (Player player : world.getPlayers()) {
            if (player == p) continue;
            MagicProfile td = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
            if (td == null) continue;
            if (td.getCircleLevel() <= data.getCircleLevel()) {
                ParticleCaster.spawnForPlayer(player, world, Utils.ASH, p.getLocation(), td.getCircleLevel() - data.getCircleLevel() + 11);
            }
        }
    }

    @EventHandler
    private void onInventoryClick(InventoryClickEvent e){
        Player p = (Player) e.getWhoClicked();
        MagicProfile data = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
        if (data == null) return;
        if (data.isStealth()){
            boolean armorInvis = (boolean) getOption("armor_invis").getSelectedValue(p).getValue();
            for (Player player : Bukkit.getOnlinePlayers()){
                if (armorInvis){
                    Alkatraz.getNms().fakeArmor(p, player, null, null, null, null);
                }else Alkatraz.getNms().fakeArmor(p, player, p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots());
            }
        }
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent e){
        Player p = e.getPlayer();
        for (Player caster : Bukkit.getOnlinePlayers()) {
            if (caster != null){
                MagicProfile td = ProfileManager.getProfile(caster.getUniqueId(), MagicProfile.class);
                if (td == null) continue;
                if (td.isStealth()){
                    EffectApplicator.applyConcealment(caster, this, EffectApplicator.stealthHandle());
                    boolean armorInvis = (boolean) getOption("armor_invis").getSelectedValue(caster).getValue();
                    MagicProfile data = ProfileManager.getProfile(p.getUniqueId(), MagicProfile.class);
                    if (data == null) continue;
                    if (p != caster){
                        if (td.getCircleLevel() < data.getCircleLevel()){
                            Alkatraz.getNms().setInvisible(caster, true);
                            if (armorInvis){
                                Alkatraz.getNms().fakeArmor(caster, p, null, null, null, null);
                            }else Alkatraz.getNms().fakeArmor(caster, p, p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots());
                        }else{
                            Alkatraz.getNms().setInvisible(caster, true);
                            if (armorInvis){
                                Alkatraz.getNms().fakeArmor(caster, p, null, null, null, null);
                            }else Alkatraz.getNms().fakeArmor(caster, p, p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots());
                            Alkatraz.getNms().setTransparent(caster, p, true);
                        }
                    }else {
                        Alkatraz.getNms().setInvisible(caster, true);
                        if (armorInvis){
                            Alkatraz.getNms().fakeArmor(caster, caster, null, null, null, null);
                        }else Alkatraz.getNms().fakeArmor(caster, caster, p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(), p.getInventory().getBoots());
                        Alkatraz.getNms().setTransparent(caster, caster, true);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent e){
        Player player = e.getPlayer();
        stop(player);
        MagicProfile data = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
        if (data == null || !data.isStealth()) return;
        data.setStealth(false);
        Alkatraz.getNms().setInvisible(player, false);
        Alkatraz.getNms().fakeArmor(player, player, player.getInventory().getHelmet(), player.getInventory().getChestplate(), player.getInventory().getLeggings(), player.getInventory().getBoots());
        Alkatraz.getNms().setTransparent(player, player, false);
        unstampConcealment(player);
    }
    private static final double TRAIL_RANGE_SQ = 64.0;
    private static final Particle.DustOptions TRAIL_DUST = PALETTE.deepDust();

    @EventHandler
    private void onWalk(PlayerMoveEvent e){
        Player mover = e.getPlayer();
        MagicProfile data = ProfileManager.getProfile(mover.getUniqueId(), MagicProfile.class);
        if (data == null || !data.isStealth()) return;

        Location to = e.getTo();
        Location from = e.getFrom();
        boolean crossed = to.getBlockX() != from.getBlockX()
                || to.getBlockY() != from.getBlockY()
                || to.getBlockZ() != from.getBlockZ();
        long lived = mover.getTicksLived();
        long last = lastTrailTick.getOrDefault(mover.getUniqueId(), Long.MIN_VALUE);
        if (!crossed && (last == Long.MIN_VALUE || lived - last < 3)) return;
        lastTrailTick.put(mover.getUniqueId(), lived);

        Location base = mover.getLocation().add(0, 0.3, 0);
        World world = base.getWorld();
        double bx = base.getX();
        double by = base.getY();
        double bz = base.getZ();
        for (Player other : world.getPlayers()) {
            if (other == mover) continue;
            Location oLoc = other.getLocation();
            double dx = oLoc.getX() - bx;
            double dy = oLoc.getY() - by;
            double dz = oLoc.getZ() - bz;
            if (dx * dx + dy * dy + dz * dz > TRAIL_RANGE_SQ) continue;
            MagicProfile odata = ProfileManager.getProfile(other.getUniqueId(), MagicProfile.class);
            if (odata == null) continue;
            if (odata.getCircleLevel() >= data.getCircleLevel()) {
                ParticleCaster.spawnForPlayer(other, world, Utils.DUST, base, 5, TRAIL_DUST);
            }
        }
    }

    @Override
    public boolean canMobCast(Mob mob){
        return !NBT.getPersistentData(mob, nbt -> nbt.getBoolean("stealth"));
    }
}
