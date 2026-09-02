package me.nagasonic.alkatraz.items.magic.barrier;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class BarrierGeometry {

    private BarrierGeometry() {}

    /** Golden-angle distribution of {@code points} vectors on a sphere of {@code radius}. */
    public static List<Vector> sphereVectors(double radius, int points) {
        List<Vector> out = new ArrayList<>();
        if (points < 1 || radius <= 0) return out;
        double phi = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < points; i++) {
            double y = 1 - (i / (double) (points - 1)) * 2;
            double r = Math.sqrt(Math.max(0, 1 - y * y));
            double theta = phi * i;
            out.add(new Vector(Math.cos(theta) * r * radius, y * radius, Math.sin(theta) * r * radius));
        }
        return out;
    }

    /**
     * Returns a lat-long grid of points on a spherical cap. Local frame has +Z as the
     * facing direction; lon (azimuth) is measured from +Z, lat (elevation) from horizon.
     * Final points are rotated around Y by {@code -facingYawDegrees} to match the plugin's
     * Bufkit yaw convention (see ParticleUtils.circle).
     */
    public static List<Vector> capVectors(double radius, int points, double yawArcDegrees,
                                          double pitchMinDegrees, double pitchMaxDegrees,
                                          double facingYawDegrees) {
        List<Vector> out = new ArrayList<>();
        if (points < 1 || radius <= 0) return out;
        if (yawArcDegrees >= 360) return sphereVectors(radius, points);

        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(points)));
        int rows = Math.max(1, (int) Math.ceil((double) points / cols));
        double latMin = Math.toRadians(Math.min(pitchMinDegrees, pitchMaxDegrees));
        double latMax = Math.toRadians(Math.max(pitchMinDegrees, pitchMaxDegrees));
        double lonHalf = Math.toRadians(Math.abs(yawArcDegrees) / 2.0);

        for (int row = 0; row < rows; row++) {
            double t = rows == 1 ? 0.5 : row / (double) (rows - 1);
            double lat = latMin + (latMax - latMin) * t;
            double cosLat = Math.cos(lat);
            double sinLat = Math.sin(lat);
            for (int col = 0; col < cols; col++) {
                double s = cols == 1 ? 0 : col / (double) (cols - 1);
                double lon = -lonHalf + 2 * lonHalf * s;
                double z = Math.cos(lon) * cosLat;
                double x = Math.sin(lon) * cosLat;
                out.add(new Vector(x, sinLat, z).multiply(radius)
                        .rotateAroundY(Math.toRadians(-facingYawDegrees)));
            }
        }
        return out;
    }
}
