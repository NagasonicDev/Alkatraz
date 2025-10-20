package me.nagasonic.alkatraz.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorFormat {
    private static final Pattern HEX_PATTERN = Pattern.compile("#([A-Fa-f0-9]{6})");

    public static String format(String s) {
        if (s == null) return null;

        String result = s;

        // 1️⃣ Handle hex color codes (#RRGGBB → §x§R§R§G§G§B§B)
        Matcher matcher = HEX_PATTERN.matcher(result);
        while (matcher.find()) {
            String hexCode = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hexCode.toCharArray()) {
                replacement.append('§').append(c);
            }
            result = result.replace("#" + hexCode, replacement.toString());
        }

        // 2️⃣ Handle legacy color codes (&a → §a)
        result = result.replaceAll("&([0-9a-fklmnorA-FKLMNOR])", "§$1");

        return result;
    }
}
