// Fichier ajouté dans le fork Hume Launcher+ : pastille et panneau PlayStation.
package com.petrobot.hume;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PlayStation trophies on a glass surface, next to the weather pill and in the same tvOS manner.
 * Compact: a platinum trophy, the platinum count and the average completion. Full: the panel
 * opened from it - level, the four trophy grades and a list of stats. Every part is laid out from
 * the data present: anything the sources didn't give (e.g. PSNProfiles ranks) is left out and the
 * panel closes up around it. Trophies are drawn in PlayStation's grade colours.
 */
final class PsnView extends View {
    static final float COMPACT_H = WeatherView.COMPACT_H, FULL_W = 380;
    static final int PLATINUM = 0, GOLD = 1, SILVER = 2, BRONZE = 3;
    static final int PS_BLUE = 0xFF0070D1;

    final boolean full;
    final float u;
    Psn.Data data;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Typeface regular, medium, semibold;
    private final RectF r = new RectF();

    PsnView(Context c, boolean full, float unit) {
        super(c);
        this.full = full;
        this.u = unit;
        regular = Ui.font(c, "regular");
        medium = Ui.font(c, "medium");
        semibold = Ui.font(c, "semibold");
    }

    void setData(Psn.Data d) {
        data = d;
        if (full) loadAvatar();
        requestLayout();
        invalidate();
    }

    // ---------------------------------------------------------------- profile picture

    private static String avatarUrl;
    private static android.graphics.Bitmap avatar;

    /** Shows the PSN profile picture: from memory, then the disk cache, then the network. */
    private void loadAvatar() {
        String url = data == null ? "" : data.avatar;
        if (url == null || url.isEmpty() || (url.equals(avatarUrl) && avatar != null)) return;
        java.io.File f = new java.io.File(getContext().getCacheDir(), "psn_avatar_" + Integer.toHexString(url.hashCode()) + ".png");
        new Thread(() -> {
            android.graphics.Bitmap b = null;
            try {
                if (!f.exists()) {
                    java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    con.setConnectTimeout(5000);
                    con.setReadTimeout(8000);
                    try {
                        if (con.getResponseCode() == 200) {
                            byte[] bytes = Suggestions.readAll(con.getInputStream());
                            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                            out.write(bytes);
                            out.close();
                        }
                    } finally {
                        con.disconnect();
                    }
                }
                if (f.exists()) b = android.graphics.BitmapFactory.decodeFile(f.getPath());
            } catch (Exception e) {
                android.util.Log.w("Hume", "psn avatar: " + e);
            }
            android.graphics.Bitmap got = b;
            if (got != null) post(() -> {
                avatarUrl = url;
                avatar = got;
                requestLayout();
                invalidate();
            });
        }).start();
    }

    /** The picture to draw for the current data, or null. */
    private android.graphics.Bitmap avatarFor() {
        return data != null && avatar != null && data.avatar != null && data.avatar.equals(avatarUrl) ? avatar : null;
    }

    // ---------------------------------------------------------------- what to show

    /** The stats list of the panel: only rows with a value. */
    List<String[]> rows() {
        List<String[]> out = new ArrayList<>();
        Psn.Data d = data;
        if (d == null) return out;
        if (d.completed >= 0) out.add(new String[]{"Jeux à 100 %", fmt(d.completed)});
        if (d.avg >= 0) out.add(new String[]{"Complétion moyenne", pct(d.avg)});
        if (d.games >= 0) out.add(new String[]{"Jeux joués", fmt(d.games)});
        if (d.total() >= 0) out.add(new String[]{"Trophées au total", fmt(d.total())});
        if (d.worldRank > 0) out.add(new String[]{"Rang mondial", "#" + fmt(d.worldRank)});
        if (d.countryRank > 0) {
            out.add(new String[]{"Rang national" + (d.country.isEmpty() ? "" : " · " + d.country), "#" + fmt(d.countryRank)});
        }
        return out;
    }

    int[] grades() {
        Psn.Data d = data;
        List<Integer> g = new ArrayList<>();
        if (d != null) {
            if (d.platinum >= 0) g.add(PLATINUM);
            if (d.gold >= 0) g.add(GOLD);
            if (d.silver >= 0) g.add(SILVER);
            if (d.bronze >= 0) g.add(BRONZE);
        }
        int[] a = new int[g.size()];
        for (int i = 0; i < a.length; i++) a[i] = g.get(i);
        return a;
    }

    int count(int grade) {
        switch (grade) {
            case PLATINUM: return data.platinum;
            case GOLD: return data.gold;
            case SILVER: return data.silver;
            default: return data.bronze;
        }
    }

