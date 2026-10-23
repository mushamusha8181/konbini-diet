package com.local.convenidiet;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.util.Locale;

final class DailyIntakeStore {
    private static final String PREFS = "daily_intake";
    private final SharedPreferences p;

    DailyIntakeStore(Context context) {
        p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureToday();
    }

    void add(Double calories, Double protein, Double salt, Double sugar) {
        ensureToday();
        SharedPreferences.Editor e = p.edit();
        addMetric(e, "calories", calories);
        addMetric(e, "protein", protein);
        addMetric(e, "salt", salt);
        addMetric(e, "sugar", sugar);
        e.apply();
    }

    void addProduct(ProductCatalog.Product product) {
        add(product.calories, product.protein, product.salt, product.sugar);
    }

    void reset() { p.edit().clear().putString("date", LocalDate.now().toString()).apply(); }

    double calories() { ensureToday(); return p.getFloat("calories", 0f); }
    double protein() { ensureToday(); return p.getFloat("protein", 0f); }
    double salt() { ensureToday(); return p.getFloat("salt", 0f); }
    double sugar() { ensureToday(); return p.getFloat("sugar", 0f); }

    String summary(String calTarget, String proteinTarget, String saltTarget, String sugarTarget) {
        StringBuilder b = new StringBuilder();
        b.append("今日の記録  ")
                .append(metricText("calories", calories(), "kcal"))
                .append(" / ").append(metricText("protein", protein(), "gたんぱく質"))
                .append(" / ").append(metricText("salt", salt(), "g塩分"))
                .append(" / ").append(metricText("sugar", sugar(), "g糖質/糖類"));
        b.append("\n残り目安  ")
                .append(remainText("calories", calTarget, calories(), "kcal"))
                .append(" / ").append(remainText("protein", proteinTarget, protein(), "gたんぱく質"))
                .append(" / ").append(remainText("salt", saltTarget, salt(), "g塩分"))
                .append(" / ").append(remainText("sugar", sugarTarget, sugar(), "g糖質/糖類"));
        return b.toString();
    }

    String promptRemaining(String calTarget, String proteinTarget, String saltTarget, String sugarTarget) {
        return "今日ここまで: " + metricText("calories", calories(), "kcal") + ", " +
                metricText("protein", protein(), "gたんぱく質") + ", " + metricText("salt", salt(), "g塩分") + ", " +
                metricText("sugar", sugar(), "g糖質/糖類") + "。\n今日の残り目安: " +
                remainText("calories", calTarget, calories(), "kcal") + ", " +
                remainText("protein", proteinTarget, protein(), "gたんぱく質") + ", " +
                remainText("salt", saltTarget, salt(), "g塩分") + ", " +
                remainText("sugar", sugarTarget, sugar(), "g糖質/糖類") + "。";
    }

    private void addMetric(SharedPreferences.Editor e, String key, Double value) {
        if (value == null) {
            e.putInt(key + "_unknown", p.getInt(key + "_unknown", 0) + 1);
            return;
        }
        if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) return;
        e.putFloat(key, p.getFloat(key, 0f) + value.floatValue());
    }

    private String metricText(String key, double value, String unit) {
        int unknown = p.getInt(key + "_unknown", 0);
        return format(value) + unit + (unknown > 0 ? "（不明" + unknown + "件）" : "");
    }

    private String remainText(String key, String targetText, double consumed, String unit) {
        double target = parse(targetText);
        if (target <= 0) return "未設定";
        int unknown = p.getInt(key + "_unknown", 0);
        if (unknown > 0) return "算出不可（不明" + unknown + "件）";
        return format(Math.max(0, target - consumed)) + unit;
    }

    private void ensureToday() {
        String today = LocalDate.now().toString();
        String saved = p.getString("date", "");
        if (!today.equals(saved)) reset();
    }

    private static double parse(String s) {
        try { return Double.parseDouble(s == null ? "" : s.trim()); }
        catch (Exception e) { return 0; }
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.01) return String.format(Locale.JAPAN, "%.0f", value);
        return String.format(Locale.JAPAN, "%.1f", value);
    }
}
