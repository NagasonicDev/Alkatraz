package me.nagasonic.alkatraz.items.magic.barrier;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrierGeometryTest {

    private static final double EPSILON = 1e-6;

    @Test
    void sphereVectorsPointsAreOnRadius() {
        double radius = 5.0;
        int points = 50;
        List<Vector> vectors = BarrierGeometry.sphereVectors(radius, points);
        assertEquals(points, vectors.size());
        for (Vector v : vectors) {
            assertEquals(radius, v.length(), EPSILON);
        }
    }

    @Test
    void sphereVectorsInvalidInputReturnsEmpty() {
        assertTrue(BarrierGeometry.sphereVectors(1.0, 0).isEmpty());
        assertTrue(BarrierGeometry.sphereVectors(1.0, -1).isEmpty());
        assertTrue(BarrierGeometry.sphereVectors(0.0, 10).isEmpty());
        assertTrue(BarrierGeometry.sphereVectors(-1.0, 10).isEmpty());
    }

    @Test
    void capVectorsPointsAreOnRadius() {
        double radius = 4.0;
        int points = 30;
        double yawArc = 180.0;
        double pitchMin = -30.0;
        double pitchMax = 30.0;
        double facingYaw = 45.0;
        List<Vector> vectors = BarrierGeometry.capVectors(radius, points, yawArc, pitchMin, pitchMax, facingYaw);
        assertEquals(points, vectors.size());
        for (Vector v : vectors) {
            assertEquals(radius, v.length(), EPSILON);
        }
    }

    @Test
    void capVectorsInvalidInputReturnsEmpty() {
        assertTrue(BarrierGeometry.capVectors(1.0, 0, 180, -30, 30, 0).isEmpty());
        assertTrue(BarrierGeometry.capVectors(1.0, -1, 180, -30, 30, 0).isEmpty());
        assertTrue(BarrierGeometry.capVectors(0.0, 10, 180, -30, 30, 0).isEmpty());
        assertTrue(BarrierGeometry.capVectors(-1.0, 10, 180, -30, 30, 0).isEmpty());
    }

    @Test
    void capVectorsElevationWithinRange() {
        double radius = 3.0;
        int points = 40;
        double yawArc = 120.0;
        double pitchMin = -20.0;
        double pitchMax = 45.0;
        double facingYaw = 90.0;
        List<Vector> vectors = BarrierGeometry.capVectors(radius, points, yawArc, pitchMin, pitchMax, facingYaw);
        assertFalse(vectors.isEmpty());
        double yawMin = Math.min(pitchMin, pitchMax);
        double yawMax = Math.max(pitchMin, pitchMax);
        for (Vector v : vectors) {
            Vector local = v.clone().rotateAroundY(Math.toRadians(facingYaw));
            double elevationDeg = Math.toDegrees(Math.atan2(local.getY(), Math.hypot(local.getX(), local.getZ())));
            assertTrue(elevationDeg >= yawMin - EPSILON && elevationDeg <= yawMax + EPSILON,
                    "elevation " + elevationDeg + " not in [" + yawMin + ", " + yawMax + "]");
        }
    }

    @Test
    void capVectorsAzimuthWithinArc() {
        double radius = 3.0;
        int points = 40;
        double yawArc = 90.0;
        double pitchMin = -10.0;
        double pitchMax = 10.0;
        double facingYaw = 180.0;
        List<Vector> vectors = BarrierGeometry.capVectors(radius, points, yawArc, pitchMin, pitchMax, facingYaw);
        assertFalse(vectors.isEmpty());
        double halfArc = yawArc / 2.0;
        for (Vector v : vectors) {
            Vector local = v.clone().rotateAroundY(Math.toRadians(facingYaw));
            double azimuthDeg = Math.toDegrees(Math.atan2(local.getX(), local.getZ()));
            assertTrue(azimuthDeg >= -halfArc - EPSILON && azimuthDeg <= halfArc + EPSILON,
                    "azimuth " + azimuthDeg + " not in [" + (-halfArc) + ", " + halfArc + "]");
        }
    }

    @Test
    void capVectorsFacingYawZeroCenteredOnPlusZ() {
        double radius = 2.0;
        int points = 25;
        double yawArc = 60.0;
        double pitchMin = 0.0;
        double pitchMax = 0.0;
        double facingYaw = 0.0;
        List<Vector> vectors = BarrierGeometry.capVectors(radius, points, yawArc, pitchMin, pitchMax, facingYaw);
        assertFalse(vectors.isEmpty());
        double halfArc = yawArc / 2.0;
        for (Vector v : vectors) {
            double azimuthDeg = Math.toDegrees(Math.atan2(v.getX(), v.getZ()));
            assertTrue(azimuthDeg >= -halfArc - EPSILON && azimuthDeg <= halfArc + EPSILON,
                    "azimuth " + azimuthDeg + " not centered on +Z for facingYaw=0");
        }
    }

    @Test
    void capVectorsYawArc360DelegatesToSphere() {
        double radius = 5.0;
        int points = 20;
        List<Vector> cap = BarrierGeometry.capVectors(radius, points, 360.0, 0, 90, 0);
        List<Vector> sphere = BarrierGeometry.sphereVectors(radius, points);
        assertEquals(sphere.size(), cap.size());
        for (int i = 0; i < sphere.size(); i++) {
            assertEquals(sphere.get(i).getX(), cap.get(i).getX(), EPSILON);
            assertEquals(sphere.get(i).getY(), cap.get(i).getY(), EPSILON);
            assertEquals(sphere.get(i).getZ(), cap.get(i).getZ(), EPSILON);
        }
    }

    @Test
    void capVectorsYawArcOver360DelegatesToSphere() {
        double radius = 4.0;
        int points = 15;
        List<Vector> cap = BarrierGeometry.capVectors(radius, points, 400.0, -45, 45, 0);
        List<Vector> sphere = BarrierGeometry.sphereVectors(radius, points);
        assertEquals(sphere.size(), cap.size());
        for (int i = 0; i < sphere.size(); i++) {
            assertEquals(sphere.get(i).getX(), cap.get(i).getX(), EPSILON);
            assertEquals(sphere.get(i).getY(), cap.get(i).getY(), EPSILON);
            assertEquals(sphere.get(i).getZ(), cap.get(i).getZ(), EPSILON);
        }
    }
}
