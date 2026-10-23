package com.local.convenidiet;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

final class DeepSeekClient {
    private final UsageStore usage;

    DeepSeekClient(Context context) {
        usage = new UsageStore(context);
    }

    private static final String HOST = "api.deepseek.com";
    private static final String CHAT_ENDPOINT = "https://api.deepseek.com/chat/completions";
    private static final String SEARCH_ENDPOINT = "https://api.deepseek.com/v1/tools/search";
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    static final class SearchResult {
        final String title, url, snippet, summary, content, siteName;
        SearchResult(String title, String url, String snippet, String summary, String content, String siteName) {
            this.title = title; this.url = url; this.snippet = snippet; this.summary = summary;
            this.content = content; this.siteName = siteName;
        }
    }

    String ask(String apiKey, String model, boolean thinking, String systemPrompt, String userPrompt) throws Exception {
        return askInternal(apiKey, model, thinking, systemPrompt, userPrompt, false, 2600);
    }

    String askJson(String apiKey, String model, String systemPrompt, String userPrompt) throws Exception {
        return askInternal(apiKey, model, false, systemPrompt, userPrompt, true, 6000);
    }

    private String askInternal(String apiKey, String model, boolean thinking, String systemPrompt, String userPrompt,
                               boolean jsonMode, int maxTokens) throws Exception {
        validateModel(model);
        validateKey(apiKey);
        if (userPrompt == null || userPrompt.length() > 180_000) throw new IllegalArgumentException("入力が長すぎます");

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", false);
        body.put("max_tokens", maxTokens);
        body.put("thinking", new JSONObject().put("type", thinking ? "enabled" : "disabled"));
        if (thinking) body.put("reasoning_effort", "high");
        if (jsonMode) body.put("response_format", new JSONObject().put("type", "json_object"));

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
        messages.put(new JSONObject().put("role", "user").put("content", userPrompt));
        body.put("messages", messages);

        JSONObject root = postJson(CHAT_ENDPOINT, apiKey, body, 15_000, 100_000);
        usage.recordChat(root.optString("model", model), root);
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) throw new IllegalStateException("AIの回答が空です");
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        String content = message == null ? null : message.optString("content", null);
        if (TextUtil.isBlank(content)) throw new IllegalStateException("AIの最終回答が空です");
        return content.trim();
    }

    List<SearchResult> search(String apiKey, String query, int count, boolean needContent) throws Exception {
        validateKey(apiKey);
        String clean = TextUtil.trimTo(query, 128);
        if (TextUtil.isBlank(clean)) throw new IllegalArgumentException("検索語がありません");
        int safeCount = Math.max(1, Math.min(50, count));
        JSONObject body = new JSONObject();
        body.put("query", clean);
        body.put("count", safeCount);
        body.put("need_content", needContent);
        JSONObject root = postJson(SEARCH_ENDPOINT, apiKey, body, 15_000, 60_000);
        JSONArray a = root.optJSONArray("results");
        ArrayList<SearchResult> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i); if (o == null) continue;
            out.add(new SearchResult(o.optString("title", ""), o.optString("url", ""),
                    o.optString("snippet", ""), o.optString("summary", ""), o.optString("content", ""),
                    o.optString("site_name", "")));
        }
        if (!out.isEmpty()) usage.recordSearchSuccess();
        return out;
    }

    String test(String apiKey, String model) throws Exception {
        String result = ask(apiKey, model, false,
                "Return only the Japanese word 接続OK. Do not add anything else.", "接続確認");
        return result.length() > 40 ? result.substring(0, 40) : result;
    }

    private JSONObject postJson(String endpoint, String apiKey, JSONObject body, int connectTimeout, int readTimeout) throws Exception {
        URL url = new URL(endpoint);
        if (!"https".equalsIgnoreCase(url.getProtocol()) || !HOST.equalsIgnoreCase(url.getHost())) {
            throw new SecurityException("通信先が不正です");
        }
        HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod("POST");
        c.setConnectTimeout(connectTimeout);
        c.setReadTimeout(readTimeout);
        c.setDoOutput(true);
        c.setUseCaches(false);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + apiKey);

        byte[] requestBytes = body.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(requestBytes.length);
        try (OutputStream out = c.getOutputStream()) { out.write(requestBytes); }

        int status = c.getResponseCode();
        if (status >= 300 && status < 400) {
            c.disconnect();
            throw new SecurityException("リダイレクトを拒否しました");
        }
        InputStream source = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
        String response = source == null ? "" : readBounded(source);
        c.disconnect();
        if (status < 200 || status >= 300) throw new ApiException(status, extractErrorMessage(response));
        if (TextUtil.isBlank(response)) throw new IllegalStateException("API応答が空です");
        return new JSONObject(response);
    }

    private void validateModel(String model) {
        if (!"deepseek-flash".equals(model) && !"deepseek-v4-pro".equals(model)) {
            throw new IllegalArgumentException("許可されていないモデルです");
        }
    }

    private void validateKey(String apiKey) {
        if (TextUtil.isBlank(apiKey)) throw new IllegalArgumentException("APIキーがありません");
        if (apiKey.length() > 512) throw new IllegalArgumentException("APIキーが長すぎます");
    }

    private String readBounded(InputStream in) throws Exception {
        try (InputStream source = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int total = 0; int n;
            while ((n = source.read(buf)) != -1) {
                total += n;
                if (total > MAX_RESPONSE_BYTES) throw new IllegalStateException("API応答が大きすぎます");
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private String extractErrorMessage(String response) {
        try {
            JSONObject root = new JSONObject(response);
            JSONObject error = root.optJSONObject("error");
            String msg = error == null ? null : error.optString("message", null);
            if (!TextUtil.isBlank(msg)) return sanitize(msg);
        } catch (Exception ignored) {}
        return "APIからエラーが返されました";
    }

    private String sanitize(String s) {
        String clean = s.replaceAll("[\\r\\n\\t]+", " ").trim();
        return clean.length() > 300 ? clean.substring(0, 300) : clean;
    }

    static final class ApiException extends Exception {
        final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
    }
}
