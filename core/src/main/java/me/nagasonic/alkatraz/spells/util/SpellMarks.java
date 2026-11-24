package me.nagasonic.alkatraz.spells.util;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.vfx.ElementPalette;
import me.nagasonic.alkatraz.util.ParticleCaster;
import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caster-only particle outline plus actionbar label for scanned blocks and
 * highlighted drops. Marks are visible to the viewer alone
 * ({@link ParticleCaster#spawnForPlayer}) and expire after 10 seconds.
 */
public final class SpellMarks {

    private static final long PERIOD_TICKS = 5L;
    private static final long LIFETIME_TICKS = 200L;
    private static final int MAX_LABELS = 5;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private SpellMarks() {
    }

    /** Adds a mark for {@code viewer} only, with an optional actionbar label. */
    public static void mark(Player viewer, Location loc, ElementPalette.Palette palette, String label) {
        if (viewer == null || loc == null || loc.getWorld() == null) return;
        ElementPalette.Palette pal = palette == null ? ElementPalette.of(Element.NONE) : palette;
        Session session = SESSIONS.computeIfAbsent(viewer.getUniqueId(), id -> new Session());
        session.marks.add(new Mark(loc.clone(), pal.coreDust()));
        if (label != null && !label.isBlank()) session.labels.add(label);
        session.elapsed = 0;
        if (session.task == null) session.start(viewer);
    }

    /** Drops every mark for {@code viewer}. */
    public static void clear(Player viewer) {
        if (viewer == null) return;
        Session session = SESSIONS.remove(viewer.getUniqueId());
        if (session != null) session.stop();
    }

    /** Drops every mark for every viewer. Called from plugin disable. */
    public static void shutdown() {
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            Session session = SESSIONS.remove(id);
            if (session != null) session.stop();
        }
    }

    /**
     * The 48 points of a block-hugging wireframe cube. Pure geometry, exposed for tests.
     */
    static void boxOutline(Location block, List<Location> out) {
        if (block == null || out == null || block.getWorld() == null) return;
        World world = block.getWorld();
        double x = block.getBlockX();
        double y = block.getBlockY();
        double z = block.getBlockZ();
        double lo = 0.06;
        double hi = 0.94;
        double[] xs = {x + lo, x + hi};
        double[] ys = {y + lo, y + hi};
        double[] zs = {z + lo, z + hi};
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                addEdge(out, world, xs[a], ys[b], zs[0], xs[a], ys[b], zs[1]);
                addEdge(out, world, xs[a], ys[0], zs[b], xs[a], ys[1], zs[b]);
                addEdge(out, world, xs[0], ys[a], zs[b], xs[1], ys[a], zs[b]);
            }
        }
    }

    private static void addEdge(List<Location> out, World world,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2) {
        out.add(new Location(world, x1, y1, z1));
        out.add(new Location(world, x1 + (x2 - x1) / 3.0, y1 + (y2 - y1) / 3.0, z1 + (z2 - z1) / 3.0));
        out.add(new Location(world, x1 + 2.0 * (x2 - x1) / 3.0, y1 + 2.0 * (y2 - y1) / 3.0,
                z1 + 2.0 * (z2 - z1) / 3.0));
        out.add(new Location(world, x2, y2, z2));
    }

    private record Mark(Location location, org.bukkit.Particle.DustOptions dust) {
    }

    private static final class Session {
        private final List<Mark> marks = new ArrayList<>();
        private final List<String> labels = new ArrayList<>();
        private final List<Location> scratch = new ArrayList<>();
        private BukkitTask task;
        private long elapsed;

        void start(Player viewer) {
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!viewer.isOnline() || elapsed >= LIFETIME_TICKS) {
                        SESSIONS.remove(viewer.getUniqueId());
                        stop();
                        return;
                    }
                    draw(viewer);
                    elapsed += PERIOD_TICKS;
                }
            }.runTaskTimer(Alkatraz.getInstance(), 0L, PERIOD_TICKS);
        }

        void stop() {
            if (task != null) {
                task.cancel();
                task = null;
            }
            marks.clear();
            labels.clear();
        }

        private void draw(Player viewer) {
            for (Mark mark : marks) {
                World world = mark.location().getWorld();
                if (world == null) continue;
                scratch.clear();
                boxOutline(mark.location(), scratch);
                for (Location point : scratch) {
                    ParticleCaster.spawnForPlayer(viewer, world, Utils.DUST, point, 1, mark.dust());
                }
            }
            if (!labels.isEmpty()) {
                StringBuilder line = new StringBuilder();
                int shown = Math.min(MAX_LABELS, labels.size());
                for (int i = 0; i < shown; i++) {
                    if (i > 0) line.append("  ");
                    line.append(labels.get(i));
                }
                if (labels.size() > shown) line.append("  +").append(labels.size() - shown);
                Utils.sendActionBar(viewer, line.toString());
            }
        }
    }
}
