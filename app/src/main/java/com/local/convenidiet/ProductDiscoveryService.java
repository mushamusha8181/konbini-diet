package com.local.convenidiet;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ProductDiscoveryService {
    private final DeepSeekClient client;

    ProductDiscoveryService(Context context) {
        client = new DeepSeekClient(context);
    }

    static final class RefreshResult {
        final int searched, accepted;
        RefreshResult(int searched, int accepted) { this.searched = searched; this.accepted = accepted; }
    }

    RefreshResult refresh(String apiKey, String model, String store, ProductCatalog catalog) throws Exception {
        StoreSource source = source(store);
        if (source == null) return new RefreshResult(0, 0);
        List<DeepSeekClient.SearchResult> results = client.search(apiKey, source.query, 30, true);
        if (results.isEmpty()) return new RefreshResult(0, 0);

        ArrayList<DeepSeekClient.SearchResult> trusted = new ArrayList<>();
        Map<String, String> urls = new HashMap<>();
        for (DeepSeekClient.SearchResult r : results) {
            if (isAllowedUrl(r.url, source.allowedDomains)) {
                trusted.add(r); urls.put(canonicalUrl(r.url), r.url);
            }
        }
        if (trusted.isEmpty()) return new RefreshResult(results.size(), 0);

        String sources = buildSources(trusted);
        String system = "あなたは日本のコンビニ公式商品ページから構造化データを抽出する処理です。必ずJSONだけを返してください。" +
                "推測・補完は禁止です。ページに明記されない数値はnullにしてください。糖質と糖類を混同しないでください。" +
                "source_urlは入力に存在するURLをそのまま使ってください。商品名が確認できない項目は出力しないでください。";
        String user = "次の公式サイト検索結果から、コンビニ『" + store + "』の商品を抽出してください。JSON形式は必ず " +
                "{\"products\":[{\"name\":\"商品名\",\"price_yen\":123.0,\"calories\":100.0,\"protein\":10.0," +
                "\"fat\":1.0,\"carbs\":20.0,\"sugar\":null,\"fiber\":null,\"salt\":1.0,\"region\":\"販売地域\"," +
                "\"source_url\":\"入力にあるURL\",\"nutrition_text\":\"確認できた栄養表示の要約\"}]} としてください。" +
                "\n糖質または糖類が明記されている場合だけsugarへ入れ、どちらも無ければnull。価格は税込価格を優先。" +
                "\n\n【公式検索結果】\n" + sources;

        String json = client.askJson(apiKey, model, system, user);
        JSONObject root = new JSONObject(json);
        JSONArray products = root.optJSONArray("products");
        if (products == null) return new RefreshResult(trusted.size(), 0);
        int accepted = 0;
        for (int i = 0; i < products.length(); i++) {
            JSONObject o = products.optJSONObject(i); if (o == null) continue;
            String name = TextUtil.trimTo(o.optString("name", ""), 160);
            String sourceUrl = TextUtil.trimTo(o.optString("source_url", ""), 1000);
            String originalUrl = urls.get(canonicalUrl(sourceUrl));
            if (TextUtil.isBlank(name) || originalUrl == null || !isAllowedUrl(originalUrl, source.allowedDomains)) continue;
            sourceUrl = originalUrl;
            String idMaterial = store + "\n" + name;
            String id = "web-" + UUID.nameUUIDFromBytes(idMaterial.getBytes(StandardCharsets.UTF_8));
            ProductCatalog.Product p = new ProductCatalog.Product(id, store, name, "",
                    TextUtil.trimTo(o.optString("nutrition_text", ""), 12000),
                    nullable(o, "price_yen"), nullable(o, "calories"), nullable(o, "protein"), nullable(o, "fat"),
                    nullable(o, "carbs"), nullable(o, "sugar"), nullable(o, "fiber"), nullable(o, "salt"),
                    TextUtil.trimTo(o.optString("region", ""), 300), sourceUrl, "deepseek_search_official",
                    LocalDate.now().toString());
            catalog.upsert(p); accepted++;
        }
        return new RefreshResult(trusted.size(), accepted);
    }

    private String buildSources(List<DeepSeekClient.SearchResult> results) {
        StringBuilder b = new StringBuilder(); int i = 1;
        for (DeepSeekClient.SearchResult r : results) {
            b.append("\n--- SOURCE ").append(i++).append(" ---\nURL: ").append(r.url)
                    .append("\nTITLE: ").append(r.title).append("\nSUMMARY: ").append(r.summary)
                    .append("\nCONTENT:\n");
            String c = TextUtil.isBlank(r.content) ? r.snippet : r.content;
            if (c.length() > 3500) c = c.substring(0, 3500);
            b.append(c).append('\n');
            if (b.length() > 100000) break;
        }
        return b.toString();
    }

    private static Double nullable(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        try {
            double v = o.getDouble(key);
            if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) return null;
            return v;
        } catch (Exception e) { return null; }
    }

    private String canonicalUrl(String raw) {
        if (TextUtil.isBlank(raw)) return "";
        try {
            URI u = URI.create(raw.trim());
            String host = u.getHost();
            if (host == null) return "";
            String path = u.getPath() == null ? "" : u.getPath();
            while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return (u.getScheme() == null ? "https" : u.getScheme().toLowerCase()) + "://" + host.toLowerCase() + path;
        } catch (Exception e) { return ""; }
    }

    private boolean isAllowedUrl(String raw, String[] domains) {
        if (TextUtil.isBlank(raw)) return false;
        try {
            URI u = URI.create(raw);
            if (!"https".equalsIgnoreCase(u.getScheme())) return false;
            String host = u.getHost(); if (host == null) return false;
            host = host.toLowerCase();
            for (String d : domains) if (host.equals(d) || host.endsWith("." + d)) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private StoreSource source(String store) {
        if ("セブン-イレブン".equals(store)) return new StoreSource(
                "site:sej.co.jp/products/a/item/ 商品 栄養成分 食塩相当量", new String[]{"sej.co.jp"});
        if ("ファミリーマート".equals(store)) return new StoreSource(
                "site:family.co.jp/goods/ 商品 栄養成分 食塩相当量", new String[]{"family.co.jp"});
        if ("ローソン".equals(store)) return new StoreSource(
                "site:lawson.co.jp/recommend/original/ 商品 kcal 税込", new String[]{"lawson.co.jp"});
        if ("ミニストップ".equals(store)) return new StoreSource(
                "site:ministop.co.jp/syohin/ 商品 栄養成分 kcal", new String[]{"ministop.co.jp"});
        return null;
    }

    private static final class StoreSource {
        final String query; final String[] allowedDomains;
        StoreSource(String query, String[] allowedDomains) { this.query = query; this.allowedDomains = allowedDomains; }
    }
}
