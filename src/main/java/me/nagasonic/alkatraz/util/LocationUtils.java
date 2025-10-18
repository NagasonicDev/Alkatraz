package me.nagasonic.alkatraz.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public class LocationUtils {
    public static final double DEG_TO_RAD = Math.PI / 180;
    public static final double RAD_TO_DEG =  180 / Math.PI;

    public static Vector getDirectionBetweenLocations(Location start, Location end) {
        Vector from = start.toVector();
        Vector to = end.toVector();
        return to.subtract(from);
    }

    public static Vector fromYawAndPitch(float yaw, float pitch){
        double y = Math.sin(pitch * DEG_TO_RAD);
        double div = Math.cos(pitch * DEG_TO_RAD);
        double x = Math.cos(yaw * DEG_TO_RAD);
        double z = Math.sin(yaw * DEG_TO_RAD);
        x *= div;
        z *= div;
        return new Vector(x,y,z);
    }

    public static Vector fromSphericalCoordinates(double radius, double theta, double phi){
        double r = Math.abs(radius);
        double t = theta * DEG_TO_RAD;
        double p = phi * DEG_TO_RAD;
        double sinp = Math.sin(p);
        double x = r * sinp * Math.cos(t);
        double y = r * Math.cos(p);
        double z = r * sinp * Math.sin(t);
        return new Vector(x, y, z);
    }

    public static Vector fromCylindricalCoordinates(double radius, double phi, double height) {
        double r = Math.abs(radius);
        double p = phi * DEG_TO_RAD;
        double x = r * Math.cos(p);
        double z = r * Math.sin(p);
        return new Vector(x, height, z);
    }

    public static Vector interpolate(Vector a, Vector b, double t) {
        return a.clone().multiply(1 - t).add(b.clone().multiply(t));
    }

    public static Vector lerp(Vector start, Vector end, double t) {
        double x = start.getX() + (end.getX() - start.getX()) * t;
        double y = start.getY() + (end.getY() - start.getY()) * t;
        double z = start.getZ() + (end.getZ() - start.getZ()) * t;
        return new Vector(x, y, z);
    }

    public static Vector rotateVector(Vector vec, float yaw, float pitch) {
        double yawRad = Math.toRadians(-yaw);    // Negative because of Minecraft yaw direction
        double pitchRad = Math.toRadians(-pitch);

        // Rotate around X axis (pitch)
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);
        double y1 = vec.getY() * cosPitch - vec.getZ() * sinPitch;
        double z1 = vec.getY() * sinPitch + vec.getZ() * cosPitch;

        // Rotate around Y axis (yaw)
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double x2 = vec.getX() * cosYaw + z1 * sinYaw;
        double z2 = -vec.getX() * sinYaw + z1 * cosYaw;

        return new Vector(x2, y1, z2);
    }

    public static Vector rotateVectorYawOnly(Vector vec, float yawDegrees) {
        double yawRad = Math.toRadians(-yawDegrees);

        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);

        double x = vec.getX() * cosYaw - vec.getZ() * sinYaw;
        double z = vec.getX() * sinYaw + vec.getZ() * cosYaw;

        // Y unchanged (flat)
        return new Vector(x, vec.getY(), z);
    }

    public static float getYaw(Vector vector){
        if (((Double) vector.getX()).equals((double) 0) && ((Double) vector.getZ()).equals((double) 0)){
            return 0;
        }
        return (float) (Math.atan2(vector.getZ(), vector.getX()) * RAD_TO_DEG);
    }

    public static float getPitch(Vector vector) {
        double xy = Math.sqrt(vector.getX() * vector.getX() + vector.getZ() * vector.getZ());
        return (float) (Math.atan(vector.getY() / xy) * RAD_TO_DEG);
    }

    public static void setYaw(Vector vector, float value){
        float pitch = getPitch(vector);
        Vector newVector = fromYawAndPitch(value, pitch);
        vector.copy(newVector);
    }

    public static void setPitch(Vector vector, float value){
        float yaw = getYaw(vector);
        Vector newVector = fromYawAndPitch(yaw, value);
        vector.copy(newVector);
    }

    public static void addPitch(Vector vector, float amount){
        float yaw = getYaw(vector);
        float pitch = getPitch(vector);
        pitch -= amount;
        Vector newVector = fromYawAndPitch(yaw, pitch);
        vector.copy(newVector);
    }

    public static void addYaw(Vector vector, float amount){
        float yaw = getYaw(vector);
        float pitch = getPitch(vector);
        yaw += amount;
        Vector newVector = fromYawAndPitch(yaw, pitch);
        vector.copy(newVector);
    }
}
