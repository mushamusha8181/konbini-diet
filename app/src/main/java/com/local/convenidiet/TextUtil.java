package com.local.convenidiet;

final class TextUtil {
    private TextUtil() {}

    static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    static String trimTo(String s, int max) {
        if (s == null) return "";
        String out = s.trim();
        return out.length() > max ? out.substring(0, max) : out;
    }
}
