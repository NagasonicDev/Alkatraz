package me.nagasonic.alkatraz.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class ParticleUtils {
    public static List<Location> line(double frequency, Location loc1, Location loc2) {
        List<Location> locs = new ArrayList<>();
        Location loc = loc1;
        Vector line = LocationUtils.getDirectionBetweenLocations(loc1, loc2);
        for (double i = 1; i <= loc1.distance(loc2); i += frequency) {
            line.multiply(i);
            loc.add(line);
            locs.add(loc.clone());
            loc.subtract(line);
            line.normalize();
        }
        locs.remove(0);
        return locs;
    }

    public static List<Location> circle(Location center, double radius, double frequency, float yaw, float pitch){
        List<Location> locations = new ArrayList<>();

        // Convert yaw and pitch to radians
        double yawRad = Math.toRadians(-yaw); // In Minecraft, yaw is inverted
        double pitchRad = Math.toRadians(-pitch);
        int points = (int) (360 / frequency);
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;

            // Create the point in local XZ plane (flat circle)
            double x = Math.cos(angle) * radius;
            double y = 0;
            double z = Math.sin(angle) * radius;

            // Create vector
            Vector v = new Vector(x, y, z);

            // Apply pitch (X-axis rotation)
            v.rotateAroundX(pitchRad);

            // Apply yaw (Y-axis rotation)
            v.rotateAroundY(yawRad);

            // Add to center
            Location point = center.clone().add(v);
            locations.add(point);
        }

        return locations;
    }

    public static List<Location> createHelix(Location center, int loops, double radius, double height, int pointsPerLoop) {
        List<Location> locations = new ArrayList<>();

        int totalPoints = loops * pointsPerLoop;
        double heightStep = height / totalPoints;
        double angleStep = 2 * Math.PI / pointsPerLoop;

        for (int i = 0; i < totalPoints; i++) {
            double angle = i * angleStep;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            double y = i * heightStep;

            Location particleLocation = center.clone().add(x, y, z);
            locations.add(particleLocation);
        }

        return locations;
    }

    public static List<Location> regularPolygon(Location center, int sides, double radius, int frequency, float yaw, float pitch, float rotationOffsetDegrees) {
        List<Location> points = new ArrayList<>();

        if (sides < 3) sides = 3;  // Minimum polygon is triangle

        double angleStep = 2 * Math.PI / sides;
        double rotationOffset = Math.toRadians(rotationOffsetDegrees);

        // Calculate all vertex positions first
        List<Vector> vertices = new ArrayList<>();
        for (int i = 0; i < sides; i++) {
            double angle = i * angleStep + rotationOffset;
            double x = radius * Math.cos(angle);
            double y = radius * Math.sin(angle);
            vertices.add(new Vector(x, y, 0));  // Polygon in XY plane, Z=0
        }

        // For each edge, interpolate points between vertices according to frequency
        for (int i = 0; i < sides; i++) {
            Vector start = vertices.get(i);
            Vector end = vertices.get((i + 1) % sides);

            for (int f = 0; f < frequency; f++) {
                double t = (double) f / frequency;  // interpolation factor [0,1)
                Vector point = LocationUtils.lerp(start, end, t);

                // Rotate point by yaw and pitch to align with player orientation
                Vector rotated = LocationUtils.rotateVector(point, yaw, pitch);

                // Add final point translated to world location
                points.add(center.clone().add(rotated));
            }
        }

        return points;
    }

    public static List<Location> sphere(Location center, double radius, int points) {
        List<Location> locations = new ArrayList<>();
        if (center.getWorld() == null) return locations;

        double phi = Math.PI * (3.0 - Math.sqrt(5.0)); // golden angle

        for (int i = 0; i < points; i++) {
            double y = 1 - (i / (double) (points - 1)) * 2;
            double r = Math.sqrt(1 - y * y);
            double theta = phi * i;

            double x = Math.cos(theta) * r;
            double z = Math.sin(theta) * r;

            locations.add(
                    center.clone().add(
                            x * radius,
                            y * radius,
                            z * radius
                    )
            );
        }

        return locations;
    }

    public static List<Location> fibonacciSphere(Location center, double radius, int points) {
        List<Location> locs = new ArrayList<>();
        if (points < 1) return locs;  // no points requested, return empty

        double phi = Math.PI * (3 - Math.sqrt(5)); // golden angle in radians

        for (int i = 0; i < points; i++) {
            double y;
            if (points == 1) {
                y = 0; // just center at equator if only one point
            } else {
                y = 1 - (i / (double)(points - 1)) * 2; // y goes from 1 to -1
            }
            double radiusAtY = Math.sqrt(1 - y * y);
            double theta = phi * i;

            double x = Math.cos(theta) * radiusAtY;
            double z = Math.sin(theta) * radiusAtY;

            Vector point = new Vector(x, y, z).multiply(radius);
            locs.add(center.clone().add(point));
        }

        return locs;
    }

    public static List<Location> magicCircle(Location center, float yaw, float pitch, Vector offset, double size, double rotationOffsetDegrees) {
        List<Location> locs = new ArrayList<>();
        float newYaw = (float) (yaw + rotationOffsetDegrees);

        Location loc = center.clone().add(offset);
        double sizeMult = 5 / size;

        // Smooth circles
        locs.addAll(circle(loc, 1.5 / sizeMult, 80, newYaw, -pitch + 90));
        locs.addAll(circle(loc, 4.45 / sizeMult, 40, newYaw, -pitch + 90));

        // Squares (polygons) with smooth edges
        locs.addAll(regularPolygon(loc, 4, 3.0 / sizeMult, 5, newYaw, -pitch + 180, 0));
        locs.addAll(regularPolygon(loc, 4, 3.0 / sizeMult, 5, newYaw, -pitch + 180, 60));

        // Decorative Fibonacci spheres placed on a hex ring around the magic circle
        List<Location> sphereCenters = regularPolygon(loc, 6, 4.0 / sizeMult, 1, newYaw, -pitch + 180, 0);
        for (Location sphereCenter : sphereCenters) {
            locs.addAll(fibonacciSphere(sphereCenter, 0.25 / sizeMult, 6));
        }

        return locs;
    }
}
