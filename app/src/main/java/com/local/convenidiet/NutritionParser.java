package com.local.convenidiet;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NutritionParser {
    private NutritionParser() {}

    static ProductCatalog.Nutrition parse(String text) {
        String s = text == null ? "" : text.replace('：', ':').replace('，', ',');
        ProductCatalog.Nutrition n = new ProductCatalog.Nutrition();
        n.calories = find(s, "(?:熱量|エネルギー)\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*k?cal");
        n.protein = find(s, "たんぱく質\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*g");
        n.fat = find(s, "脂質\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*g");
        n.carbs = find(s, "炭水化物\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*g");
        // 糖類と糖質は意味が違う。アプリ内の sugar は「ユーザーが管理対象として選ぶ糖質/糖類の表示値」。
        Double sugars = find(s, "糖類\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*g");
        Double carbsSugar = find(s, "糖質\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*g");
        n.sugar = sugars != null ? sugars : carbsSugar;
        n.fiber = find(s, "食物繊維\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*g");
        n.salt = find(s, "食塩相当量\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*g");
        return n;
    }

    static String summarize(ProductCatalog.Product p) {
        StringBuilder b = new StringBuilder();
        append(b, "熱量", p.calories, "kcal");
        append(b, "たんぱく質", p.protein, "g");
        append(b, "脂質", p.fat, "g");
        append(b, "炭水化物", p.carbs, "g");
        append(b, "糖質/糖類", p.sugar, "g");
        append(b, "食物繊維", p.fiber, "g");
        append(b, "食塩相当量", p.salt, "g");
        if (b.length() == 0 && !TextUtil.isBlank(p.nutritionText)) return p.nutritionText;
        return b.toString();
    }

    private static Double find(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        if (!m.find()) return null;
        try { return Double.valueOf(m.group(1)); }
        catch (Exception e) { return null; }
    }

    private static void append(StringBuilder b, String label, Double value, String unit) {
        if (value == null) return;
        if (b.length() > 0) b.append(" / ");
        b.append(label).append(' ').append(format(value)).append(unit);
    }

    static String format(Double value) {
        if (value == null) return "不明";
        if (Math.abs(value - Math.rint(value)) < 0.005) return String.format(Locale.JAPAN, "%.0f", value);
        return String.format(Locale.JAPAN, "%.1f", value);
    }
}
