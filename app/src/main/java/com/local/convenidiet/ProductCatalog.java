package com.local.convenidiet;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class ProductCatalog extends SQLiteOpenHelper {
    private static final String DB = "products.db";
    private static final int VERSION = 2;
    private static final String TABLE = "products";
    private static final String META = "catalog_meta";
    private static final int SEED_VERSION = 2;
    private final Context context;

    static final class Nutrition {
        Double calories, protein, fat, carbs, sugar, fiber, salt;
    }

    static final class Product {
        final String id, store, name, barcode, nutritionText, region, sourceUrl, sourceKind, verifiedAt;
        final Double priceYen, calories, protein, fat, carbs, sugar, fiber, salt;

        Product(String id, String store, String name, String barcode, String nutritionText,
                Double priceYen, Double calories, Double protein, Double fat, Double carbs,
                Double sugar, Double fiber, Double salt, String region, String sourceUrl,
                String sourceKind, String verifiedAt) {
            this.id = id; this.store = store; this.name = name; this.barcode = barcode;
            this.nutritionText = nutritionText; this.priceYen = priceYen; this.calories = calories;
            this.protein = protein; this.fat = fat; this.carbs = carbs; this.sugar = sugar;
            this.fiber = fiber; this.salt = salt; this.region = region; this.sourceUrl = sourceUrl;
            this.sourceKind = sourceKind; this.verifiedAt = verifiedAt;
        }
    }

    ProductCatalog(Context context) {
        super(context.getApplicationContext(), DB, null, VERSION);
        this.context = context.getApplicationContext();
        SQLiteDatabase db = getWritableDatabase();
        importSeedIfNeeded(db);
        migrateLegacyIfNeeded(db);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                "id TEXT PRIMARY KEY, store TEXT NOT NULL, name TEXT NOT NULL, barcode TEXT, nutrition_text TEXT," +
                "price_yen REAL, calories REAL, protein REAL, fat REAL, carbs REAL, sugar REAL, fiber REAL, salt REAL," +
                "region TEXT, source_url TEXT, source_kind TEXT, verified_at TEXT, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX idx_products_barcode ON " + TABLE + "(barcode) WHERE barcode IS NOT NULL AND barcode <> ''");
        db.execSQL("CREATE INDEX idx_products_store ON " + TABLE + "(store)");
        db.execSQL("CREATE INDEX idx_products_name ON " + TABLE + "(name)");
        db.execSQL("CREATE TABLE " + META + " (k TEXT PRIMARY KEY, v TEXT)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // VERSION 2 is the first SQLite schema shipped by this project. Defensive recreate for dev builds.
            db.execSQL("DROP TABLE IF EXISTS " + TABLE);
            db.execSQL("DROP TABLE IF EXISTS " + META);
            onCreate(db);
        }
    }

    List<Product> all() { return query(null, null, "store, name"); }

    List<Product> forStore(String store) {
        if (TextUtil.isBlank(store) || "指定なし".equals(store)) return all();
        return query("store=?", new String[]{store}, "name");
    }


    Product findById(String id) {
        if (TextUtil.isBlank(id)) return null;
        List<Product> found = query("id=?", new String[]{id}, null);
        return found.isEmpty() ? null : found.get(0);
    }

    Product findBarcode(String barcode) {
        if (TextUtil.isBlank(barcode)) return null;
        List<Product> found = query("barcode=?", new String[]{barcode.trim()}, null);
        return found.isEmpty() ? null : found.get(0);
    }

    void addManual(String store, String name, String barcode, String nutritionText) {
        Nutrition n = NutritionParser.parse(nutritionText);
        Product p = new Product(UUID.randomUUID().toString(), cleanStore(store), safe(name, 160), safe(barcode, 80),
                safe(nutritionText, 12000), null, n.calories, n.protein, n.fat, n.carbs, n.sugar, n.fiber, n.salt,
                "", "", "manual", LocalDate.now().toString());
        upsert(p);
    }

    void upsert(Product p) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = values(p);
        if (!TextUtil.isBlank(p.barcode)) {
            db.delete(TABLE, "barcode=? AND id<>?", new String[]{p.barcode, p.id});
        }
        if (!"manual".equals(p.sourceKind)) {
            // Search results can point at both a category page and a detail page for the same product.
            // Keep one official/search row per store+name while preserving user-entered rows.
            db.delete(TABLE, "store=? AND name=? AND id<>? AND source_kind<>'manual'", new String[]{p.store, p.name, p.id});
        }
        db.insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void remove(String id) { getWritableDatabase().delete(TABLE, "id=?", new String[]{id}); }

    int size() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    int countForStore(String store) {
        if (TextUtil.isBlank(store) || "指定なし".equals(store)) return size();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE + " WHERE store=?", new String[]{store})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    String promptCatalog(String store) {
        List<Product> products = forStore(store);
        if (products.isEmpty()) return "登録商品なし";
        StringBuilder b = new StringBuilder();
        int index = 1;
        for (Product p : products) {
            b.append(index++).append(". ").append(p.name).append(" / コンビニ: ").append(p.store);
            b.append(" / 価格: ").append(p.priceYen == null ? "不明" : NutritionParser.format(p.priceYen) + "円");
            b.append(" / kcal: ").append(NutritionParser.format(p.calories));
            b.append(" / たんぱく質: ").append(NutritionParser.format(p.protein)).append("g");
            b.append(" / 糖質・糖類: ").append(NutritionParser.format(p.sugar)).append("g");
            b.append(" / 食塩相当量: ").append(NutritionParser.format(p.salt)).append("g");
            if (!TextUtil.isBlank(p.region)) b.append(" / 販売地域: ").append(p.region);
            if (!TextUtil.isBlank(p.verifiedAt)) b.append(" / 確認日: ").append(p.verifiedAt);
            b.append('\n');
            if (b.length() > 60000) { b.append("（DBが大きいため以降省略）\n"); break; }
        }
        return b.toString();
    }

    private List<Product> query(String where, String[] args, String order) {
        ArrayList<Product> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE, null, where, args, null, null, order)) {
            while (c.moveToNext()) out.add(fromCursor(c));
        }
        return out;
    }

    private Product fromCursor(Cursor c) {
        return new Product(
                str(c, "id"), str(c, "store"), str(c, "name"), str(c, "barcode"), str(c, "nutrition_text"),
                dbl(c, "price_yen"), dbl(c, "calories"), dbl(c, "protein"), dbl(c, "fat"), dbl(c, "carbs"),
                dbl(c, "sugar"), dbl(c, "fiber"), dbl(c, "salt"), str(c, "region"), str(c, "source_url"),
                str(c, "source_kind"), str(c, "verified_at"));
    }

    private static String str(Cursor c, String name) {
        int i = c.getColumnIndexOrThrow(name); return c.isNull(i) ? "" : c.getString(i);
    }
    private static Double dbl(Cursor c, String name) {
        int i = c.getColumnIndexOrThrow(name); return c.isNull(i) ? null : c.getDouble(i);
    }

    private ContentValues values(Product p) {
        ContentValues v = new ContentValues();
        v.put("id", p.id); v.put("store", cleanStore(p.store)); v.put("name", safe(p.name, 160));
        v.put("barcode", safe(p.barcode, 80)); v.put("nutrition_text", safe(p.nutritionText, 12000));
        put(v, "price_yen", p.priceYen); put(v, "calories", p.calories); put(v, "protein", p.protein);
        put(v, "fat", p.fat); put(v, "carbs", p.carbs); put(v, "sugar", p.sugar); put(v, "fiber", p.fiber); put(v, "salt", p.salt);
        v.put("region", safe(p.region, 300)); v.put("source_url", safe(p.sourceUrl, 1000));
        v.put("source_kind", safe(p.sourceKind, 40)); v.put("verified_at", safe(p.verifiedAt, 32));
        v.put("updated_at", System.currentTimeMillis());
        return v;
    }

    private static void put(ContentValues v, String key, Double value) { if (value == null) v.putNull(key); else v.put(key, value); }

    private void importSeedIfNeeded(SQLiteDatabase db) {
        String seed = meta(db, "seed_version");
        if (String.valueOf(SEED_VERSION).equals(seed)) return;
        db.beginTransaction();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(context.getAssets().open("seed_products.json"), StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) json.append(line);
            JSONArray a = new JSONArray(json.toString());
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i); if (o == null) continue;
                Product p = fromJson(o);
                db.insertWithOnConflict(TABLE, null, values(p), SQLiteDatabase.CONFLICT_REPLACE);
            }
            setMeta(db, "seed_version", String.valueOf(SEED_VERSION));
            db.setTransactionSuccessful();
        } catch (Exception e) {
            // Seed failure must not crash the whole app. The UI will still allow live search/manual registration.
        } finally { db.endTransaction(); }
    }

    private Product fromJson(JSONObject o) {
        return new Product(o.optString("id", UUID.randomUUID().toString()), o.optString("store", "指定なし"),
                o.optString("name", "商品"), o.optString("barcode", ""), o.optString("nutrition_text", ""),
                nullable(o, "price_yen"), nullable(o, "calories"), nullable(o, "protein"), nullable(o, "fat"),
                nullable(o, "carbs"), nullable(o, "sugar"), nullable(o, "fiber"), nullable(o, "salt"),
                o.optString("region", ""), o.optString("source_url", ""), o.optString("source_kind", "official_seed"),
                o.optString("verified_at", LocalDate.now().toString()));
    }

    private static Double nullable(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        try { return o.getDouble(key); } catch (Exception e) { return null; }
    }

    private void migrateLegacyIfNeeded(SQLiteDatabase db) {
        if ("1".equals(meta(db, "legacy_migrated"))) return;
        SharedPreferences p = context.getSharedPreferences("product_catalog", Context.MODE_PRIVATE);
        try {
            JSONArray a = new JSONArray(p.getString("products", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i); if (o == null) continue;
                String nutrition = o.optString("nutrition", ""); Nutrition n = NutritionParser.parse(nutrition);
                Product item = new Product(o.optString("id", UUID.randomUUID().toString()), o.optString("store", "指定なし"),
                        o.optString("name", "商品"), o.optString("barcode", ""), nutrition, null,
                        n.calories, n.protein, n.fat, n.carbs, n.sugar, n.fiber, n.salt,
                        "", "", "legacy", LocalDate.now().toString());
                db.insertWithOnConflict(TABLE, null, values(item), SQLiteDatabase.CONFLICT_REPLACE);
            }
            p.edit().clear().apply();
        } catch (Exception ignored) {}
        setMeta(db, "legacy_migrated", "1");
    }

    private static String meta(SQLiteDatabase db, String key) {
        try (Cursor c = db.rawQuery("SELECT v FROM " + META + " WHERE k=?", new String[]{key})) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }
    private static void setMeta(SQLiteDatabase db, String k, String v) {
        ContentValues cv = new ContentValues(); cv.put("k", k); cv.put("v", v);
        db.insertWithOnConflict(META, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static String cleanStore(String s) { return TextUtil.isBlank(s) ? "指定なし" : safe(s, 80); }
    private static String safe(String value, int max) { return TextUtil.trimTo(value, max); }
}