    static String fmt(int n) {
        return String.format(Locale.US, "%,d", n).replace(',', ' ');
    }

    static String pct(float v) {
        return String.format(Locale.FRANCE, "%.1f %%", v);
    }

    // ---------------------------------------------------------------- layout

    static final float HEADER = 92, GRADES = 92, ROW = 34, FOOTER = 34, PAD = 20;

    /** Panel height for the data present (no empty sections). */
    float fullHeight() {
        float h = PAD + HEADER;
        if (avatarFor() != null && data.level < 0) h += 26;  // the picture needs the full header
        if (data != null && data.level < 0) h -= 26;
        if (grades().length > 0) h += GRADES;
        int n = rows().size();
        if (n > 0) h += 18 + n * ROW;
        return h + FOOTER;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        if (full) {
            setMeasuredDimension(Math.round(FULL_W * u), Math.round(fullHeight() * u));
            return;
        }
        float w = 150 * u;
        Psn.Data d = data;
        if (d != null) {
            float pad = 16 * u, is = 24 * u;
            String big = big();
            p.setTypeface(regular);
            p.setTextSize(24 * u);
            p.setLetterSpacing(-0.03f);
            float tw = p.measureText(big);
            p.setLetterSpacing(0f);
            p.setTypeface(semibold);
            p.setTextSize(12.5f * u);
            float l1 = p.measureText(line1());
            p.setTypeface(regular);
            p.setTextSize(11.5f * u);
            float l2 = line2().isEmpty() ? 0 : p.measureText(line2());
            w = pad + is + 8 * u + tw + 10 * u + Math.max(l1, l2) + pad + 2 * u;
            w = Math.max(120 * u, Math.min(w, 280 * u));
        }
        setMeasuredDimension(Math.round(w), resolveSize(Math.round(COMPACT_H * u), hSpec));
    }

    /** Pill: the big number is platinums, else the average, else the level. */
    String big() {
        Psn.Data d = data;
        if (d.platinum >= 0) return fmt(d.platinum);
        if (d.avg >= 0) return pct(d.avg);
        if (d.level >= 0) return fmt(d.level);
        return "—";
    }

    String line1() {
        Psn.Data d = data;
        if (d.platinum >= 0) return d.platinum == 1 ? "Platine" : "Platines";
        if (d.avg >= 0) return "Complétion";
        return d.user;
    }

    String line2() {
        Psn.Data d = data;
        if (d.platinum >= 0 && d.avg >= 0) return pct(d.avg) + " de complétion";
        if (d.platinum >= 0 || d.avg >= 0) return d.user;
        return "";
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas c) {
        if (data == null) {
            text(c, "Chargement des trophées…", getWidth() / 2f, getHeight() / 2f + 5 * u, 13, medium, 0.7f, Paint.Align.CENTER);
            return;
        }
        if (full) drawFull(c);
        else drawCompact(c);
    }

    private void drawCompact(Canvas c) {
        float h = getHeight(), w = getWidth(), pad = 16 * u, is = 24 * u, mid = h / 2f;
        trophy(c, data.platinum >= 0 ? PLATINUM : GOLD, pad, mid - is / 2f, is);
        float tx = pad + is + 8 * u;
        p.setLetterSpacing(-0.03f);
        float tw = text(c, big(), tx, mid + 8.5f * u, 24, regular, 1f, Paint.Align.LEFT);
        p.setLetterSpacing(0f);
        float cx = tx + tw + 10 * u, room = w - cx - pad;
        String l2 = line2();
        if (l2.isEmpty()) {
            text(c, fit(line1(), room, 12.5f, semibold), cx, mid + 4.5f * u, 12.5f, semibold, 1f, Paint.Align.LEFT);
        } else {
            text(c, fit(line1(), room, 12.5f, semibold), cx, mid - 2.5f * u, 12.5f, semibold, 1f, Paint.Align.LEFT);
            text(c, fit(l2, room, 11.5f, regular), cx, mid + 12 * u, 11.5f, regular, 0.6f, Paint.Align.LEFT);
        }
    }

