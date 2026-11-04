package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.changeme.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.effect.ActiveEffect;
import me.nagasonic.alkatraz.spells.effect.EffectApplicator;
import me.nagasonic.alkatraz.spells.effect.EffectKind;
import me.nagasonic.alkatraz.spells.effect.EffectRegistry;
import me.nagasonic.alkatraz.lang.LangManager;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
import me.nagasonic.alkatraz.spells.vfx.CastCircle;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static me.nagasonic.alkatraz.util.ColorFormat.format;

public class Disguise extends Spell implements Listener {

    /**
     * Disguise is element NONE, so its cast circle draws in neutral grey rather than
     * in a colour that would imply it belongs to some other element's family.
     */
    private static final ElementPalette.Palette PALETTE = ElementPalette.of(Element.NONE);

    private static LangManager lang() {
        return Alkatraz.getLangManager();
    }

    public Disguise(String type) {
        super(type);
    }

    /**
     * All per-cast state lives here, keyed by the caster's UUID.
     * ConcurrentHashMap so that async tasks (e.g. skin fetching) can read safely.
     */
    private final Map<UUID, DisguiseProperties> activeCasts = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Spell lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void loadConfiguration() {
        Alkatraz.getInstance().save("spells/disguise.yml");
        Alkatraz.getInstance().saveConfig("spells/disguise_options.yml");

        YamlConfiguration spellConfig = ConfigManager.getConfig("spells/disguise.yml").get();

        loadCommonConfig(spellConfig);
        loadOptions();
        Alkatraz.getInstance().getServer().getPluginManager().registerEvents(this, Alkatraz.getInstance());
    }

    @Override
    public void castAction(Player p, ItemStack wand) {
        // Build fresh per-cast state and store it before opening the GUI.
        DisguiseProperties props = new DisguiseProperties(p, p.getEyeLocation());
        activeCasts.put(p.getUniqueId(), props);


        openGUI(p, props);
    }

    @Override
    public void mobCastAction(Mob caster, ItemStack wand) {
        // Not implemented for mobs.
    }

    @Override
    public int circleAction(LivingEntity caster, SpellPrepareEvent e) {
        return CastCircle.start(this, caster, e, 2.0, 0);
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName(lang().get("spells.disguise.book_name"))
                .addCustomLoreLine(lang().get("spells.disguise.lore1"))
                .addCustomLoreLine("")
                .addRequirement(new NumberStatRequirement<>("circleLevel", 3))
                .build();
    }

    // -------------------------------------------------------------------------
    // GUI helpers  (now accept / work through DisguiseProperties)
    // -------------------------------------------------------------------------

    /**
     * Builds the paged player list for {@code player} and stores it in
     * their {@link DisguiseProperties}, then opens page 1.
     */
    public void openGUI(Player player, DisguiseProperties props) {
        double targetRange = (Double) getOption("target_scope")
                .getSelectedValue(player).getValue();

        int slot      = 0;
        int pageIndex = 1;
        List<Player> currentPage = new ArrayList<>();

        for (Player candidate : Bukkit.getServer().getOnlinePlayers()) {
            if (candidate.equals(player)) continue;

            // Range filter (negative range = infinite).
            if (targetRange >= 0
                    && candidate.getWorld().equals(player.getWorld())
                    && candidate.getLocation().distance(player.getLocation()) > targetRange) {
                continue;
            }

            if (slot < 36) {
                currentPage.add(candidate);
                slot++;
            } else {
                props.guiPages.put(pageIndex, new ArrayList<>(currentPage));
                pageIndex++;
                slot = 1;
                currentPage.clear();
                currentPage.add(candidate);
            }
        }
        props.guiPages.put(pageIndex, currentPage);

        int totalPages = pageIndex;
        createGUI(1, player, totalPages, props);
    }

