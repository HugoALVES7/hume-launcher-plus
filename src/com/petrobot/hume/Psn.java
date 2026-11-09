// Fichier ajouté dans le fork Hume Launcher+ : statistiques PlayStation lues sur PSNProfiles.
package com.petrobot.hume;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

/**
 * PlayStation trophy stats for the PSN pill and panel (off by default; Settings -> PlayStation).
 *
 * Source: PSNProfiles (psnprofiles.com/<user>), read in a hidden WebView because the site sits
 * behind Cloudflare: trophy counts, level, games, games at 100%, average completion, and world and
 * country rank. No sign-in: the PSN ID's trophies only need to be public.
 * A value the page doesn't give stays -1 and is simply not shown.
 */
final class Psn {
    static final String PREFS = "psn", K_ON = "on", K_USER = "user", K_CACHE = "cache", K_AT = "cache_at",
            K_ERROR = "error";
    /**
     * PSN ID used until one is set in Settings: "psn_user" in assets/personal.json, a local file
     * kept out of git (see personal.example.json). Empty when the build has none.
     */
    private static String defaultUser;

    static synchronized String defaultUser(Context c) {
        if (defaultUser == null) {
            defaultUser = "";
            try (java.io.InputStream in = c.getAssets().open("personal.json")) {
                defaultUser = new JSONObject(new String(Suggestions.readAll(in), "UTF-8")).optString("psn_user", "").trim();
            } catch (Exception ignored) {  // no personal file in this build
            }
        }
        return defaultUser;
    }
    static final long REFRESH_MS = 3 * 60 * 60 * 1000L, RETRY_MS = 30 * 60 * 1000L;


    private static boolean busy;

