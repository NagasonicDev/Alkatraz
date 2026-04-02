package me.nagasonic.alkatraz.spells.spellbooks;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.configuration.impact.ValueImpact;
import me.nagasonic.alkatraz.spells.configuration.requirement.ValueRequirement;
import me.nagasonic.alkatraz.util.ColorFormat;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for spellbooks that unlock spells when used
 * 
 * Features:
 * - Requirements that must be met before use
 * - Impacts applied when spell is discovered
 * - Discovery animation with particles and sounds
 * - Automatic spell unlock on use
 * - Item is consumed on successful use
 */
public class Spellbook {
    
    protected final String spellId;
    protected final Spell spell;
    protected final List<ValueRequirement> requirements;
    protected final List<ValueImpact> impacts;
    
    // Display customization
    protected String displayName;
    protected List<String> lore;
    protected Material material;
    
    /**
     * Creates a spellbook for a specific spell
     * 
     * @param spellId The ID of the spell to unlock
     */
    public Spellbook(String spellId) {
        this.spellId = spellId;
        this.spell = SpellRegistry.getSpell(spellId);
        this.requirements = new ArrayList<>();
        this.impacts = new ArrayList<>();
        
        // Default appearance
        this.material = Material.KNOWLEDGE_BOOK;
        this.displayName = spell != null ? "&6Spellbook: " + spell.getDisplayName() : "&6Unknown Spellbook";
        this.lore = new ArrayList<>();
        generateDefaultLore();
    }
    
    /**
     * Generates default lore based on spell info
     */
    private void generateDefaultLore() {
        if (spell == null) {
            lore.add("&7This spellbook appears to be corrupted.");
            return;
        }
        
        lore.add("&7Contains knowledge of:");
        lore.add(spell.getDisplayName());
        lore.add("");
        
        // Add spell description
        for (String descLine : spell.getDescription()) {
            lore.add(descLine);
        }
        
        lore.add("");
        lore.add("&eCircle " + spell.getLevel() + " &7| &b" + spell.getElement().name());
        lore.add("");
        lore.add("&7Right-click to discover this spell!");
    }
    
    /**
     * Adds a requirement that must be met to use the spellbook
     */
    public Spellbook addRequirement(ValueRequirement requirement) {
        this.requirements.add(requirement);
        return this;
    }
    
    /**
     * Adds an impact that is applied when the spell is discovered
     */
    public Spellbook addImpact(ValueImpact impact) {
        this.impacts.add(impact);
        return this;
    }
    
