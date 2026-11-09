// Modifié dans le fork Hume Launcher+ (par rapport à Hume Launcher de Petrobot) : sons de navigation propres (SoundPool) à la place des sons système.
package com.petrobot.hume;

import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;

/**
 * Navigation sounds: Hume's own short, soft tvOS-style ticks (res/raw/nav_*.wav, made by
 * tools/gen_sounds.py) played through a SoundPool; a tick when focus moves, a pop on select and a
 * softer one on Back. Falls back to Android's system sounds until the pool has loaded.
 * Settings -> Home -> Navigation sounds.
 */
final class Clicks {
    static final float VOLUME = 0.4f;  // softer sounds, a bit quieter too
    private static AudioManager audio;
    private static android.media.SoundPool pool;
    private static int sMove, sSelect, sBack;
    private static final java.util.Set<Integer> ready = new java.util.HashSet<>();

    private Clicks() {
    }

    static boolean enabled(Context c) {
        return c.getSharedPreferences("sounds", Context.MODE_PRIVATE).getBoolean("nav_sounds", true);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("sounds", Context.MODE_PRIVATE).edit().putBoolean("nav_sounds", on).apply();
    }

    static void init(Context c) {
        Context app = c.getApplicationContext();
        if (audio == null) audio = app.getSystemService(AudioManager.class);
        if (pool == null) {
            pool = new android.media.SoundPool.Builder().setMaxStreams(4)
                    .setAudioAttributes(new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .build();
            pool.setOnLoadCompleteListener((sp, id, status) -> {
                if (status == 0) synchronized (ready) { ready.add(id); }
            });
            sMove = pool.load(app, R.raw.nav_move, 1);
            sSelect = pool.load(app, R.raw.nav_select, 1);
            sBack = pool.load(app, R.raw.nav_back, 1);
        }
    }

    /** Plays one of Hume's sounds; Android's system effect if it isn't loaded yet. */
    private static void play(Context c, int sound, int fallback) {
        init(c);
        if (!enabled(c)) return;
        boolean ok;
        synchronized (ready) { ok = ready.contains(sound); }
        if (ok) pool.play(sound, VOLUME, VOLUME, 1, 0, 1f);
        else if (audio != null) audio.playSoundEffect(fallback, VOLUME);
    }

    /** Focus moved: Hume's tick (Android's focus sound for that direction as a fallback). */
    static void move(Context c, int keyCode) {
        int fx;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: fx = AudioManager.FX_FOCUS_NAVIGATION_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: fx = AudioManager.FX_FOCUS_NAVIGATION_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: fx = AudioManager.FX_FOCUS_NAVIGATION_LEFT; break;
            default: fx = AudioManager.FX_FOCUS_NAVIGATION_RIGHT; break;
        }
        play(c, sMove, fx);
    }

    /** Something was chosen. */
    static void select(Context c) {
        play(c, sSelect, AudioManager.FX_KEY_CLICK);
    }

    /** Back. */
    static void back(Context c) {
        play(c, sBack, AudioManager.FX_KEY_CLICK);
    }

    // ------------------------------------------------------------ Android's own sounds

    /*
     * Android plays its own click (on every View click) and focus sounds (when it moves focus for a
     * D-pad press) according to the TV's system setting, not Hume's. So Hume silences them on its
     * views and moves focus itself; its own sounds above, governed by the Sounds switch, are then
     * the only ones (and there are no double clicks when it is on).
     */

    private static final android.view.ViewGroup.OnHierarchyChangeListener QUIET =
            new android.view.ViewGroup.OnHierarchyChangeListener() {
                @Override public void onChildViewAdded(android.view.View parent, android.view.View child) {
                    quiet(child);
                }

                @Override public void onChildViewRemoved(android.view.View parent, android.view.View child) {
                }
            };

    /** Silences Android's click sound on a view tree, including views added to it later. */
    static void quiet(android.view.View v) {
        v.setSoundEffectsEnabled(false);
        if (!(v instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup g = (android.view.ViewGroup) v;
        // These widgets rely on their own hierarchy listener; their children are still silenced.
        if (!(g instanceof android.widget.RadioGroup) && !(g instanceof android.widget.TableLayout)) {
            g.setOnHierarchyChangeListener(QUIET);
        }
        for (int i = 0; i < g.getChildCount(); i++) quiet(g.getChildAt(i));
    }

    /**
     * A D-pad press nothing else used: move focus here, so Android doesn't (with its own sound).
     * Returns true when focus moved.
     */
    static boolean moveFocus(android.view.Window w, KeyEvent ev) {
        if (ev.getAction() != KeyEvent.ACTION_DOWN || w == null) return false;
        int dir;
        switch (ev.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP: dir = android.view.View.FOCUS_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dir = android.view.View.FOCUS_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: dir = android.view.View.FOCUS_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dir = android.view.View.FOCUS_RIGHT; break;
            default: return false;
        }
        android.view.View f = w.getDecorView().findFocus();
        if (f == null) return false;
        android.view.View n = f.focusSearch(dir);
        return n != null && n != f && n.requestFocus(dir);
    }

    /** Hume's dialog builder (system dark alert style), following the Sounds switch like the rest of Hume. */
    static android.app.AlertDialog.Builder dialog(Context c) {
        return new android.app.AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert) {
            @Override public android.app.AlertDialog create() {
                android.app.AlertDialog d = super.create();
                quiet(d);
                return d;
            }
        };
    }

    /** A dialog that follows the Sounds switch too (its own window). */
    static void quiet(android.app.Dialog d) {
        d.setOnShowListener(x -> {
            if (d.getWindow() != null) quiet(d.getWindow().getDecorView());
        });
    }
}
