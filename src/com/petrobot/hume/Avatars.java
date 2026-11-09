// Fichier ajouté dans le fork Hume Launcher+ : avatars de profils par saga et sélecteur plein écran.
package com.petrobot.hume;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Profile pictures, streaming-service style: characters grouped by saga (assets/avatars, built by
 * tools/fetch_avatars.py). An avatar id is "category/name", e.g. "zelda/link".
 */
final class Avatars {
    private Avatars() {
    }

    static final class Item {
        String id, name;
    }

    static final class Category {
        String id, name;
        final List<Item> items = new ArrayList<>();
    }

    private static List<Category> cats;
    /**
     * Only the pictures in use stay in memory: one small bitmap per profile (3 at most, ~100 KB
     * each). The picker decodes its 44 portraits on demand at display size and frees them on close.
     */
    private static final android.util.LruCache<String, Bitmap> CACHE = new android.util.LruCache<String, Bitmap>(512 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };

    static synchronized List<Category> all(Context c) {
        if (cats != null) return cats;
        List<Category> out = new ArrayList<>();
        try (InputStream in = c.getAssets().open("avatars/index.json")) {
            JSONArray a = new JSONArray(new String(Suggestions.readAll(in), "UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Category cat = new Category();
                cat.id = o.getString("id");
                cat.name = o.getString("name");
                JSONArray items = o.getJSONArray("items");
                for (int j = 0; j < items.length(); j++) {
                    Item it = new Item();
                    it.id = items.getJSONObject(j).getString("id");
                    it.name = items.getJSONObject(j).getString("name");
                    cat.items.add(it);
                }
                if (!cat.items.isEmpty()) out.add(cat);
            }
        } catch (Exception ignored) {  // no avatars in this build: profiles keep their emoji
        }
        cats = out;
        return out;
    }

    static boolean exists(Context c, String id) {
        if (id == null || id.isEmpty()) return false;
        for (Category cat : all(c)) for (Item it : cat.items) if (it.id.equals(id)) return true;
        return false;
    }

    static String name(Context c, String id) {
        for (Category cat : all(c)) for (Item it : cat.items) if (it.id.equals(id)) return it.name;
        return "";
    }

    /** All avatar ids, in catalogue order. */
    static List<String> ids(Context c) {
        List<String> out = new ArrayList<>();
        for (Category cat : all(c)) for (Item it : cat.items) out.add(it.id);
        return out;
    }

    /** Frees the cached profile pictures (Hume went to the background); they decode again when shown. */
    static void trim() {
        CACHE.evictAll();
    }

    /** A profile's picture at about {@code px} wide (kept: profiles show it all the time), or null. */
    static Bitmap bitmap(Context c, String id, int px) {
        if (id == null || id.isEmpty()) return null;
        String key = id + "@" + px;
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        b = decode(c, id, px);
        if (b != null) CACHE.put(key, b);
        return b;
    }

    /**
     * Decodes at no more than twice {@code px} (pictures are 360 px; halving is free in the JPEG
     * decoder) and in RGB_565, half the memory of ARGB for a photo with no transparency.
     */
    static Bitmap decode(Context c, String id, int px) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        o.inSampleSize = px > 0 && px * 2 <= 360 ? 2 : 1;
        try (InputStream in = c.getAssets().open("avatars/" + id + ".jpg")) {
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            return null;
        }
    }


    interface Picked {
        void picked(String id);
    }

