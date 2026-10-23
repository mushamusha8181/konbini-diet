package com.local.convenidiet;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class FavoriteStore {
    private static final String PREFS = "favorite_sets";
    private final SharedPreferences p;

    FavoriteStore(Context context) { p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static final class Favorite {
        final String id, name, store, text;
        final List<String> productIds;
        Favorite(String id, String name, String store, String text, List<String> productIds) {
            this.id = id; this.name = name; this.store = store; this.text = text; this.productIds = productIds;
        }
    }

    List<Favorite> all() {
        ArrayList<Favorite> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("items", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i); if (o == null) continue;
                ArrayList<String> ids = new ArrayList<>();
                JSONArray ja = o.optJSONArray("product_ids");
                if (ja != null) for (int j = 0; j < ja.length(); j++) {
                    String id = ja.optString(j, ""); if (!TextUtil.isBlank(id)) ids.add(id);
                }
                out.add(new Favorite(o.optString("id", UUID.randomUUID().toString()),
                        o.optString("name", "お気に入り"), o.optString("store", "指定なし"),
                        o.optString("text", ""), ids));
            }
        } catch (Exception ignored) {}
        return out;
    }

    void add(String name, String store, String text, List<String> productIds) {
        List<Favorite> items = all();
        items.add(new Favorite(UUID.randomUUID().toString(), safeName(name), TextUtil.trimTo(store, 80),
                TextUtil.trimTo(text, 20000), new ArrayList<>(productIds)));
        save(items);
    }

    void remove(String id) {
        List<Favorite> items = all(); items.removeIf(item -> item.id.equals(id)); save(items);
    }

    private void save(List<Favorite> items) {
        JSONArray a = new JSONArray();
        for (Favorite item : items) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", item.id); o.put("name", item.name); o.put("store", item.store); o.put("text", item.text);
                JSONArray ids = new JSONArray(); for (String id : item.productIds) ids.put(id); o.put("product_ids", ids);
            } catch (Exception ignored) {}
            a.put(o);
        }
        p.edit().putString("items", a.toString()).apply();
    }

    private static String safeName(String s) {
        String out = TextUtil.trimTo(s, 80); return TextUtil.isBlank(out) ? "お気に入り" : out;
    }
}
