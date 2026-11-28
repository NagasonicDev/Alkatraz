package me.nagasonic.alkatraz.spells.domains;

import java.util.ArrayList;
import java.util.List;

/** Pure radial math shared by domain expansion and opposing-border handling. */
public final class DomainGeometry {

    private DomainGeometry() {
    }

    /** A horizontal column coordinate. {@code y} is kept for tile snapshots. */
    public record Tile(int x, int y, int z) {
    }

    /** Result of arbitrating one tile between two active domain fronts. */
    public enum Claim {
        SELF, OTHER, BORDER, FREE
    }

    /**
     * Resolves one tile against a rival domain. Exact ties become neutral border
     * tiles; the strictly closer front claims it. Tiles beyond both radii are free.
     */
    public static Claim claim(double distanceSelf, double distanceOther,
                              int radiusSelf, int radiusOther, double epsilon) {
        if (distanceSelf > radiusSelf) return Claim.FREE;
        if (distanceOther > radiusOther) return Claim.SELF;
        if (isContested(distanceSelf, distanceOther, epsilon)) return Claim.BORDER;
        return distanceSelf < distanceOther ? Claim.SELF : Claim.OTHER;
    }

    /**
     * One square ring of a radial disc, ordered deterministically by x then z.
     * Ring 1 is the eight neighbours around the origin.
     */
    public static List<Tile> ring(int centerX, int centerY, int centerZ, int radius) {
        List<Tile> tiles = new ArrayList<>();
        if (radius < 1) return tiles;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == radius) {
                    tiles.add(new Tile(centerX + dx, centerY, centerZ + dz));
                }
            }
        }
        return tiles;
    }

    /** Exact ties and near-ties at a shared front are claimed by neither domain. */
    public static boolean isContested(double distanceA, double distanceB, double epsilon) {
        return Math.abs(distanceA - distanceB) <= epsilon;
    }

    /** Opposing elements dampen effects only inside the configured border band. */
    public static double dampen(double distanceToBorder, double band, double factor) {
        return distanceToBorder <= band ? factor : 1.0;
    }
}
