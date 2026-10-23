package com.local.convenidiet;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.time.YearMonth;
import java.util.Locale;

/**
 * Local-only meter for API calls made by this app on this device.
 * This does not read or modify DeepSeek account billing history.
 */
final class UsageStore {
    static final String PRICING_AS_OF = "2026-10-23";
    private static final String PREFS = "deepseek_usage_v1";
    private static final long SEARCH_COST_NANO_USD = 750_000L; // $0.75 / 1000 successful requests.
    private final SharedPreferences p;

    UsageStore(Context context) {
        p = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static final class Snapshot {
        final String month;
        final long cacheHitTokens, cacheMissTokens, outputTokens, reasoningTokens, totalTokens;
        final long chatCalls, searchCalls, fetchCalls;
        final long minCostNanoUsd, maxCostNanoUsd;

        Snapshot(String month, long cacheHitTokens, long cacheMissTokens, long outputTokens,
                 long reasoningTokens, long totalTokens, long chatCalls, long searchCalls,
                 long fetchCalls, long minCostNanoUsd, long maxCostNanoUsd) {
            this.month = month;
            this.cacheHitTokens = cacheHitTokens;
            this.cacheMissTokens = cacheMissTokens;
            this.outputTokens = outputTokens;
            this.reasoningTokens = reasoningTokens;
            this.totalTokens = totalTokens;
            this.chatCalls = chatCalls;
            this.searchCalls = searchCalls;
            this.fetchCalls = fetchCalls;
            this.minCostNanoUsd = minCostNanoUsd;
            this.maxCostNanoUsd = maxCostNanoUsd;
        }

        long inputTokens() { return cacheHitTokens + cacheMissTokens; }
        double minUsd() { return minCostNanoUsd / 1_000_000_000.0; }
        double maxUsd() { return maxCostNanoUsd / 1_000_000_000.0; }
    }

    synchronized void recordChat(String model, JSONObject root) {
        JSONObject usage = root == null ? null : root.optJSONObject("usage");
        if (usage == null) return;

        long prompt = nonNegative(usage.optLong("prompt_tokens", 0));
        boolean hasHit = usage.has("prompt_cache_hit_tokens");
        boolean hasMiss = usage.has("prompt_cache_miss_tokens");
        long hit = hasHit ? nonNegative(usage.optLong("prompt_cache_hit_tokens", 0)) : 0;
        long miss = hasMiss ? nonNegative(usage.optLong("prompt_cache_miss_tokens", 0)) : 0;
        if (!hasHit || !hasMiss) {
            JSONObject details = usage.optJSONObject("prompt_tokens_details");
            long cached = details == null ? hit : nonNegative(details.optLong("cached_tokens", hit));
            hit = Math.min(prompt, cached);
            miss = Math.max(0, prompt - hit);
        }
        long output = nonNegative(usage.optLong("completion_tokens", 0));
        long total = nonNegative(usage.optLong("total_tokens", prompt + output));
        JSONObject completionDetails = usage.optJSONObject("completion_tokens_details");
        long reasoning = completionDetails == null ? 0 : nonNegative(completionDetails.optLong("reasoning_tokens", 0));

        Price price = Price.forModel(model);
        long minCost = safeCost(hit, price.hitMin) + safeCost(miss, price.missMin) + safeCost(output, price.outputMin);
        long maxCost = safeCost(hit, price.hitMax) + safeCost(miss, price.missMax) + safeCost(output, price.outputMax);
        addBoth(hit, miss, output, reasoning, total, 1, 0, 0, minCost, maxCost);
    }

    synchronized void recordSearchSuccess() {
        addBoth(0, 0, 0, 0, 0, 0, 1, 0, SEARCH_COST_NANO_USD, SEARCH_COST_NANO_USD);
    }

    synchronized void recordFetchSuccess() {
        addBoth(0, 0, 0, 0, 0, 0, 0, 1, SEARCH_COST_NANO_USD, SEARCH_COST_NANO_USD);
    }

    synchronized Snapshot month() {
        ensureMonth();
        return snapshot("m_", p.getString("month_key", YearMonth.now().toString()));
    }

    synchronized Snapshot cumulative() {
        ensureMonth();
        return snapshot("a_", "累計");
    }

    synchronized void resetMonth() {
        SharedPreferences.Editor e = p.edit();
        clearPrefix(e, "m_");
        e.putString("month_key", YearMonth.now().toString()).apply();
    }

    synchronized void resetCumulative() {
        SharedPreferences.Editor e = p.edit();
        clearPrefix(e, "a_");
        e.apply();
    }

    private void addBoth(long hit, long miss, long output, long reasoning, long total,
                         long chats, long searches, long fetches, long minCost, long maxCost) {
        ensureMonth();
        SharedPreferences.Editor e = p.edit();
        add(e, "m_", hit, miss, output, reasoning, total, chats, searches, fetches, minCost, maxCost);
        add(e, "a_", hit, miss, output, reasoning, total, chats, searches, fetches, minCost, maxCost);
        e.apply();
    }

    private void add(SharedPreferences.Editor e, String prefix, long hit, long miss, long output,
                     long reasoning, long total, long chats, long searches, long fetches,
                     long minCost, long maxCost) {
        e.putLong(prefix + "hit", satAdd(p.getLong(prefix + "hit", 0), hit));
        e.putLong(prefix + "miss", satAdd(p.getLong(prefix + "miss", 0), miss));
        e.putLong(prefix + "output", satAdd(p.getLong(prefix + "output", 0), output));
        e.putLong(prefix + "reasoning", satAdd(p.getLong(prefix + "reasoning", 0), reasoning));
        e.putLong(prefix + "total", satAdd(p.getLong(prefix + "total", 0), total));
        e.putLong(prefix + "chats", satAdd(p.getLong(prefix + "chats", 0), chats));
        e.putLong(prefix + "searches", satAdd(p.getLong(prefix + "searches", 0), searches));
        e.putLong(prefix + "fetches", satAdd(p.getLong(prefix + "fetches", 0), fetches));
        e.putLong(prefix + "cost_min", satAdd(p.getLong(prefix + "cost_min", 0), minCost));
        e.putLong(prefix + "cost_max", satAdd(p.getLong(prefix + "cost_max", 0), maxCost));
    }

    private Snapshot snapshot(String prefix, String label) {
        return new Snapshot(label,
                p.getLong(prefix + "hit", 0), p.getLong(prefix + "miss", 0),
                p.getLong(prefix + "output", 0), p.getLong(prefix + "reasoning", 0),
                p.getLong(prefix + "total", 0), p.getLong(prefix + "chats", 0),
                p.getLong(prefix + "searches", 0), p.getLong(prefix + "fetches", 0),
                p.getLong(prefix + "cost_min", 0), p.getLong(prefix + "cost_max", 0));
    }

    private void ensureMonth() {
        String now = YearMonth.now().toString();
        String saved = p.getString("month_key", "");
        if (!now.equals(saved)) {
            SharedPreferences.Editor e = p.edit();
            clearPrefix(e, "m_");
            e.putString("month_key", now).apply();
        }
    }

    private void clearPrefix(SharedPreferences.Editor e, String prefix) {
        e.remove(prefix + "hit").remove(prefix + "miss").remove(prefix + "output")
                .remove(prefix + "reasoning").remove(prefix + "total").remove(prefix + "chats")
                .remove(prefix + "searches").remove(prefix + "fetches")
                .remove(prefix + "cost_min").remove(prefix + "cost_max");
    }

    static String formatTokens(long v) {
        return NumberFormat.getIntegerInstance(Locale.JAPAN).format(v);
    }

    static String formatUsdRange(Snapshot s) {
        if (s.minCostNanoUsd == s.maxCostNanoUsd) return String.format(Locale.US, "US$%.6f", s.minUsd());
        return String.format(Locale.US, "US$%.6f〜%.6f", s.minUsd(), s.maxUsd());
    }

    static String formatYenRange(Snapshot s, double usdJpy) {
        double min = s.minUsd() * usdJpy;
        double max = s.maxUsd() * usdJpy;
        if (Math.abs(min - max) < 0.0001) return String.format(Locale.JAPAN, "約¥%.2f", min);
        return String.format(Locale.JAPAN, "約¥%.2f〜¥%.2f", min, max);
    }

    private static long nonNegative(long v) { return Math.max(0, v); }
    private static long safeCost(long tokens, long nanoUsdPerToken) {
        if (tokens <= 0 || nanoUsdPerToken <= 0) return 0;
        if (tokens > Long.MAX_VALUE / nanoUsdPerToken) return Long.MAX_VALUE;
        return tokens * nanoUsdPerToken;
    }
    private static long satAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return a + b;
    }

    /** Price range uses current official off-peak (min) and peak (max) rates. Values are nano-USD/token. */
    private static final class Price {
        final long hitMin, hitMax, missMin, missMax, outputMin, outputMax;
        Price(long hitMin, long hitMax, long missMin, long missMax, long outputMin, long outputMax) {
            this.hitMin = hitMin; this.hitMax = hitMax; this.missMin = missMin; this.missMax = missMax;
            this.outputMin = outputMin; this.outputMax = outputMax;
        }
        static Price forModel(String model) {
            String normalized = model == null ? "" : model.toLowerCase(Locale.US);
            if (normalized.contains("pro")) {
                return new Price(22, 44, 660, 1320, 1980, 3960);
            }
            return new Price(3, 6, 150, 300, 600, 1200);
        }
    }
}
