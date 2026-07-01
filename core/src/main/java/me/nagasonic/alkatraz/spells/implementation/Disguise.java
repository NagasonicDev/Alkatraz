package me.nagasonic.alkatraz.spells.implementation;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.config.Configs;
import me.nagasonic.alkatraz.events.SpellPrepareEvent;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.NumberStatRequirement;
import me.nagasonic.alkatraz.spells.spellbooks.Spellbook;
import me.nagasonic.alkatraz.spells.types.properties.SpellProperties;
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
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static me.nagasonic.alkatraz.util.ColorFormat.format;

public class Disguise extends Spell implements Listener {

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
        return Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Alkatraz.getInstance(), () -> {
            if (e.isCancelled()) return;
            Location playerLoc = caster.getEyeLocation();
            float yaw   = playerLoc.getYaw();
            float pitch = playerLoc.getPitch();

            Vector forward = playerLoc.getDirection().normalize().multiply(1.5);
            List<Location> magicCirclePoints = ParticleUtils.magicCircle(playerLoc, yaw, pitch, forward, 2, 0);

            for (int i = 0; i < 100; i++) {
                for (Location loc : magicCirclePoints) {
                    loc.getWorld().spawnParticle(Utils.DUST, loc, 0,
                            new Particle.DustOptions(Color.WHITE, 0.4F));
                }
            }
        }, 0L, (Long) Configs.CIRCLE_TICKS.get());
    }

    @Override
    public ItemStack getSpellBook() {
        return new Spellbook(getId())
                .setDisplayName("&dIllusionist's Guide &oI")
                .addCustomLoreLine("&8The first step in mastering the art of illusions.")
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
        Inventory inv = Bukkit.createInventory(null, 54, "Disguise Target");

        // Top and bottom border rows.
        for (int i = 0; i < 9; i++)  inv.setItem(i,      Utils.getBlank());
        for (int i = 45; i < 54; i++) inv.setItem(i,     Utils.getBlank());

        // Next-page arrow.
        if (page < totalPages) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta  meta = next.getItemMeta();
            meta.setDisplayName(format("&fNext Page"));
            meta.setLore(List.of(format("&ePage " + (page + 1))));
            next.setItemMeta(meta);
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
            meta.setDisplayName(format("&fPrevious Page"));
            meta.setLore(List.of(format("&ePage " + (page - 1))));
            prev.setItemMeta(meta);
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
            meta.setLore(List.of(format("&aClick to disguise as " + target.getName())));
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

        if (displayName.equals(format("&fNext Page")) || displayName.equals(format("&fPrevious Page"))) {
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
                clicker.sendMessage(format("&cCouldn't find this player. Player must be online."));
                return;
            }

            if (Objects.equals(target.getUniqueId().toString(), data.getDisguise())) {
                clicker.sendMessage(format("&cYou are already disguised as this player."));
                return;
            }

            data.setDisguise(target.getUniqueId().toString());
            clicker.setDisplayName(target.getName());

            Skin skin = Skin.fromURL(
                    "https://sessionserver.mojang.com/session/minecraft/profile/"
                            + target.getUniqueId() + "?unsigned=false");

            try {
                Alkatraz.getNms().changeSkin(clicker,
                        List.copyOf(Bukkit.getOnlinePlayers()), skin);
                props.selected = true;
            } catch (UnsupportedOperationException ex) {
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
            closer.sendMessage(format("&cNo player was chosen, cancelling casting..."));
            StatUtils.addMana(closer, getCost());
        }
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent e) {
        Player joined = e.getPlayer();

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == joined) continue;

            MagicProfile odata = ProfileManager.getProfile(other.getUniqueId(), MagicProfile.class);

            if (odata.getDisguise() != null) {
                UUID disguisedAs = UUID.fromString(odata.getDisguise());
                if (Bukkit.getPlayer(disguisedAs) != null) {
                    Alkatraz.getNms().changeSkinElse(
                            other,
                            List.of(joined),
                            Skin.fromURL("https://sessionserver.mojang.com/session/minecraft/profile/"
                                    + disguisedAs + "?unsigned=false"));
                }
            } else {
                // Bug-for-bug preservation of the original logic; consider whether
                // you actually want this branch — it sets another player's disguise
                // to the joining player's UUID unconditionally.
                odata.setDisguise(joined.getUniqueId().toString());
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
}