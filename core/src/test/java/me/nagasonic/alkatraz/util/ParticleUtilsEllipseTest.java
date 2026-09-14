package me.nagasonic.alkatraz.util;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleUtilsEllipseTest {

    private static final Location CENTER = new Location(null, 100, 64, -200);

    @Test
    void returnsRequestedPointCount() {
        assertEquals(20, ParticleUtils.ellipse(CENTER, 0, 4, 6, 20).size());
        assertEquals(36, ParticleUtils.ellipse(CENTER, 45, 4, 6, 36).size());
    }

    @Test
    void yawZeroPlaneIsPerpendicularToFacing() {
        List<Location> pts = ParticleUtils.ellipse(CENTER, 0, 4, 6, 40);
        for (Location p : pts) {
            assertEquals(-200, p.getZ(), 1e-6, "z constant when facing +Z");
            assertTrue(p.getX() >= 98 - 1e-6 && p.getX() <= 102 + 1e-6, "x spans width");
            assertTrue(p.getY() >= 61 - 1e-6 && p.getY() <= 67 + 1e-6, "y spans height");
        }
    }

    @Test
    void yawNinetyPlaneIsPerpendicularToFacing() {
        List<Location> pts = ParticleUtils.ellipse(CENTER, 90, 4, 6, 40);
        for (Location p : pts) {
            assertEquals(100, p.getX(), 1e-6, "x constant when facing -X");
            assertTrue(p.getZ() >= -202 - 1e-6 && p.getZ() <= -198 + 1e-6, "z spans width");
            assertTrue(p.getY() >= 61 - 1e-6 && p.getY() <= 67 + 1e-6, "y spans height");
        }
    }

    @Test
    void oppositePointsAreSymmetricAboutCenter() {
        List<Location> pts = ParticleUtils.ellipse(CENTER, 30, 4, 6, 8);
        Location a = pts.get(0);
        Location b = pts.get(4);
        assertEquals(2 * CENTER.getX(), a.getX() + b.getX(), 1e-6);
        assertEquals(2 * CENTER.getY(), a.getY() + b.getY(), 1e-6);
        assertEquals(2 * CENTER.getZ(), a.getZ() + b.getZ(), 1e-6);
    }

    @Test
    void spansRequestedWidthAndHeight() {
        List<Location> pts = ParticleUtils.ellipse(CENTER, 0, 4, 6, 120);
        double maxX = pts.stream().mapToDouble(Location::getX).map(v -> Math.abs(v - 100)).max().orElse(0);
        double maxY = pts.stream().mapToDouble(Location::getY).map(v -> Math.abs(v - 64)).max().orElse(0);
        assertEquals(2.0, maxX, 1e-6, "half of width");
        assertEquals(3.0, maxY, 1e-6, "half of height");
    }

    @Test
    void nonPositivePointCountReturnsEmpty() {
        assertTrue(ParticleUtils.ellipse(CENTER, 0, 4, 6, 0).isEmpty());
        assertTrue(ParticleUtils.ellipse(CENTER, 0, 4, 6, -5).isEmpty());
    }
}