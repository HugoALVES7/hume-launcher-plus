// Fichier ajouté dans le fork Hume Launcher+ : images HD pour « À reprendre ».
package com.petrobot.hume;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Full-HD artwork for Continue Watching. Apps publish small thumbnails (often 300-640 px wide) to
 * Watch Next, which look soft once stretched across the screen. This looks the title up on
 * Stremio's Cinemeta catalogue (no key needed) and uses its 1920x1080 background and title logo
 * from metahub; the app's own artwork stays as the fallback. Results (and misses) are cached per
 * title, so each show is looked up once. Runs on Hume's background thread.
 */
final class HdArt {
    static final String CINEMETA = "https://v3-cinemeta.strem.io/catalog/";
    static final long MISS_RETRY_MS = 3L * 24 * 60 * 60 * 1000;

    private HdArt() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("hdart", Context.MODE_PRIVATE);
    }

    /** Replaces the item's backdrop and logo with HD ones when the title is found. */
    static void apply(Context c, Suggestions.Item it, boolean series) {
        String key = WatchNext.norm(it.title);
        if (key.isEmpty()) return;
        SharedPreferences p = prefs(c);
        key = "v2|" + key;  // v2: stricter matching, background checked before use
        String hit = p.getString("hit|" + key, null);
        if (hit == null) {
            long miss = p.getLong("miss|" + key, 0);
            if (System.currentTimeMillis() - miss < MISS_RETRY_MS) return;
            String imdb = search(it.title, series ? "series" : "movie");
            if (imdb.isEmpty()) imdb = search(it.title, series ? "movie" : "series");
            if (imdb.isEmpty()) {
                p.edit().putLong("miss|" + key, System.currentTimeMillis()).apply();
                return;
            }
            hit = imdb;
            p.edit().putString("hit|" + key, hit).remove("miss|" + key).apply();
        }
        it.backdrop = "https://images.metahub.space/background/medium/" + hit + "/img";
        it.logo = "https://images.metahub.space/logo/medium/" + hit + "/img";
    }

    /** True when the image answers 200 (metahub redirects, then 404s for titles it lacks). */
    static boolean exists(String url) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("HEAD");
            con.setConnectTimeout(4000);
            con.setReadTimeout(6000);
            try {
                return con.getResponseCode() == 200;
            } finally {
                con.disconnect();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** IMDb id of the best match for the title, or "" (only a close name match counts). */
    static String search(String title, String type) {
        try {
            String q = URLEncoder.encode(title, "UTF-8").replace("+", "%20");
            HttpURLConnection con = (HttpURLConnection) new URL(CINEMETA + type + "/top/search=" + q + ".json").openConnection();
            con.setConnectTimeout(4000);
            con.setReadTimeout(6000);
            try {
                if (con.getResponseCode() != 200) return "";
                JSONArray metas = new JSONObject(new String(Suggestions.readAll(con.getInputStream()), StandardCharsets.UTF_8))
                        .optJSONArray("metas");
                String want = WatchNext.norm(title);
                for (int i = 0; metas != null && i < Math.min(5, metas.length()); i++) {
                    JSONObject m = metas.getJSONObject(i);
                    String id = m.optString("imdb_id", m.optString("id"));
                    String raw = m.optString("name").toLowerCase(java.util.Locale.ROOT).trim();
                    String name = WatchNext.norm(raw);
                    if (!id.startsWith("tt") || m.optString("background").isEmpty()) continue;
                    // Same title, or the title plus a subtitle after a colon ("Frieren: Beyond
                    // Journey's End"); "Nouvelle École - La Renaissance..." is another work.
                    String t = title.toLowerCase(java.util.Locale.ROOT).trim();
                    boolean same = name.equals(want) || raw.startsWith(t + ":") || t.startsWith(raw + ":");
                    if (same && exists("https://images.metahub.space/background/medium/" + id + "/img")) return id;
                }
            } finally {
                con.disconnect();
            }
        } catch (Exception e) {
            android.util.Log.w("Hume", "hd art: " + e);
        }
        return "";
    }
}
