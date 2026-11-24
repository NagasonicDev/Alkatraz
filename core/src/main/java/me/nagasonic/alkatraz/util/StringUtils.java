package me.nagasonic.alkatraz.util;

import java.util.*;

public class StringUtils {

    private final static TreeMap<Integer, String> romanNumeralsMap = new TreeMap<>();

    static {
        romanNumeralsMap.put(1000, "M");
        romanNumeralsMap.put(900, "CM");
        romanNumeralsMap.put(500, "D");
        romanNumeralsMap.put(400, "CD");
        romanNumeralsMap.put(100, "C");
        romanNumeralsMap.put(90, "XC");
        romanNumeralsMap.put(50, "L");
        romanNumeralsMap.put(40, "XL");
        romanNumeralsMap.put(10, "X");
        romanNumeralsMap.put(9, "IX");
        romanNumeralsMap.put(5, "V");
        romanNumeralsMap.put(4, "IV");
        romanNumeralsMap.put(1, "I");
    }

    public static String toRoman(int number) {
        if (number < 0) return "-" + toRoman(-number);
        if (number == 0) return "0";
        if (number == 1) return "I";
        int l = romanNumeralsMap.floorKey(number);
        if (number == l) return romanNumeralsMap.get(number);
        return romanNumeralsMap.get(l) + toRoman(number - l);
    }

    public static boolean isEmpty(String str){
        return str == null || str.isEmpty();
    }

    public static String trimTrailingZeroes(String s){
        return !s.contains(".") ? s : s.replaceAll("0*$", "").replaceAll("\\.$", "");
    }

    /**
     * Converts a snake_case registry key to a human-readable Title Case string.
     * Strips namespace prefix if present (e.g. "alkatraz:fire_bolt" → "Fire Bolt").
     */
    public static String prettifyKey(String key) {
        if (key == null) return "";
        int colon = key.indexOf(':');
        if (colon >= 0) key = key.substring(colon + 1);
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" ");
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /**
     * Returns a string with the appropriate English ordinal suffix
     * (e.g., 1 -> "1st", 2 -> "2nd", 3 -> "3rd", 4 -> "4th").
     */
    public static String toOrdinal(int number) {
        int abs = Math.abs(number);
        int mod100 = abs % 100;

        // Special case: 11th, 12th, 13th
        if (mod100 >= 11 && mod100 <= 13) {
            return number + "th";
        }

        // Otherwise, choose based on last digit
        return switch (abs % 10) {
            case 1 -> number + "st";
            case 2 -> number + "nd";
            case 3 -> number + "rd";
            default -> number + "th";
        };
    }
}
