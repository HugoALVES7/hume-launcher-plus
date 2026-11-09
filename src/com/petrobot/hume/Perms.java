// Modifié dans le fork Hume Launcher+ (par rapport à Hume Launcher de Petrobot) : textes traduits en français.
package com.petrobot.hume;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Permissions, asked for on screen wherever the TV allows it; adb is only the last resort.
 * - Location (Wi-Fi name): the normal runtime prompt.
 * - Device admin (Power Off, Sleep Timer): Android's "Activer l\u2019app d\u2019administration de l\u2019appareil ?" screen.
 * - TV listings (Continue Watching from apps' Watch Next rows, Android TV 8+): the normal runtime prompt.
 * - Notification access (Do Not Disturb): the system's notification-access screen (Android 11+
 *   opens Hume's own switch directly).
 * Some TVs (e.g. Android 9 builds) have no screen for the last two; only then is the
 * one-time computer command shown.
 */
final class Perms {
    static final int REQ_LOCATION = 41;
    static final int REQ_TV_LISTINGS = 43;

    private Perms() {
    }

    static ComponentName admin(Activity a) {
        return new ComponentName(a, SleepReceiver.Admin.class);
    }

    static ComponentName listener(Activity a) {
        return new ComponentName(a, MediaWatchService.class);
    }

    static boolean hasLocation(Activity a) {
        return a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Some TVs (e.g. this office TV's Android 9 build) lack device admin entirely; nothing can enable it. */
    /** Power Off is possible via device admin, or via Hume's accessibility service (any TV with that screen). */
    static boolean adminSupported(Activity a) {
        return adminWorks(a) || accessibilityScreen(a) != null;
    }

    static Intent accessibilityScreen(Activity a) {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        ComponentName cn = i.resolveActivity(a.getPackageManager());
        return cn == null || cn.getClassName().contains("Stub") ? null : i;
    }

    static boolean hasAdmin(Activity a) {
        return a.getSystemService(DevicePolicyManager.class).isAdminActive(admin(a)) || PowerService.instance != null;
    }

    /** Device admin actually works only when Android's device-policy service is on. */
    static boolean adminWorks(Activity a) {
        return a.getPackageManager().hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN);
    }

    /** Android turns notification listeners off for other apps on low-RAM (1 GB) TVs. */
    static boolean listenerSupported(Activity a) {
        return !a.getSystemService(android.app.ActivityManager.class).isLowRamDevice();
    }

    static boolean hasListener(Activity a) {
        return MediaWatchService.granted(a);
    }

    // ---------------------------------------------------------------- Continue Watching

    static boolean hasTvListings(Activity a) {
        return WatchNext.granted(a);
    }

    static void requestTvListings(Activity a) {
        if (hasTvListings(a)) return;
        if (!WatchNext.supported()) {
            Clicks.dialog(a)
                    .setTitle("Non disponible sur cette TV")
                    .setMessage("« À reprendre » lit les listes « À regarder ensuite » de vos apps, ce qui nécessite Android TV 8 ou plus récent.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        android.content.SharedPreferences p = a.getSharedPreferences("perms", Activity.MODE_PRIVATE);
        // Once refused with "Ne plus demander", Android answers without showing the prompt.
        if (p.getBoolean("asked_tv_listings", false) && !a.shouldShowRequestPermissionRationale(WatchNext.PERM)) {
            lastResort(a, "À reprendre",
                    "adb shell pm grant com.petrobot.hume android.permission.READ_TV_LISTINGS");
            return;
        }
        p.edit().putBoolean("asked_tv_listings", true).apply();
        a.requestPermissions(new String[]{WatchNext.PERM}, REQ_TV_LISTINGS);
    }

    /** Hume's accessibility switch is on (Power Off fallback, Clean, Home button, waking into an app). */
    static boolean hasAccessibility(Activity a) {
        return PowerService.instance != null;
    }

    /** Opens the TV's Accessibility settings with a short explanation of what the switch is for. */
    static void requestAccessibility(Activity a, String why) {
        if (hasAccessibility(a)) return;
        Intent acc = accessibilityScreen(a);
        if (acc == null) {
            lastResort(a, "Hume Launcher+ switch", "adb shell settings put secure enabled_accessibility_services "
                    + "com.petrobot.hume/com.petrobot.hume.PowerService\n"
                    + "adb shell settings put secure accessibility_enabled 1");
            return;
        }
        // Switched on but not running: Android stops it when the app is force-stopped and only
        // restarts it on reboot or when the switch is turned off and on again.
        String on = Settings.Secure.getString(a.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean stale = on != null && on.contains(a.getPackageName() + "/");
        Clicks.dialog(a)
                .setTitle(stale ? "Relancer l\u2019interrupteur de Hume" : "Activer l\u2019interrupteur de Hume")
                .setMessage(why + (stale
                        ? "\n\nIl est activé, mais Android l\u2019a mis en pause. Dans l\u2019écran suivant, choisissez \u201cHume Launcher+\u201d, désactivez-le puis réactivez-le."
                        : "\n\nDans l\u2019écran suivant, choisissez \u201cHume Launcher+\u201d et activez-le."))
                .setPositiveButton("Ouvrir Accessibilité", (d, w) -> tryStart(a, acc))
                .setNegativeButton("Annuler", null).show();
    }

    static void requestLocation(Activity a) {
        if (!hasLocation(a)) a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
    }

    static void requestAdmin(Activity a) {
        if (hasAdmin(a)) return;
        if (!adminSupported(a)) {
            Clicks.dialog(a)
                    .setTitle("Non disponible sur cette TV")
                    .setMessage("Cette TV ne laisse pas les apps éteindre l\u2019écran : Éteindre et la minuterie de veille ne peuvent pas fonctionner "
                            + "ici. Utilisez le bouton marche/arrêt de la télécommande.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (!adminWorks(a)) {
            // Device admin is switched off on this TV: use Hume's accessibility switch instead.
            Intent acc = accessibilityScreen(a);
            Clicks.dialog(a)
                    .setTitle("Activer Éteindre")
                    .setMessage("Dans l\u2019écran suivant, choisissez \u201cHume Launcher+\u201d et activez-le. "
                            + "Hume pourra mettre la TV en veille (et gérer le bouton Accueil et le démarrage avec la TV).")
                    .setPositiveButton("Ouvrir Accessibilité", (d, w) -> tryStart(a, acc))
                    .setNegativeButton("Annuler", null).show();
            return;
        }
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(a))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Permet à Hume Launcher+ d\u2019éteindre l\u2019écran pour Éteindre et la minuterie de veille. Rien d\u2019autre.");
        if (tryStart(a, i)) return;
        lastResort(a, "Éteindre et minuterie de veille",
                "adb shell dpm set-active-admin com.petrobot.hume/.SleepReceiver\\$Admin");
    }

    static void requestListener(Activity a) {
        if (hasListener(a)) return;
        if (!listenerSupported(a)) {
            Clicks.dialog(a)
                    .setTitle("Non disponible sur cette TV")
                    .setMessage("Cette TV a peu de mémoire, donc Android ne donne pas aux apps l\u2019accès aux notifications. "
                            + "Ne pas déranger ne peut pas fonctionner ici.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener(a).flattenToString());
            if (tryStart(a, detail)) return;
        }
        if (tryStart(a, new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) return;
        if (tryStart(a, new Intent("android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS"))) return;
        lastResort(a, "Ne pas déranger",
                "adb shell cmd notification allow_listener com.petrobot.hume/com.petrobot.hume.MediaWatchService");
    }

    /** Starts a system screen if the TV really has it (many TVs map settings actions to empty stubs). */
    private static boolean tryStart(Activity a, Intent i) {
        ComponentName cn = i.resolveActivity(a.getPackageManager());
        if (cn == null || cn.getClassName().contains("Stub")) return false;
        try {
            a.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- default home screen

    static boolean isDefaultHome(Activity a) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo ri = a.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && a.getPackageName().equals(ri.activityInfo.packageName);
    }

    /**
     * Become the home screen: Android 10+'s "Définir comme app d\u2019accueil par défaut ?" prompt, else the TV's Home
     * app settings (when it is a real screen), else Android's chooser, and only then the command.
     */
    static void requestDefaultHome(Activity a) {
        if (isDefaultHome(a)) return;
        if (Build.VERSION.SDK_INT >= 29) {
            android.app.role.RoleManager rm = a.getSystemService(android.app.role.RoleManager.class);
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                try {
                    a.startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), 42);
                    return;
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        if (tryStart(a, new Intent(Settings.ACTION_HOME_SETTINGS))) return;
        // With no default chosen yet, asking for Home shows Android's chooser ("Toujours" makes it stick).
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo def = a.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        boolean noDefault = def == null || def.activityInfo == null || "android".equals(def.activityInfo.packageName);
        if (noDefault && tryStart(a, home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return;
        // TVs that keep their own launcher (Google TV): Hume can take over the Home button instead.
        if (accessibilityScreen(a) != null || hasAccessibility(a)) {
            PowerService.HomeButton.setEnabled(a, true);
            requestAccessibility(a, "Cette TV ne permet pas de choisir une autre app d\u2019accueil. Avec l\u2019interrupteur de Hume activé, "
                    + "Hume s\u2019ouvre dès que l\u2019accueil de la TV apparaît : le bouton Accueil vous ramène donc ici.");
            return;
        }
        lastResort(a, "Écran d\u2019accueil",
                "adb shell cmd package set-home-activity com.petrobot.hume/.MainActivity\n"
                        + "(on Google TV also: adb shell pm disable-user --user 0 com.google.android.tvlauncher)");
    }

    /** Only when the TV has no screen for it: explain plainly, with the command for whoever installs the TV. */
    private static void lastResort(Activity a, String what, String cmd) {
        Clicks.dialog(a)
                .setTitle(what)
                .setMessage("Les réglages de cette TV n\u2019ont pas d\u2019interrupteur pour cette autorisation. La personne qui a installé la TV peut l\u2019activer "
                        + "une fois depuis un ordinateur du même réseau :\n\n" + cmd)
                .setPositiveButton("OK", null)
                .setNeutralButton("Infos sur l\u2019app", (d, w) -> tryStart(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + a.getPackageName()))))
                .show();
    }
}
