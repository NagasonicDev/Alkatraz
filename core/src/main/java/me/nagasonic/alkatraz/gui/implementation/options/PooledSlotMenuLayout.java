package me.nagasonic.alkatraz.gui.implementation.options;

import java.util.HashSet;
import java.util.Set;

/**
 * Computes symmetrical slot-header positions for {@link PooledSlotSelectionMenu}.
 *
 * <p>Within a row, slots are laid out as {@code X S X S X ...} — border columns
 * between each slot, centered with equal padding on both ends. Examples (row 1,
 * columns 0–8):
 * <ul>
 *   <li>3 slots: {@code X X S X S X S X X} → inventory slots 11, 13, 15</li>
 *   <li>4 slots: {@code X S X S X S X S X} → 10, 12, 14, 16</li>
 * </ul>
 *
 * <p>More than five slots uses a second header row (row 2), each row centered
 * independently (e.g. 7 slots → 4 on row 1, 3 on row 2).
 */
public final class PooledSlotMenuLayout {

    public static final int ROW_WIDTH = 9;
    public static final int MAX_MENU_SLOTS = 7;

    /** Maximum slots that fit on one inventory row with border gaps. */
    public static final int MAX_SLOTS_PER_ROW = (ROW_WIDTH + 1) / 2;

    private PooledSlotMenuLayout() {}

    /**
     * @param slotCount Number of slot headers (1–{@link #MAX_MENU_SLOTS})
     * @return Inventory indices for each slot header, in display order
     */
    public static int[] computeHeaderSlots(int slotCount) {
        slotCount = clampSlotCount(slotCount);
        if (slotCount <= MAX_SLOTS_PER_ROW) {
            return positionsInRow(slotCount, 1);
        }

        int row1Count = (slotCount + 1) / 2;
        int row2Count = slotCount - row1Count;
        int[] row1 = positionsInRow(row1Count, 1);
        int[] row2 = positionsInRow(row2Count, 2);
        int[] combined = new int[slotCount];
        System.arraycopy(row1, 0, combined, 0, row1.length);
        System.arraycopy(row2, 0, combined, row1.length, row2.length);
        return combined;
    }

    /**
     * All inventory indices that should show border panes, excluding reserved slots.
     */
    public static Set<Integer> borderSlotsExcluding(Set<Integer> reserved) {
        Set<Integer> borders = new HashSet<>();
        for (int i = 0; i < 54; i++) {
            if (!reserved.contains(i)) {
                borders.add(i);
            }
        }
        return borders;
    }

    public static int clampSlotCount(int slotCount) {
        return Math.max(1, Math.min(slotCount, MAX_MENU_SLOTS));
    }

    /**
     * Centered {@code X S X S ...} positions for {@code slotCount} headers in one row.
     */
    static int[] positionsInRow(int slotCount, int row) {
        int span = 2 * slotCount - 1;
        int startCol = (ROW_WIDTH - span) / 2;
        int rowBase = row * ROW_WIDTH;

        int[] positions = new int[slotCount];
        for (int i = 0; i < slotCount; i++) {
            positions[i] = rowBase + startCol + (i * 2);
        }
        return positions;
    }

}
