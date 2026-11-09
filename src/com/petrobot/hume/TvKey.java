// Fichier ajouté dans le fork Hume Launcher+ : touche Réglages de la télécommande (Réglages rapides de la TV, ou Centre de contrôle sur l'accueil Hume).
package com.petrobot.hume;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;

/**
 * The remote's Settings key (TCL sends KEYCODE_NOTIFICATION; other remotes KEYCODE_SETTINGS).
 * It used to open Google TV's side panel, which belongs to the stock launcher (disabled here).
 * Caught by {@link PowerService}: on Hume's Home Screen it opens Control Center; anywhere else
 * (apps, HDMI, tuner) the TV's own light Quick Settings bar.
 */
final class TvKey {
    private TvKey() { }

    static boolean isSettingsKey(int code) {
        return code == KeyEvent.KEYCODE_NOTIFICATION || code == KeyEvent.KEYCODE_SETTINGS;
    }

    /** Runs on the key's release; {@code front} is the package on screen. */
    static void press(Context c, CharSequence front) {
        if (front != null && c.getPackageName().contentEquals(front)) {
            try {
                c.startActivity(new Intent(c, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra("panel", "cc_key"));
                return;
            } catch (RuntimeException ignored) {
            }
        }
        quickSettings(c);
    }

    /** The TV's Quick Settings bar (picture, sound, audio output, sleep timer…), or full Settings. */
    static void quickSettings(Context c) {
        Intent i = new Intent("com.tcl.settings.SHOW_WINDOW")
                .setComponent(new ComponentName("com.android.tv.settings", "com.tcl.settings.ShowWindowService"))
                .putExtra("Type", "quicksettings");
        try {
            if (c.startService(i) != null) return;
        } catch (RuntimeException ignored) {
        }
        try {
            if (c.startForegroundService(i) != null) return;
        } catch (RuntimeException ignored) {
        }
        Suggestions.start(c, new Intent(Settings.ACTION_SETTINGS));
    }
}
