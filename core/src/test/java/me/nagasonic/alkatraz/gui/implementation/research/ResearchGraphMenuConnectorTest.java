package me.nagasonic.alkatraz.gui.implementation.research;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResearchGraphMenuConnectorTest {

    private static Method declared(String name, Class<?>... params) throws Exception {
        Method method = ResearchGraphMenu.class.getDeclaredMethod(name, params);
        method.setAccessible(true);
        return method;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    private static int mask(Set<Long> cells, int x, int y) {
        int mask = 0;
        if (cells.contains(key(x, y - 1))) mask |= 1;
        if (cells.contains(key(x, y + 1))) mask |= 2;
        if (cells.contains(key(x + 1, y))) mask |= 4;
        if (cells.contains(key(x - 1, y))) mask |= 8;
        return mask;
    }

    private static String pieceName(Method pieceFor, int mask) throws Exception {
        Object piece = pieceFor.invoke(null, mask);
        return piece == null ? null : ((Enum<?>) piece).name();
    }

    @Test
    void diagonalSegmentBecomesOrthogonalRunWithCornerAtBend() throws Exception {
        Method addPath = declared("addPath", int[].class, int[].class, Set.class, Map.class, Set.class, String.class);
        Method pieceFor = declared("pieceFor", int.class);

        Set<Long> cells = new LinkedHashSet<>();
        addPath.invoke(null, new int[]{0, 0}, new int[]{3, 2}, cells, new HashMap<>(), new HashSet<>(), "locked");

        long[] previous = null;
        for (long cell : cells) {
            long[] current = {(int) (cell >> 32), (int) cell};
            if (previous != null) {
                assertEquals(1, Math.abs(current[0] - previous[0]) + Math.abs(current[1] - previous[1]),
                        "no diagonal step between consecutive path cells");
            }
            previous = current;
        }
        assertEquals(6, cells.size());

        assertEquals("STRAIGHT_H", pieceName(pieceFor, mask(cells, 1, 0)));
        assertEquals("CORNER_SW", pieceName(pieceFor, mask(cells, 3, 0)));
        assertEquals("STRAIGHT_V", pieceName(pieceFor, mask(cells, 3, 1)));
    }

    @Test
    void neighbourPairsSelectExactConnectorTexture() throws Exception {
        Method pieceFor = declared("pieceFor", int.class);

        assertEquals("STRAIGHT_V", pieceName(pieceFor, 1 | 2));
        assertEquals("STRAIGHT_H", pieceName(pieceFor, 4 | 8));
        assertEquals("CORNER_NE", pieceName(pieceFor, 1 | 4));
        assertEquals("CORNER_SE", pieceName(pieceFor, 2 | 4));
        assertEquals("CORNER_SW", pieceName(pieceFor, 2 | 8));
        assertEquals("CORNER_NW", pieceName(pieceFor, 1 | 8));
        assertNull(pieceFor.invoke(null, 0));
    }

    @Test
    void overlappingEdgesKeepFirstState() throws Exception {
        Method addPath = declared("addPath", int[].class, int[].class, Set.class, Map.class, Set.class, String.class);

        Set<Long> cells = new HashSet<>();
        Map<Long, String> states = new HashMap<>();
        addPath.invoke(null, new int[]{0, 0}, new int[]{1, 1}, cells, states, new HashSet<>(), "completed");
        addPath.invoke(null, new int[]{1, 1}, new int[]{2, 2}, cells, states, new HashSet<>(), "locked");

        assertEquals(5, cells.size());
        assertEquals("completed", states.get(key(1, 1)));
    }
}