    private Psn() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean(K_ON, false);
    }

    static String user(Context c) {
        String u = prefs(c).getString(K_USER, "").trim();
        return u.isEmpty() ? defaultUser(c) : u;
    }

    /** Last problem, for Settings ("" when fine). */
    static String error(Context c) {
        return prefs(c).getString(K_ERROR, "");
    }

    /** Settings changed: fetch again on the next refresh. */
    static void changed(Context c) {
        prefs(c).edit().remove(K_AT).apply();
    }

    static void setUser(Context c, String u) {
        prefs(c).edit().putString(K_USER, u.trim()).remove(K_CACHE).remove(K_AT).apply();
    }

    // ---------------------------------------------------------------- data

    static final class Data {
        String user = "", country = "", avatar = "";
        int level = -1, levelProgress = -1, platinum = -1, gold = -1, silver = -1, bronze = -1,
                games = -1, completed = -1, worldRank = -1, countryRank = -1;
        float avg = -1;
        long at;
        boolean profiles;

        int total() {
            if (platinum < 0 && gold < 0 && silver < 0 && bronze < 0) return -1;
            return Math.max(0, platinum) + Math.max(0, gold) + Math.max(0, silver) + Math.max(0, bronze);
        }

        boolean empty() {
            return platinum < 0 && gold < 0 && silver < 0 && bronze < 0 && level < 0 && avg < 0 && games < 0
                    && worldRank < 0 && countryRank < 0;
        }

        JSONObject toJson() throws Exception {
            return new JSONObject().put("user", user).put("country", country).put("avatar", avatar)
                    .put("level", level).put("levelProgress", levelProgress).put("platinum", platinum)
                    .put("gold", gold).put("silver", silver).put("bronze", bronze).put("games", games)
                    .put("completed", completed).put("worldRank", worldRank).put("countryRank", countryRank)
                    .put("avg", avg).put("at", at).put("profiles", profiles);
        }

        static Data fromJson(JSONObject o) {
            Data d = new Data();
            d.user = o.optString("user");
            d.country = o.optString("country");
            d.avatar = o.optString("avatar");
            d.level = o.optInt("level", -1);
            d.levelProgress = o.optInt("levelProgress", -1);
            d.platinum = o.optInt("platinum", -1);
            d.gold = o.optInt("gold", -1);
            d.silver = o.optInt("silver", -1);
            d.bronze = o.optInt("bronze", -1);
            d.games = o.optInt("games", -1);
            d.completed = o.optInt("completed", -1);
            d.worldRank = o.optInt("worldRank", -1);
            d.countryRank = o.optInt("countryRank", -1);
            d.avg = (float) o.optDouble("avg", -1);
            d.at = o.optLong("at");
            d.profiles = o.optBoolean("profiles");
            return d;
        }
    }

    /** The last stats for the current user, or null. */
    static Data cached(Context c) {
        String s = prefs(c).getString(K_CACHE, null);
        if (s == null) return null;
        try {
            Data d = Data.fromJson(new JSONObject(s));
            return d.user.equalsIgnoreCase(user(c)) && !d.empty() ? d : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetches when due (every 3 hours) or when forced. Call on the main thread from an activity:
     * PSNProfiles is read in a hidden WebView attached to {@code host}. {@code done} runs on the
     * main thread when something new is saved.
     */
    static void refresh(android.app.Activity a, android.view.ViewGroup host, boolean force, Runnable done) {
        Context c = a.getApplicationContext();
        if (busy || !enabled(c) || user(c).isEmpty()) return;
        long at = prefs(c).getLong(K_AT, 0);
        if (!force && System.currentTimeMillis() - at < REFRESH_MS) return;
        busy = true;
        String u = user(c);
        Data d = new Data();
        d.user = u;
        if (a.isDestroyed()) {
            busy = false;
            return;
        }
        Scrape.read(a, host, u, d, ok -> {
            busy = false;
            String e = ok ? "" : "PSNProfiles inaccessible";
            android.util.Log.i("Hume", "psn: profiles=" + ok + " empty=" + d.empty() + " err=" + e);
            save(c, d, e);
            if (done != null) done.run();
        });
    }

    static void save(Context c, Data d, String err) {
        SharedPreferences.Editor e = prefs(c).edit().putString(K_ERROR, err);
        long now = System.currentTimeMillis();
        if (d.empty()) {
            // Keep what we had; try again in half an hour.
            e.putLong(K_AT, now - REFRESH_MS + RETRY_MS).apply();
            return;
        }
        d.at = now;
        try {
            e.putString(K_CACHE, d.toJson().toString());
        } catch (Exception ignored) {
        }
        e.putLong(K_AT, now).apply();
    }

    static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }

    // ---------------------------------------------------------------- PSNProfiles

    /** Reads psnprofiles.com/<user> in a hidden WebView and fills what Data is still missing. */
    static final class Scrape {
        interface Done {
            void run(boolean ok);
        }

        static final String JS = "(function(){"
                + "if(!document.querySelector('#user-bar')){return /moment|attention|cloudflare/i.test(document.title)||document.readyState!='complete'?'wait':'none';}"
                + "function n(t){t=(t||'').replace(/[^0-9.]/g,'');return t?parseFloat(t):-1;}"
                + "var o={};"
                + "document.querySelectorAll('ul.profile-bar li').forEach(function(li){o[li.className.trim()]=n(li.textContent);});"
                + "document.querySelectorAll('span.stat').forEach(function(s){var l=s.querySelector('span');if(!l)return;"
                + "var k=l.textContent.trim();o[k]=n(s.textContent.replace(k,''));});"
                + "var lv=document.querySelector('.level-box span');o.level=lv?n(lv.textContent):-1;"
                + "var pb=document.querySelector('.progress-bar.level div');o.levelProgress=pb?n(pb.style.width):-1;"
                + "var f=document.querySelector('#bar-country');o.country=f?f.className.replace('round-flags','').trim():'';"
                + "o.avatar='';document.querySelectorAll('#user-bar img').forEach(function(i){if(!o.avatar&&/\\/avatars\\//.test(i.src))o.avatar=i.src.replace('/avatars/m/','/avatars/l/');});"
                + "return JSON.stringify(o);})()";

        static void read(android.app.Activity a, android.view.ViewGroup host, String user, Data d, Done done) {
            android.webkit.WebView w;
            try {
                w = new android.webkit.WebView(a);
            } catch (Exception e) {  // no WebView on this TV
                done.run(false);
                return;
            }
            w.getSettings().setJavaScriptEnabled(true);
            w.getSettings().setDomStorageEnabled(true);
            w.getSettings().setLoadsImagesAutomatically(false);
            w.setFocusable(false);
            w.setAlpha(0.01f);
            host.addView(w, new android.view.ViewGroup.LayoutParams(4, 4));
            Handler h = new Handler(Looper.getMainLooper());
            long deadline = System.currentTimeMillis() + 40_000;
            boolean[] finished = {false};
            Runnable[] poll = new Runnable[1];
            poll[0] = () -> {
                if (finished[0]) return;
                w.evaluateJavascript(JS, res -> {
                    if (finished[0]) return;
                    String s = unquote(res);
                    android.util.Log.i("Hume", "psn profiles: " + (s.length() > 160 ? s.substring(0, 160) : s));
                    boolean timeUp = System.currentTimeMillis() > deadline;
                    if (s.startsWith("{") || s.equals("none") || timeUp) {
                        finished[0] = true;
                        boolean ok = s.startsWith("{") && fill(d, s);
                        host.removeView(w);
                        w.destroy();
                        done.run(ok);
                    } else {
                        h.postDelayed(poll[0], 1500);
                    }
                });
            };
            w.setWebViewClient(new android.webkit.WebViewClient() {
                @Override public void onPageFinished(android.webkit.WebView view, String url) {
                    h.removeCallbacks(poll[0]);
                    h.postDelayed(poll[0], 800);
                }
            });
            try {
                w.loadUrl("https://psnprofiles.com/" + enc(user));
            } catch (Exception e) {
                finished[0] = true;
                host.removeView(w);
                w.destroy();
                done.run(false);
                return;
            }
            h.postDelayed(poll[0], 6000);  // in case onPageFinished never comes
        }

        static String unquote(String res) {
            if (res == null || res.equals("null")) return "";
            try {
                return new JSONArray("[" + res + "]").getString(0);
            } catch (Exception e) {
                return res;
            }
        }

        /** Fills fields the PlayStation API didn't give; ranks always come from here. */
        static boolean fill(Data d, String json) {
            try {
                JSONObject o = new JSONObject(json);
                if (d.platinum < 0) d.platinum = num(o, "platinum");
                if (d.gold < 0) d.gold = num(o, "gold");
                if (d.silver < 0) d.silver = num(o, "silver");
                if (d.bronze < 0) d.bronze = num(o, "bronze");
                if (d.level < 0) {
                    d.level = num(o, "level");
                    d.levelProgress = num(o, "levelProgress");
                }
                if (d.games < 0) d.games = num(o, "Games Played");
                if (d.completed < 0) d.completed = num(o, "Completed Games");
                if (d.avg < 0) d.avg = (float) o.optDouble("Completion", -1);
                d.worldRank = num(o, "World Rank");
                d.countryRank = num(o, "Country Rank");
                d.country = o.optString("country").toUpperCase(java.util.Locale.ROOT);
                d.avatar = o.optString("avatar");
                d.profiles = true;
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        static int num(JSONObject o, String k) {
            double v = o.optDouble(k, -1);
            return v < 0 ? -1 : (int) Math.round(v);
        }
    }
}
