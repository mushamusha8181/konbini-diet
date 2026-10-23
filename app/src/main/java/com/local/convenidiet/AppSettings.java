package com.local.convenidiet;

import android.content.Context;
import android.content.SharedPreferences;

final class AppSettings {
    private static final String PREFS = "local_settings";
    private final SharedPreferences p;

    AppSettings(Context context) {
        p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String model() { return p.getString("model", "deepseek-flash"); }
    void setModel(String value) { p.edit().putString("model", value).apply(); }

    boolean thinking() { return p.getBoolean("thinking", false); }
    void setThinking(boolean value) { p.edit().putBoolean("thinking", value).apply(); }

    String calories() { return p.getString("calories", ""); }
    void setCalories(String value) { p.edit().putString("calories", value).apply(); }

    String protein() { return p.getString("protein", ""); }
    void setProtein(String value) { p.edit().putString("protein", value).apply(); }

    String saltTarget() { return p.getString("salt_target", ""); }
    void setSaltTarget(String value) { p.edit().putString("salt_target", value).apply(); }

    String sugarTarget() { return p.getString("sugar_target", ""); }
    void setSugarTarget(String value) { p.edit().putString("sugar_target", value).apply(); }

    String budget() { return p.getString("budget", ""); }
    void setBudget(String value) { p.edit().putString("budget", value).apply(); }

    String concern() { return p.getString("concern", "特に気にしない"); }
    void setConcern(String value) { p.edit().putString("concern", value).apply(); }

    String goalNote() { return p.getString("goal_note", ""); }
    void setGoalNote(String value) { p.edit().putString("goal_note", value).apply(); }

    String avoidFoods() { return p.getString("avoid_foods", ""); }
    void setAvoidFoods(String value) { p.edit().putString("avoid_foods", value).apply(); }


    String usdJpyRate() { return p.getString("usd_jpy_rate", "150.0"); }
    void setUsdJpyRate(String value) { p.edit().putString("usd_jpy_rate", value).apply(); }

    boolean initialPermissionAsked() { return p.getBoolean("initial_permission_asked", false); }
    void setInitialPermissionAsked(boolean value) { p.edit().putBoolean("initial_permission_asked", value).apply(); }
}
