package me.nagasonic.alkatraz.api.gui.implementation.options;

import java.util.HashSet;
import java.util.Set;

/**
 * Computes symmetrical slot-header positions for a pooled slot selection menu.
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

    /** Number of columns in a single Minecraft inventory row. */
    public static final int ROW_WIDTH = 9;

    /** Maximum number of pooled slot headers that can be displayed in a single menu. */
    public static final int MAX_MENU_SLOTS = 7;

    /** Maximum slots that fit on one inventory row with border gaps. */
    public static final int MAX_SLOTS_PER_ROW = (ROW_WIDTH + 1) / 2;

    private PooledSlotMenuLayout() {}

    /**
     * Computes the inventory slot indices where slot headers should be placed,
     * centering them symmetrically across one or two rows as needed.
     *
     * @param slotCount Number of slot headers to lay out (1–{@link #MAX_MENU_SLOTS}).
     *                  Values outside this range are clamped automatically.
     * @return An array of inventory slot indices for each header, in display order
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
     * Returns all inventory indices (0–53) that should display border panes,
     * excluding the given reserved slots.
     *
     * @param reserved A set of inventory indices that are already occupied
     *                 and should not receive border panes
     * @return A set of inventory indices available for border pane placement
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

    /**
     * Clamps the given slot count to the valid range of 1–{@link #MAX_MENU_SLOTS}.
     *
     * @param slotCount The raw slot count to clamp
     * @return A value between 1 and {@link #MAX_MENU_SLOTS}, inclusive
     */
    public static int clampSlotCount(int slotCount) {
        return Math.max(1, Math.min(slotCount, MAX_MENU_SLOTS));
    }

    /**
     * Computes centered {@code X S X S ...} positions for a given number of
     * slot headers in a single inventory row.
     *
     * @param slotCount Number of slot headers to place in this row
     * @param row       The zero-indexed row number (0–4) within the inventory
     * @return An array of inventory slot indices for the computed positions
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