    /**
     * Renders a single page of the GUI into {@code props.gui} and opens it
     * for the player.
     */
    @SuppressWarnings("deprecation")
    public void createGUI(int page, Player player, int totalPages, DisguiseProperties props) {
        Inventory inv = Bukkit.createInventory(null, 54,
                me.nagasonic.alkatraz.texturepack.TexturePackManager.getMenuTitle("disguise", "Disguise Target"));

        ItemStack blank = Utils.getBlank();
        for (int i = 0; i < 54; i++) inv.setItem(i, blank);

        // Next-page arrow.
        if (page < totalPages) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta  meta = next.getItemMeta();
            meta.setDisplayName(format(lang().get("spells.disguise.next_page")));
            meta.setLore(List.of(format(lang().get("spells.disguise.page_number", "page", String.valueOf(page + 1)))));
            next.setItemMeta(meta);
            me.nagasonic.alkatraz.texturepack.TexturePackManager.applyGuiModel(next, "next_page");
            NBT.modify(next, nbt -> {
                nbt.setInteger("page", page + 1);
                nbt.setString("player", player.getUniqueId().toString());
                nbt.setInteger("total_pages", totalPages);
            });
            inv.setItem(53, next);
        }

        // Previous-page arrow.
        if (page > 1) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta  meta = prev.getItemMeta();
            meta.setDisplayName(format(lang().get("spells.disguise.previous_page")));
            meta.setLore(List.of(format(lang().get("spells.disguise.page_number", "page", String.valueOf(page - 1)))));
            prev.setItemMeta(meta);
            me.nagasonic.alkatraz.texturepack.TexturePackManager.applyGuiModel(prev, "prev_page");
            NBT.modify(prev, nbt -> {
                nbt.setInteger("page", page - 1);
                nbt.setString("player", player.getUniqueId().toString());
                nbt.setInteger("total_pages", totalPages);
            });
            inv.setItem(45, prev);
        }

        // Player heads in slots 9–44.
        List<Player> players = props.guiPages.getOrDefault(page, Collections.emptyList());
        int s = 0;
        for (int i = 9; i < 45 && s < players.size(); i++, s++) {
            Player target = players.get(s);
            ItemStack item = ItemUtils.headFromUuid(target.getUniqueId().toString());
            ItemMeta  meta = item.getItemMeta();
            meta.setDisplayName(target.getName());
            meta.setLore(List.of(format(lang().get("spells.disguise.click_disguise", "target", target.getName()))));
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        props.gui = inv;
        player.openInventory(inv);
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    @EventHandler
    private void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player clicker)) return;

        // Find the DisguiseProperties whose open GUI matches this inventory.
        DisguiseProperties props = activeCasts.get(clicker.getUniqueId());
        if (props == null || !e.getInventory().equals(props.gui)) return;

        e.setCancelled(true);

        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String displayName = meta.getDisplayName();

        if (displayName.equals(format(lang().get("spells.disguise.next_page"))) || displayName.equals(format(lang().get("spells.disguise.previous_page")))) {
            int  newPage    = NBT.get(item, nbt -> (Integer) nbt.getInteger("page"));
            UUID ownerUuid  = UUID.fromString(NBT.get(item, nbt -> (String) nbt.getString("player")));
            int  totalPages = NBT.get(item, nbt -> (Integer) nbt.getInteger("total_pages"));

            // The arrow always encodes the caster who originally opened the GUI.
            Player owner = Bukkit.getPlayer(ownerUuid);
            if (owner != null) {
                DisguiseProperties ownerProps = activeCasts.get(ownerUuid);
                if (ownerProps != null) {
                    createGUI(newPage, owner, totalPages, ownerProps);
                }
            }

        } else if (item.getType().equals(Material.PLAYER_HEAD)) {
            Player target = Bukkit.getPlayer(displayName);
            MagicProfile data = ProfileManager.getProfile(clicker.getUniqueId(), MagicProfile.class);

            if (target == null) {
                clicker.sendMessage(format(lang().get("spells.disguise.player_not_found")));
                return;
            }

            if (Objects.equals(target.getUniqueId().toString(), data.getDisguise())) {
                clicker.sendMessage(format(lang().get("spells.disguise.already_disguised")));
                return;
            }

            // Skin first, profile second, exactly as dispel() does it. Skin.fromURL
            // performs a blocking HTTP call and throws RuntimeException on failure
            // (Skin.java:76-80); it used to sit OUTSIDE the try, so it threw after
            // setDisguise and setDisplayName had already committed, and
            // onInventoryClose then refunded the mana as if nothing had happened.
            Skin skin;
            try {
                skin = Skin.fromURL(
                        "https://sessionserver.mojang.com/session/minecraft/profile/"
                                + target.getUniqueId() + "?unsigned=false");
                Alkatraz.getNms().changeSkin(clicker, List.copyOf(Bukkit.getOnlinePlayers()), skin);
                data.setDisguise(target.getUniqueId().toString());
                clicker.setDisplayName(target.getName());
                EffectApplicator.applyConcealment(clicker, this, EffectApplicator.disguiseHandle());
                props.selected = true;
            } catch (RuntimeException ex) {
                props.selected = false;
            }

            clicker.closeInventory();
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    private void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player closer)) return;

        DisguiseProperties props = activeCasts.remove(closer.getUniqueId());
        if (props == null || !e.getInventory().equals(props.gui)) return;

        if (!props.selected) {
            closer.sendMessage(format(lang().get("spells.disguise.no_player_chosen")));
            StatUtils.addMana(closer, getCost());
        }
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent e) {
        Player joined = e.getPlayer();

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.size() <= 1) return;

        List<Player> joinedOnly = List.of(joined);

        for (Player other : online) {
            if (other == joined) continue;

            MagicProfile odata = ProfileManager.getProfile(other.getUniqueId(), MagicProfile.class);

            // Bug 1: this used to be `odata.getDisguise() != null`, but
            // Profile.stringStat registers a "" default, and Profile.setString
            // does NOT null-guard, so an in-memory setDisguise(null) yields null
            // until the next save/load. Feeding "" into UUID.fromString threw an
            // uncaught IllegalArgumentException out of PlayerJoinEvent. Blank is
            // the real "not disguised" value, so test for blank and let the
            // try/catch own anything malformed.
            //
            // Bug 2: the old `else` branch stamped the joiner's UUID into every
            // non-disguised player's profile. A dispel reading getDisguise()
            // would consume that garbage. The branch was already documented as
            // suspicious in-source and is wrong either way, so it is deleted
            // rather than fixed.
            String stored = odata.getDisguise();
            if (stored == null || stored.isBlank()) continue;

            try {
                UUID disguisedAs = UUID.fromString(stored);
                if (Bukkit.getPlayer(disguisedAs) != null) {
                    Alkatraz.getNms().changeSkinElse(
                            other,
                            joinedOnly,
                            Skin.fromURL("https://sessionserver.mojang.com/session/minecraft/profile/"
                                    + disguisedAs + "?unsigned=false"));
                    // Disguise survives a restart (it lives on the profile), so it
                    // re-stamps on join or a dispel cannot see it.
                    EffectApplicator.applyConcealment(other, this, EffectApplicator.disguiseHandle());
                }
            } catch (RuntimeException ignored) {
                // A malformed stored id or a Mojang outage must not escape
                // PlayerJoinEvent. The disguise simply stays unapplied this join;
                // the next one retries.
            }
        }
    }

    // -------------------------------------------------------------------------
    // Per-cast state container
    // -------------------------------------------------------------------------

    protected class DisguiseProperties extends SpellProperties {

        /** The inventory currently open for this caster. */
        protected Inventory gui;

        /**
         * Whether the caster successfully chose a disguise target.
         * Kept here so concurrent casts never share a flag.
         */
        protected boolean selected = false;

        /** Page map built at cast time for this specific caster. */
        protected final Map<Integer, List<Player>> guiPages = new HashMap<>();

        protected DisguiseProperties(LivingEntity caster, Location castLocation) {
            super(caster, castLocation);
        }

        public Inventory getGui() {
            return gui;
        }

        public void setGui(Inventory gui) {
            this.gui = gui;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        public Map<Integer, List<Player>> getGuiPages() {
            return guiPages;
        }
    }

    /**
     * Drops an in-flight Disguise cast without refunding anything.
     *
     * <p>The mana refund lives in {@code onInventoryClose}, which reads the
     * pending cast out of {@code activeCasts} and refunds when nothing was
     * selected. Removing the entry first is therefore the whole fix: the close
     * then finds nothing, returns early, and refunds nothing. The GUI stays open
     * but goes inert, because {@code onInventoryClick} matches on the same entry.
     *
     * @param caster the caster whose pending cast should be dropped
     */
    public void cancelCast(UUID caster) {
        activeCasts.remove(caster);
    }

    /**
     * Reverses an active Disguise on a victim who did not cast it. Called by
     * {@code Dispel} when the victim is not the caster.
     *
     * <p><b>Skin first, profile second.</b> That ordering is the entire point of
     * this method. Clearing the profile and <em>then</em> failing the reset would
     * leave a player whose profile says clean but whose texture never reverts —
     * and because {@code onJoin} skips players with no disguise, that desync
     * persists across every relog until the profile is hand-edited. Doing it in
     * this order turns a Mojang outage into a clean <em>resisted</em>, which is a
     * result the contest in spec 4.5.3 already has a vocabulary for.
     *
     * <p>No new NMS code is needed in any version module: {@code changeSkin}
     * already exists on the interface, and {@code Bukkit.getOfflinePlayer}'s
     * {@code PlayerProfile} is <em>not</em> usable here because it carries
     * textures with no Mojang signature, which is what the sessionserver round
     * trip exists to supply.
     *
     * <p>The concealment stamp is dropped only once the profile is clear, so a
     * failed fetch leaves the illusion registered and still contestable.
     *
     * @param victim the player whose disguise is being broken
     * @return true if the illusion was cleared, false if it resisted
     */
    public boolean dispel(Player victim) {
        MagicProfile data = ProfileManager.getProfile(victim.getUniqueId(), MagicProfile.class);
        if (data == null) return false;

        String stored = data.getDisguise();
        if (stored == null || stored.isBlank()) return false;

        try {
            UUID.fromString(stored);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        Skin ownSkin;
        try {
            ownSkin = Skin.fromURL("https://sessionserver.mojang.com/session/minecraft/profile/"
                    + victim.getUniqueId() + "?unsigned=false");
        } catch (RuntimeException ex) {
            return false;
        }

        try {
            Alkatraz.getNms().changeSkin(victim, List.copyOf(Bukkit.getOnlinePlayers()), ownSkin);
        } catch (RuntimeException ex) {
            return false;
        }

        data.setDisguise(null);
        victim.setDisplayName(victim.getName());
        unstampConcealment(victim);
        return true;
    }

    /**
     * Drops this spell's concealment stamp from a player whose disguise has just
     * been broken.
     *
     * <p>Mirrors {@code Stealth.unstampConcealment} and for the same reason: a
     * concealment entry carries {@code Long.MAX_VALUE} and never self-prunes, so
     * a reversal that leaves it behind would let a later dispel report clearing
     * an illusion that no longer exists.
     *
     * @param p the player whose concealment stamp should be dropped
     */
    private void unstampConcealment(Player p) {
        for (ActiveEffect effect : EffectRegistry.effectsOn(p.getUniqueId())) {
            if (effect.kind() == EffectKind.CONCEALMENT && EffectApplicator.isDisguiseHandle(effect.handle())) {
                EffectRegistry.remove(effect);
            }
        }
    }
}