    /**
     * Sets custom display name
     */
    public Spellbook setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }
    
    /**
     * Sets custom lore (replaces default)
     */
    public Spellbook setLore(List<String> lore) {
        this.lore = new ArrayList<>(lore);
        return this;
    }
    
    /**
     * Adds a line to the lore
     */
    public Spellbook addLoreLine(String line) {
        this.lore.add(line);
        return this;
    }
    
    /**
     * Sets the material for the spellbook item
     */
    public Spellbook setMaterial(Material material) {
        this.material = material;
        return this;
    }
    
    /**
     * Builds the ItemStack for this spellbook
     */
    public ItemStack build() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            // Set display name
            meta.setDisplayName(ColorFormat.format(displayName));
            
            // Set lore with requirements and impacts
            List<String> finalLore = new ArrayList<>();
            
            // Add custom lore
            for (String line : lore) {
                finalLore.add(ColorFormat.format(line));
            }
            
            // Add requirements section if any
            if (!requirements.isEmpty()) {
                finalLore.add("");
                finalLore.add(ColorFormat.format("&cRequirements:"));
                for (ValueRequirement req : requirements) {
                    finalLore.add(ColorFormat.format("&7- " + req.getDescription()));
                }
            }
            
            // Add impacts section if any
            if (!impacts.isEmpty()) {
                finalLore.add("");
                finalLore.add(ColorFormat.format("&aEffects:"));
                for (ValueImpact impact : impacts) {
                    finalLore.add(ColorFormat.format("&7- " + impact.getDescription()));
                }
            }
            
            meta.setLore(finalLore);
            item.setItemMeta(meta);
        }
        
        // Add NBT data to identify as spellbook
        NBT.modify(item, nbt -> {
            nbt.setString("spellbook_type", "regular");
            nbt.setString("spell_id", spellId);
        });
        return item;
    }
    
    /**
     * Attempts to use the spellbook
     * 
     * @param player The player using the spellbook
     * @param item The spellbook item
     * @return true if successfully used, false otherwise
     */
    public boolean use(Player player, ItemStack item) {
        if (spell == null) {
            player.sendMessage(ColorFormat.format("&cThis spellbook appears to be corrupted!"));
            return false;
        }
        player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
        
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        
        // Check if spell is already discovered
        if (profile.hasDiscoveredSpell(spell)) {
            player.sendMessage(ColorFormat.format("&eYou already know " + spell.getDisplayName() + "&e!"));
            return false;
        }
        
        // Check all requirements
        for (ValueRequirement requirement : requirements) {
            if (!requirement.isMet(player)) {
                player.sendMessage(ColorFormat.format("&cRequirement not met: &7" + requirement.getDescription()));
                return false;
            }
        }
        
        // All requirements met - begin discovery animation
        playDiscoveryAnimation(player, () -> {
            // Discover the spell
            profile.setDiscoveredSpell(spell, true);
            
            // Apply all impacts
            for (ValueImpact impact : impacts) {
                impact.apply(player);
            }
            
            // Success message
            player.sendMessage(ColorFormat.format("&aYou have discovered " + spell.getDisplayName() + "&a!"));
            player.sendMessage(ColorFormat.format("&7Use &e/spells &7to view your discovered spells."));
            
            // Consume the item
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
        });
        
        return true;
    }
    
    /**
     * Plays the discovery animation
     */
    private void playDiscoveryAnimation(Player player, Runnable onComplete) {
        Location loc = player.getLocation();
        
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 60; // 3 seconds
            
            @Override
            public void run() {
                if (!player.isOnline() || ticks >= maxTicks) {
                    // Animation complete
                    if (player.isOnline() && ticks >= maxTicks) {
                        // Final burst
                        loc.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, 
                                loc.clone().add(0, 1, 0), 100, 1, 1, 1, 1);
                        loc.getWorld().spawnParticle(Particle.END_ROD, 
                                loc.clone().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
                        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        
                        onComplete.run();
                    }
                    cancel();
                    return;
                }
                
                Location playerLoc = player.getLocation().add(0, 1, 0);
                
                // Spiral of particles rising up
                double radius = 1.5 - (ticks / (double) maxTicks) * 0.5;
                double height = (ticks / (double) maxTicks) * 3;
                
                for (int i = 0; i < 3; i++) {
                    double angle = (ticks * 0.3) + (i * Math.PI * 2 / 3);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    
                    Location particleLoc = playerLoc.clone().add(x, height, z);
                    particleLoc.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, 
                            particleLoc, 2, 0, 0, 0, 0.1);
                }
                
                // Circle at player's feet
                if (ticks % 5 == 0) {
                    int particles = 16;
                    for (int i = 0; i < particles; i++) {
                        double angle = (Math.PI * 2 * i) / particles;
                        double x = Math.cos(angle) * 1.5;
                        double z = Math.sin(angle) * 1.5;
                        
                        Location particleLoc = loc.clone().add(x, 0.1, z);
                        particleLoc.getWorld().spawnParticle(Particle.REDSTONE, particleLoc, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.0f));
                    }
                }
                
                // Sound effects
                if (ticks % 10 == 0) {
                    loc.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.0f + (ticks / (float) maxTicks));
                }
                
                if (ticks == maxTicks / 2) {
                    loc.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                }
                
                ticks++;
            }
        }.runTaskTimer(Alkatraz.getInstance(), 0L, 1L);
    }
    
    /**
     * Gets the spell this spellbook unlocks
     */
    public Spell getSpell() {
        return spell;
    }
    
    /**
     * Gets the spell ID
     */
    public String getSpellId() {
        return spellId;
    }
    
    /**
     * Checks if an ItemStack is a spellbook
     */
    public static boolean isSpellbook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        
        String type = NBT.get(item, nbt -> (String) nbt.getString("spellbook_type"));
        return "regular".equals(type);
    }
    
    /**
     * Gets the spell ID from a spellbook item
     */
    public static String getSpellId(ItemStack item) {
        if (!isSpellbook(item)) return null;
        return NBT.get(item, nbt -> (String) nbt.getString("spell_id"));
    }
}