    private void drawFull(Canvas c) {
        Psn.Data d = data;
        float w = getWidth(), pad = PAD * u, x0 = pad, x1 = w - pad, mid = w / 2f;
        float y = pad;

        // Header. With a profile picture: the picture on the left, ID and level beside it (like the
        // PlayStation profile card). Without: PlayStation mark, ID and level, centred.
        android.graphics.Bitmap pic = avatarFor();
        if (pic != null) {
            float s = 72 * u, cy = y + 2 * u + s / 2;
            fill.setShader(null);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0x4DFFFFFF);
            c.drawCircle(x0 + s / 2, cy, s / 2 + 2.5f * u, fill);
            android.graphics.BitmapShader sh = new android.graphics.BitmapShader(pic, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            android.graphics.Matrix m = new android.graphics.Matrix();
            float k = s / Math.min(pic.getWidth(), pic.getHeight());
            m.setScale(k, k);
            m.postTranslate(x0 - (pic.getWidth() * k - s) / 2, cy - s / 2 - (pic.getHeight() * k - s) / 2);
            sh.setLocalMatrix(m);
            Paint ap = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            ap.setShader(sh);
            c.drawCircle(x0 + s / 2, cy, s / 2, ap);

            float tx = x0 + s + 18 * u, room = x1 - tx;
            psMark(c, tx, y + 3 * u, 14 * u);
            text(c, fit(d.user, room - 28 * u, 16, semibold), tx + 27 * u, y + 15 * u, 16, semibold, 1f, Paint.Align.LEFT);
            if (d.level >= 0) {
                text(c, "Niveau " + fmt(d.level), tx, y + 50 * u, 28, regular, 1f, Paint.Align.LEFT);
                if (d.levelProgress >= 0) {
                    float th = 5 * u, by = y + 62 * u;
                    fill.setColor(0x33FFFFFF);
                    r.set(tx, by, x1, by + th);
                    c.drawRoundRect(r, th / 2, th / 2, fill);
                    fill.setColor(PS_BLUE);
                    r.set(tx, by, tx + Math.max(th, room * Math.min(100, d.levelProgress) / 100f), by + th);
                    c.drawRoundRect(r, th / 2, th / 2, fill);
                    text(c, d.levelProgress + " % vers le niveau suivant", tx, by + 20 * u, 11.5f, medium, 0.55f, Paint.Align.LEFT);
                }
            }
            y += HEADER * u;
        } else {
        psMark(c, mid - 64 * u, y + 2 * u, 16 * u);
        text(c, fit(d.user, x1 - x0 - 50 * u, 17, semibold), mid + 10 * u, y + 15 * u, 17, semibold, 1f, Paint.Align.CENTER);
        if (d.level >= 0) {
            text(c, "Niveau " + fmt(d.level), mid, y + 52 * u, 30, regular, 1f, Paint.Align.CENTER);
            if (d.levelProgress >= 0) {
                float bw = 160 * u, th = 5 * u, by = y + 66 * u;
                fill.setShader(null);
                fill.setColor(0x33FFFFFF);
                r.set(mid - bw / 2, by, mid + bw / 2, by + th);
                c.drawRoundRect(r, th / 2, th / 2, fill);
                fill.setColor(PS_BLUE);
                r.set(mid - bw / 2, by, mid - bw / 2 + Math.max(th, bw * Math.min(100, d.levelProgress) / 100f), by + th);
                c.drawRoundRect(r, th / 2, th / 2, fill);
                text(c, d.levelProgress + " % vers le niveau suivant", mid, by + 20 * u, 11.5f, medium, 0.55f, Paint.Align.CENTER);
            }
            y += HEADER * u;
        } else {
            y += (HEADER - 26) * u;
        }
        }

        // The four grades, spread evenly across the panel.
        int[] g = grades();
        if (g.length > 0) {
            hairline(c, x0, x1, y);
            float cw = (x1 - x0) / g.length, is = 34 * u;
            for (int i = 0; i < g.length; i++) {
                float cx = x0 + cw * (i + 0.5f);
                trophy(c, g[i], cx - is / 2, y + 12 * u, is);
                text(c, fmt(count(g[i])), cx, y + 66 * u, 17, semibold, 1f, Paint.Align.CENTER);
                text(c, gradeName(g[i]).toUpperCase(Locale.ROOT), cx, y + 82 * u, 10, semibold, 0.5f, Paint.Align.CENTER);
            }
            y += GRADES * u;
        }

        // Stats list.
        List<String[]> rows = rows();
        if (!rows.isEmpty()) {
            text(c, "STATISTIQUES", x0, y + 14 * u, 11.5f, semibold, 0.55f, Paint.Align.LEFT);
            y += 18 * u;
            for (String[] row : rows) {
                hairline(c, x0, x1, y);
                float base = y + ROW * u / 2f + 5 * u;
                text(c, row[0], x0, base, 15, medium, 0.85f, Paint.Align.LEFT);
                text(c, row[1], x1, base, 15, semibold, 1f, Paint.Align.RIGHT);
                y += ROW * u;
            }
        }

        // Sources and time of the last update.
        List<String> src = new ArrayList<>();
        if (d.profiles) src.add("PSNProfiles");
        String when = android.text.format.DateFormat.getTimeFormat(getContext()).format(new java.util.Date(d.at));
        text(c, String.join(" et ", src) + "  ·  Mis à jour à " + when, mid, getHeight() - 14 * u, 11.5f, medium, 0.5f,
                Paint.Align.CENTER);
    }