    /**
     * Full-screen chooser: one row per saga, round portraits that lift when focused, the focused
     * character's name under the row. The current avatar is ringed and focused first.
     */
    static void pick(Context c, String current, Picked done) {
        float u = Ui.unit(c);
        Dialog d = new Dialog(c, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xF20B0D12, 0xFA050608}));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.6f);
        }
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        int side = Math.round(48 * u);
        page.setPadding(0, Math.round(30 * u), 0, Math.round(30 * u));

        TextView title = new TextView(c);
        title.setText("Choisir un avatar");
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Ui.font(c, "semibold"));
        Ui.textSize(title, 26);
        title.setGravity(Gravity.CENTER);
        page.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int size = Math.round(78 * u);
        View[] first = {null};
        // Portraits are decoded off the main thread, one by one, and freed when the picker closes.
        List<ImageView> views = new ArrayList<>();
        List<Bitmap> loaded = new ArrayList<>();
        boolean[] closed = {false};
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        for (Category cat : all(c)) {
            TextView h = new TextView(c);
            h.setText(cat.name);
            h.setTextColor(0xE6FFFFFF);
            h.setTypeface(Ui.font(c, "semibold"));
            Ui.textSize(h, 15);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hlp.leftMargin = side;
            hlp.topMargin = Math.round(20 * u);
            page.addView(h, hlp);

            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipChildren(false);
            hs.setClipToPadding(false);
            hs.setFillViewport(true);
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            row.setPadding(side - Math.round(8 * u), Math.round(10 * u), side, 0);
            for (Item it : cat.items) {
                View cell = cell(c, it, size, u, it.id.equals(current), v -> {
                    d.dismiss();
                    done.picked(it.id);
                });
                ImageView iv = cell.findViewWithTag("pic");
                views.add(iv);
                pool.execute(() -> {
                    if (closed[0]) return;
                    Bitmap b = decode(c, it.id, size);
                    if (b == null) return;
                    iv.post(() -> {
                        if (closed[0]) {
                            b.recycle();
                            return;
                        }
                        loaded.add(b);
                        iv.setImageBitmap(b);
                        iv.animate().alpha(1f).setDuration(Motion.d(160)).start();
                    });
                });
                if (first[0] == null || it.id.equals(current)) first[0] = cell;
                row.addView(cell);
            }
            hs.addView(row);
            page.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setFillViewport(true);
        sv.addView(page);
        d.setContentView(sv);
        Clicks.quiet(d);
        d.setOnKeyListener((x, k, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                    || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN)) Clicks.move(c, k);
            return false;
        });
        d.setOnDismissListener(x -> {
            closed[0] = true;
            pool.shutdownNow();
            for (ImageView iv : views) iv.setImageDrawable(null);
            for (Bitmap b : loaded) b.recycle();
            loaded.clear();
        });
        d.show();
        if (first[0] != null) first[0].post(() -> first[0].requestFocus());
    }

    /** One round portrait; the name fades in under it when focused. */
    private static View cell(Context c, Item it, int size, float u, boolean current, View.OnClickListener click) {
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);

        FrameLayout box = new FrameLayout(c);
        box.setClipChildren(false);
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setTag("pic");
        iv.setAlpha(0f);  // fades in once decoded
        GradientDrawable ph = new GradientDrawable();
        ph.setShape(GradientDrawable.OVAL);
        ph.setColor(0x1FFFFFFF);
        box.setBackground(ph);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        iv.setClipToOutline(true);
        iv.setElevation(3 * u);
        box.addView(iv, new FrameLayout.LayoutParams(size, size));
        // Ring: white when focused, green on the current avatar.
        View ring = new View(c);
        GradientDrawable rg = new GradientDrawable();
        rg.setShape(GradientDrawable.OVAL);
        rg.setColor(0);
        rg.setStroke(Math.round(2.5f * u), current ? 0xFF30D158 : 0xFFFFFFFF);
        ring.setBackground(rg);
        ring.setAlpha(current ? 1f : 0f);
        ring.setElevation(4 * u);
        int pad = Math.round(3.5f * u);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(size + 2 * pad, size + 2 * pad);
        rlp.leftMargin = rlp.topMargin = -pad;
        box.addView(ring, rlp);
        cell.addView(box, new LinearLayout.LayoutParams(size, size));

        TextView label = new TextView(c);
        label.setText(it.name);
        label.setTextColor(0xFFFFFFFF);
        label.setTypeface(Ui.font(c, "medium"));
        Ui.textSize(label, 11.5f);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setAlpha(0f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(size + Math.round(24 * u), ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Math.round(10 * u);
        cell.addView(label, llp);

        cell.setOnFocusChangeListener((v, has) -> {
            box.animate().scaleX(has ? 1.14f : 1f).scaleY(has ? 1.14f : 1f).translationZ(has ? 12 * u : 0)
                    .setDuration(Motion.d(220)).start();
            if (!current) ring.animate().alpha(has ? 1f : 0f).setDuration(Motion.d(180)).start();
            label.animate().alpha(has ? 1f : 0f).translationY(has ? 4 * u : 0).setDuration(Motion.d(200)).start();
        });
        cell.setOnClickListener(v -> {
            Clicks.select(c);
            click.onClick(v);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = lp.rightMargin = Math.round(4 * u);
        cell.setLayoutParams(lp);
        return cell;
    }
}
