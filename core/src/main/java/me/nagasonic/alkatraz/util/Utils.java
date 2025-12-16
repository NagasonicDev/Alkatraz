package me.nagasonic.alkatraz.util;

import de.tr7zw.nbtapi.NBT;
import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.dom.*;
import me.nagasonic.alkatraz.items.wands.WandRegistry;
import me.nagasonic.alkatraz.spells.Element;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class Utils {
    public static final Particle DUST = Particle.valueOf(oldOrNew("REDSTONE", "DUST"));
    private static final Random random = new Random();

    public static Random getRandom(){
        return random;
    }

    public static ItemStack getBlank(){
        ItemStack blank = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = blank.getItemMeta();
        meta.setDisplayName("");
        blank.setItemMeta(meta);
        return blank;
    }

    public static boolean withinManhattanRange(Location l1, Location l2, double range){
        return Math.abs(l1.getX() - l2.getX()) <= range &&
                Math.abs(l1.getY() - l2.getY()) <= range &&
                Math.abs(l1.getZ() - l2.getZ()) <= range;
    }

    public static int getManhattanDistance(Location l1, Location l2){
        return Math.abs(l1.getBlockX() - l2.getBlockX()) +
                Math.abs(l1.getBlockY() - l2.getBlockY()) +
                Math.abs(l1.getBlockZ() - l2.getBlockZ());
    }

    public static int getManhattanDistance(int x1, int z1, int x2, int z2){
        return Math.abs(x1 - x2) + Math.abs(z1 - z2);
    }

    public static Map<String, OfflinePlayer> getPlayersFromUUIDs(Collection<UUID> uuids){
        Map<String, OfflinePlayer> players = new HashMap<>();
        for (UUID uuid : uuids){
            OfflinePlayer player = Alkatraz.getInstance().getServer().getOfflinePlayer(uuid);
            players.put(player.getName(), player);
        }
        return players;
    }

    public static Map<String, Player> getOnlinePlayersFromUUIDs(Collection<UUID> uuids){
        Map<String, Player> players = new HashMap<>();
        for (UUID uuid : uuids){
            Player player = Alkatraz.getInstance().getServer().getPlayer(uuid);
            if (player != null) players.put(player.getName(), player);
        }
        return players;
    }

    public static Color hexToRgb(String colorStr) {
        return Color.fromRGB(Integer.valueOf( colorStr.substring( 1, 3 ), 16 ),
                Integer.valueOf( colorStr.substring( 3, 5 ), 16 ),
                Integer.valueOf( colorStr.substring( 5, 7 ), 16 ));
    }

    public static String rgbToHex(int r, int g, int b){
        return String.format("#%02x%02x%02x", r, g, b);
    }

    /**
     * Returns an integer based on the chance given, but always between this chance rounded down and the chance rounded
     * up. Example:
     * a chance of 3.4 will always return at least 3, with a 40% chance to return 4 instead.
     * a chance of 0.9 will have a 90% chance to return 1
     * a chance of 7.5 will always return at least 7, with a 50% chance to return 8 instead.
     * @param chance the average to calculate from
     * @return an integer returning at least the average rounded down, with the remaining chance to return 1 extra
     */
    public static int randomAverage(double chance){
        boolean negative = chance < 0;
        int atLeast = (negative) ? (int) Math.ceil(chance) : (int) Math.floor(chance);
        double remainingChance = chance - atLeast;
        if (getRandom().nextDouble() <= Math.abs(remainingChance)) atLeast += negative ? -1 : 1;
        return atLeast;
    }

    /**
     * Returns a collection of players from the given selector.
     * Returns a collection with a single player if no selector was used.
     * @throws IllegalArgumentException if an invalid selector was used
     * @param source the command sender that attempts the selector
     * @param selector the selector string
     * @return a collection of matching players, or single player if a single online player was given
     */
    public static Collection<Player> selectPlayers(CommandSender source, String selector) throws IllegalArgumentException{
        Collection<Player> targets = new HashSet<>();
        if (selector.startsWith("@")){
            for (Entity part : Bukkit.selectEntities(source, selector)){
                if (part instanceof Player)
                    targets.add((Player) part);
            }
        } else {
            Player target = Alkatraz.getInstance().getServer().getPlayer(selector);
            if (target != null) targets.add(target);
        }
        return targets;
    }

    public static double round6Decimals(double d){
        return (double) Math.round(d * 1000000d) / 1000000d;
    }

    public static double roundToMultiple(double number, double multiple) {
        return multiple * Math.round(number / multiple);
    }

    static final Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static List<String> chat(List<String> messages){
        List<String> chat = new ArrayList<>();
        for (String message : messages) {
            chat.add(chat(message));
        }
        return chat;
    }

    /**
     * Converts all color codes to ChatColor. Works with hex codes.
     * Hex code format is triggered with &#123456
     * @param message the message to convert
     * @return the converted message
     */
    public static String chat(String message) {
        if (StringUtils.isEmpty(message)) return "";
        return vanillaChat(message);
    }

    public static String vanillaChat(String message) {
        if (StringUtils.isEmpty(message)) return "";
        char COLOR_CHAR = ChatColor.COLOR_CHAR;
        Matcher matcher = hexPattern.matcher(message);
        StringBuilder buffer = new StringBuilder(message.length() + 4 * 8);
        while (matcher.find())
        {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, COLOR_CHAR + "x"
                    + COLOR_CHAR + group.charAt(0) + COLOR_CHAR + group.charAt(1)
                    + COLOR_CHAR + group.charAt(2) + COLOR_CHAR + group.charAt(3)
                    + COLOR_CHAR + group.charAt(4) + COLOR_CHAR + group.charAt(5)
            );
        }
        return ChatColor.translateAlternateColorCodes('&', matcher.appendTail(buffer).toString());
    }

    public static <T> Map<Integer, List<T>> paginate(int pageSize, List<T> allEntries) {
        Map<Integer, List<T>> pages = new HashMap<>();

        int maxPages = (int) Math.ceil((double) allEntries.size() / (double) pageSize);
        for (int pageNumber = 0; pageNumber < maxPages; pageNumber++) {
            pages.put(pageNumber, allEntries.subList( // sublist from start of page to start of next page
                    Math.min(pageNumber * pageSize, allEntries.size()),
                    Math.min((pageNumber + 1) * pageSize, allEntries.size())
            ));
        }

        return pages;
    }

    /**
     * Sends a message to the CommandSender, but only if the message isn't null or empty
     * @param whomst the CommandSender whomst to message
     * @param message the message to send
     */
    public static void sendMessage(CommandSender whomst, String message){
        if (!StringUtils.isEmpty(message)) {
            if (message.startsWith("ACTIONBAR") && whomst instanceof Player p) {
                sendActionBar(p, message.replaceFirst("ACTIONBAR", ""));
            } else if (message.startsWith("TITLE") && whomst instanceof Player p){
                String title = message.replaceFirst("TITLE", "");
                String subtitle = "";
                int titleDuration = 40;
                int fadeDuration = 5;
                String subString = StringUtils.substringBetween(message, "TITLE(", ")");
                if (subString != null){
                    String[] args = subString.split(";");
                    if (args.length > 0) title = args[0];
                    if (args.length > 1) subtitle = args[1];
                    if (args.length > 2) titleDuration = Catch.catchOrElse(() -> Integer.parseInt(args[2]), 100);
                    if (args.length > 3) fadeDuration = Catch.catchOrElse(() -> Integer.parseInt(args[2]), 10);
                }
                sendTitle(p, title, subtitle, titleDuration, fadeDuration);
            } else {
                whomst.sendMessage(chat(message));
            }
        }
    }

    public static void sendActionBar(Player whomst, String message){
        if (!StringUtils.isEmpty(ChatColor.stripColor(chat(message)))) whomst.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(chat(message)));
    }

    public static void sendTitle(Player whomst, String title, String subtitle, int duration, int fade){
        if (!StringUtils.isEmpty(title)) whomst.sendTitle(chat(title), chat(subtitle), fade, duration, fade);
    }

    public static <T extends Weighted> List<T> weightedSelection(Collection<T> entries, int rolls, double luck, double fortune){
        // weighted selection
        double totalWeight = 0;
        List<T> selectedEntries = new ArrayList<>();
        if (entries.isEmpty()) return selectedEntries;
        List<Pair<T, Double>> totalEntries = new ArrayList<>();
        for (T entry : entries){
            totalWeight += entry.getWeight(luck, fortune);
            totalEntries.add(new Pair<>(entry, totalWeight));
        }

        for (int i = 0; i < rolls; i++){
            double random = Utils.getRandom().nextDouble() * totalWeight;
            for (Pair<T, Double> pair : totalEntries){
                if (pair.getTwo() >= random) {
                    selectedEntries.add(pair.getOne());
                    break;
                }
            }
        }
        return selectedEntries;
    }


    public static <T> T thisorDefault(T input, T def){
        return input == null ? def : input;
    }

    public static <T> T random(Collection<T> coll) {
        int num = (int) (Math.random() * coll.size());
        for(T t: coll) if (--num < 0) return t;
        throw new AssertionError();
    }

    public static void repeat(int times, Action<Integer> what){
        for (int i = 0; i < times; i++) what.act(i);
    }

    public static ScheduledExecutorService threadPool(String name, int timeoutMs, boolean daemon, int count) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(count, threadFactory(name, daemon));
        if (timeoutMs > 0) {
            executor.setKeepAliveTime(timeoutMs, TimeUnit.MILLISECONDS);
            executor.allowCoreThreadTimeOut(true);
        }
        return executor;
    }

    public static ThreadFactory threadFactory(String name, boolean daemon) {
        return r -> {
            Thread thread = new Thread(r);
            thread.setName("Alkatraz-Async-" + name + "-Thread");
            thread.setDaemon(daemon);
            return thread;
        };
    }

    public static ItemStack materialFromString(String item){
        ItemStack def = new ItemStack(Material.BARRIER);
        try {
            if (item.contains("$")){
                String[] aitem = item.split("$");
                if (aitem[0] == "WAND"){
                    def = WandRegistry.getWand(aitem[1]).getItem();
                }
            }else{
                def.setType(Material.valueOf(item));
            }
            return def;
        } catch (IllegalArgumentException ignored){
            Alkatraz.logWarning(
                    "ItemStack/Material " + item + " did not lead to an item stack or proper material type. Defaulted to " + "BARRIER"
            );
        }
        return def;
    }

    /**
     * Quicker method to check if the server is using 1.20.5 and above, which would result in different mappings
     */
    public static boolean hasNewMappings(){
        return MinecraftVersion.currentVersionNewerThan(MinecraftVersion.MINECRAFT_1_20_5);
    }

    /**
     * @return the 'o' value if below version 1.20.5, or n if at or above it.
     */
    public static String oldOrNew(String o, String n){
        return hasNewMappings() ? n : o;
    }

    /**
     * @return the amount of experience gained per spell based on circle level
     */
    public static double getExp(int circle){
        return switch (circle) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 7;
            case 4 -> 14;
            case 5 -> 26;
            case 6 -> 50;
            case 7 -> 94;
            case 8 -> 179;
            case 9 -> 340;
            default -> 0;
        };
    }

    public static double getEntityAffinity(Element element, LivingEntity entity){
        String name;
        if (element == Element.NONE){
            name = "magic";
        }else { name = element.name().toLowerCase(); }
        if (NBT.get(entity, nbt -> (Double) nbt.getDouble(name + "_affinity")) != null){
            return NBT.get(entity, nbt -> (Double) nbt.getDouble(name + "_affinity"));
        }
        return 0;
    }

    public static double getEntityResistance(Element element, LivingEntity entity){
        String name;
        if (element == Element.NONE){
            name = "magic";
        }else { name = element.name().toLowerCase(); }
        if (NBT.get(entity, nbt -> (Double) nbt.getDouble(name + "_resistance")) != null){
            return NBT.get(entity, nbt -> (Double) nbt.getDouble(name + "_resistance"));
        }
        return 0;
    }

    public static List<Block> blocksInRadius(Location loc, double radius){
        List<Block> blocks = new ArrayList<>();
        for (double i = radius / 0.5; i > 0; i -= 0.5){
            List<Location> locs= ParticleUtils.basicSphere(loc, radius, 10, 10);
            for (Location l : locs){
                if (!blocks.contains(l.getBlock())){
                    blocks.add(l.getBlock());
                }
            }
            radius -= 0.5;
        }
        return blocks;
    }

    public static boolean notAir(ItemStack item){
        if (item != null){
            if (item.getType() != Material.AIR && item.getAmount() != 0){
                return true;
            }
        }
        return false;
    }
}