    static String gradeName(int g) {
        return g == PLATINUM ? "Platine" : g == GOLD ? "Or" : g == SILVER ? "Argent" : "Bronze";
    }

    /** PlayStation's grade colours, light to dark (top to bottom of the cup). */
    static int[] gradeColors(int g) {
        switch (g) {
            case PLATINUM: return new int[]{0xFFF2F4FA, 0xFFB9C6DE, 0xFF7D8DB0};
            case GOLD: return new int[]{0xFFFFE9A3, 0xFFE8B53C, 0xFFB07A1C};
            case SILVER: return new int[]{0xFFF4F4F4, 0xFFC4C7CC, 0xFF8B8F96};
            default: return new int[]{0xFFF6C49B, 0xFFCB8452, 0xFF8E4F27};
        }
    }

    /** A trophy cup in a {@code s} square: bowl with handles, stem and a two-step base. */
    void trophy(Canvas c, int grade, float x, float y, float s) {
        int[] col = gradeColors(grade);
        fill.setShader(new LinearGradient(x, y, x + s * 0.3f, y + s, col, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        fill.setStyle(Paint.Style.FILL);
        Path bowl = new Path();
        bowl.moveTo(x + s * 0.22f, y + s * 0.08f);
        bowl.lineTo(x + s * 0.78f, y + s * 0.08f);
        bowl.cubicTo(x + s * 0.78f, y + s * 0.42f, x + s * 0.66f, y + s * 0.56f, x + s * 0.5f, y + s * 0.58f);
        bowl.cubicTo(x + s * 0.34f, y + s * 0.56f, x + s * 0.22f, y + s * 0.42f, x + s * 0.22f, y + s * 0.08f);
        bowl.close();
        c.drawPath(bowl, fill);
        // Handles
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(s * 0.065f);
        r.set(x + s * 0.07f, y + s * 0.14f, x + s * 0.33f, y + s * 0.42f);
        c.drawArc(r, 90, 180, false, fill);
        r.set(x + s * 0.67f, y + s * 0.14f, x + s * 0.93f, y + s * 0.42f);
        c.drawArc(r, 270, 180, false, fill);
        fill.setStyle(Paint.Style.FILL);
        // Stem and base
        r.set(x + s * 0.45f, y + s * 0.56f, x + s * 0.55f, y + s * 0.74f);
        c.drawRect(r, fill);
        r.set(x + s * 0.32f, y + s * 0.72f, x + s * 0.68f, y + s * 0.82f);
        c.drawRoundRect(r, s * 0.03f, s * 0.03f, fill);
        r.set(x + s * 0.24f, y + s * 0.82f, x + s * 0.76f, y + s * 0.94f);
        c.drawRoundRect(r, s * 0.04f, s * 0.04f, fill);
        fill.setShader(null);
        // Rim highlight
        fill.setColor(0x66FFFFFF);
        r.set(x + s * 0.26f, y + s * 0.1f, x + s * 0.4f, y + s * 0.36f);
        c.drawOval(r, fill);
    }

    /** A small PlayStation-blue badge with "PS" for the panel header. */
    private void psMark(Canvas c, float x, float y, float s) {
        fill.setShader(null);
        fill.setColor(PS_BLUE);
        r.set(x, y, x + s * 1.5f, y + s);
        c.drawRoundRect(r, s * 0.25f, s * 0.25f, fill);
        text(c, "PS", x + s * 0.75f, y + s * 0.74f, s / u * 0.62f, semibold, 1f, Paint.Align.CENTER);
    }

    private String fit(String s, float max, float size, android.graphics.Typeface tf) {
        p.setTypeface(tf);
        p.setTextSize(size * u);
        if (p.measureText(s) <= max) return s;
        int n = p.breakText(s, true, max - p.measureText("…"), null);
        return s.substring(0, Math.max(1, n)).trim() + "…";
    }

    private void hairline(Canvas c, float x0, float x1, float y) {
        p.setShader(null);
        p.setColor(0x33FFFFFF);
        p.setStrokeWidth(Math.max(1, 0.8f * u));
        c.drawLine(x0, y, x1, y, p);
    }

    private float text(Canvas c, String s, float x, float y, float size, android.graphics.Typeface tf, float alpha, Paint.Align al) {
        p.setTypeface(tf);
        p.setTextSize(size * u);
        p.setTextAlign(al);
        p.setColor(0xFFFFFFFF);
        p.setAlpha(Math.round(255 * alpha));
        c.drawText(s, x, y, p);
        return p.measureText(s);
    }
}
