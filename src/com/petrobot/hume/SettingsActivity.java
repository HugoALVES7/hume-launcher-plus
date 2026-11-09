// Modifié dans le fork Hume Launcher+ (par rapport à Hume Launcher de Petrobot) : traduction, page PlayStation, avatar du profil, apps masquées.
package com.petrobot.hume;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * tvOS-style settings: a main list of sections, each opening its own page, with the page's icon
 * and title on the left and its rows on the right; Back returns to the main list.
 *
 * A TMDB token can also be supplied over adb:
 *   adb shell am start -n com.petrobot.hume/.SettingsActivity --es tmdb_token &lt;token&gt;
 * The PSN ID to follow likewise:
 *   adb shell am start -n com.petrobot.hume/.SettingsActivity --es psn_user &lt;id&gt;
 */
public class SettingsActivity extends Activity {

    LinearLayout list;
    final List<Runnable> refreshers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Clicks.init(this);
        Motion.init(this);
        String token = getIntent().getStringExtra("tmdb_token");
        if (token != null && !token.trim().isEmpty()) {
            Suggestions.prefs(this).edit().putString(Suggestions.K_TOKEN, token.trim()).apply();
            Suggestions.changed(this);
            Toast.makeText(this, "TMDB connecté", Toast.LENGTH_SHORT).show();
        }
        String psnUser = getIntent().getStringExtra("psn_user");
        if (psnUser != null && !psnUser.trim().isEmpty()) {
            Psn.setUser(this, psnUser);
            Psn.prefs(this).edit().putBoolean(Psn.K_ON, true).apply();
        }
        if (psnUser != null) Toast.makeText(this, "PlayStation mis à jour", Toast.LENGTH_SHORT).show();
        build();
        String pg = getIntent().getStringExtra("page");  // adb: --es page "Carrousel"
        if (pg != null) show(pg);
    }

    // tvOS-style pages: the main list opens a page per section; Back returns to the main list.
    static final String ROOT = "root", PROFILES = "Profils", SHELF = "Carrousel", PRESENT = "Présentation",
            INPUTS = "Sources", WEATHER = "Météo", PSN = "PlayStation", WATCHING = "À reprendre", HOME = "Accueil",
            PERMS = "Autorisations", ABOUT = "À propos";

    FrameLayout leftPane;
    ScrollView scroller;
    String page = ROOT, lastPage = null;
    final java.util.Map<String, android.view.View> navRows = new java.util.HashMap<>();

    void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0B10);
        LinearLayout split = new LinearLayout(this);
        split.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(split, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        leftPane = new FrameLayout(this);
        split.addView(leftPane, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setClipChildren(false);
        scroller.setPadding(0, Ui.px(this, 60), Ui.px(this, 44), Ui.px(this, 60));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(Ui.px(this, 12), 0, Ui.px(this, 12), 0);
        scroller.addView(list);
        split.addView(scroller, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.25f));
        setContentView(root);
        Clicks.quiet(root);
        show(ROOT);
    }

    /** Builds one page: its icon and title on the left, its rows on the right. */
    void show(String p) {
        lastPage = page;
        page = p;
        list.removeAllViews();
        rowsByLabel.clear();
        refreshers.clear();
        navRows.clear();
        buildLeft(p);
        switch (p) {
            case PROFILES: pageProfiles(); break;
            case SHELF: pageShelf(); break;
            case PRESENT: pagePresentation(); break;
            case INPUTS: pageInputs(); break;
            case WEATHER: pageWeather(); break;
            case PSN: pagePsn(); break;
            case WATCHING: pageShelf(); break;  // older deep links: Continue Watching now lives in Top Shelf
            case HOME: pageHome(); break;
            case PERMS: pagePermissions(); break;
            case ABOUT: pageAbout(); break;
            default: pageRoot(); break;
        }
        scroller.scrollTo(0, 0);
        // Returning to the main list puts focus back on the section you came from.
        android.view.View focus = p.equals(ROOT) && lastPage != null ? navRows.get(lastPage) : null;
        if (focus == null) {
            for (int i = 0; i < list.getChildCount(); i++) {
                if (list.getChildAt(i).isFocusable()) {
                    focus = list.getChildAt(i);
                    break;
                }
            }
        }
        if (focus != null) focus.requestFocus();
        list.setAlpha(0f);
        list.setTranslationX(Motion.dist(Ui.px(this, p.equals(ROOT) ? -16 : 16)));
        list.animate().alpha(1f).translationX(0).setDuration(Motion.d(220)).start();
        // Rows settle in one after another (tvOS Settings).
        List<android.view.View> rows = new ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) rows.add(list.getChildAt(i));
        Motion.cascade(rows, Ui.px(this, 8));
    }

    @Override
    public void onBackPressed() {
        if (!page.equals(ROOT)) show(ROOT);
        else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();  // e.g. a permission granted on a system screen
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (SHELF.equals(page)) show(SHELF);  // allowed: the sources' options appear
        else refreshAll();
    }

    /** Left side: the page's icon (Petrobot on the main list), its title, and the brand line. */
    void buildLeft(String p) {
        leftPane.removeAllViews();
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        android.view.View icon;
        if (p.equals(ROOT)) {
            // Petrobot's mark on a white 5:3 tile, the same shape and radius as the Home cards.
            FrameLayout tile = new FrameLayout(this);
            GradientDrawable plate = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFFFFFFFF, 0xFFE9EAEE});
            plate.setCornerRadius(Ui.px(this, 12));
            tile.setBackground(plate);
            ImageView mark = new ImageView(this);
            mark.setImageResource(R.drawable.petrobot_mark);
            mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(mark, new FrameLayout.LayoutParams(Ui.px(this, 58), Ui.px(this, 58), Gravity.CENTER));
            icon = tile;
        } else {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(pageIcon(p, Ui.px(this, 150), Ui.px(this, 90)));
            iv.setClipToOutline(true);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(Ui.px(this, 12));
            iv.setBackground(shape);
            icon = iv;
        }
        icon.setElevation(Ui.px(this, 8));
        left.addView(icon, new LinearLayout.LayoutParams(Ui.px(this, 150), Ui.px(this, 90)));

        TextView title = new TextView(this);
        title.setText(p.equals(ROOT) ? "Réglages" : p);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Ui.font(this, "display"));
        title.setLetterSpacing(-0.02f);
        Ui.textSize(title, p.equals(ROOT) ? 34 : 28);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.px(this, 22);
        left.addView(title, tlp);

        if (p.equals(ROOT)) {
            TextView by = new TextView(this);
            by.setText("Hume Launcher+ \u00b7 d\u2019après Petrobot");
            by.setTextColor(0x8CFFFFFF);
            by.setTypeface(Ui.font(this, "regular"));
            Ui.textSize(by, 13);
            by.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = Ui.px(this, 4);
            left.addView(by, blp);
        }
        leftPane.addView(left, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    /** A 5:3 icon for a section page: a gradient tile with a white glyph (or the section's own art). */
    android.graphics.Bitmap pageIcon(String p, int w, int h) {
        if (p.equals(PRESENT)) return com.petrobot.hume.present.PresentIcon.banner(w, h);
        int[] grad;
        String glyph;
        switch (p) {
            case PROFILES: grad = new int[]{0xFFBF5AF2, 0xFF7D3CCB}; glyph = null; break;
            case SHELF: grad = new int[]{0xFF64D2FF, 0xFF0A84FF}; glyph = "shelf"; break;
            case INPUTS: grad = new int[]{0xFF6E6E73, 0xFF2C2C2E}; glyph = "input"; break;
            case WEATHER: grad = new int[]{0xFF5AC8FA, 0xFF2D6BD8}; glyph = null; break;
            case WATCHING: grad = new int[]{0xFFFF6482, 0xFFD6305A}; glyph = "present"; break;
            case HOME: grad = new int[]{0xFF30D158, 0xFF1E9E48}; glyph = "house"; break;
            case PERMS: grad = new int[]{0xFF8E8E93, 0xFF48484A}; glyph = "shield"; break;
            default: grad = new int[]{0xFF8E8E93, 0xFF3A3A3C}; glyph = "gear"; break;
        }
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new android.graphics.LinearGradient(0, 0, 0, h, grad[0], grad[1], android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        android.graphics.Bitmap g;
        if (p.equals(PROFILES)) g = Profiles.avatar(this, Profiles.current(this), (int) (h * 0.62f));
        else if (p.equals(WEATHER)) g = WeatherIcons.get(0, true, (int) (h * 0.7f));
        else g = Glyphs.get(glyph, (int) (h * 0.56f), Color.WHITE);
        c.drawBitmap(g, (w - g.getWidth()) / 2f, (h - g.getHeight()) / 2f, null);
        return b;
    }

    // ---------------------------------------------------------------- pages

    void pageRoot() {
        nav(PROFILES, () -> Profiles.current(this).name);
        nav(SHELF, () -> {
            // What the carousel shows, in its order: Continue Watching, apps' rows, recommendations, news.
            List<String> on = new ArrayList<>();
            if (cwOn()) on.add("À reprendre");
            if (appRowsOn()) on.add("Apps");
            if (Suggestions.showRecs(this)) on.add("Recommandations");
            if (Suggestions.showNews(this)) on.add("Actualités");
            return on.isEmpty() ? "Désactivé" : on.size() > 2 ? on.size() + " sources" : String.join(" et ", on);
        });
        nav(PRESENT, () -> com.petrobot.hume.present.Presenter.enabled(this) ? "Activé" : "Désactivé");
        if (!Inputs.list(this).isEmpty()) nav(INPUTS, () -> Inputs.showCard(this) ? "Activé" : "Désactivé");
        nav(WEATHER, () -> !Weather.enabled(this) ? "Désactivé" : Weather.place(this).isEmpty() ? "Activé" : Weather.place(this));
        nav(PSN, () -> !Psn.enabled(this) ? "Désactivé" : Psn.user(this).isEmpty() ? "Activé · aucun ID PSN" : Psn.user(this));
        nav(HOME, () -> {
            String k = Profiles.homePrefs(this).getString("wallpaper", "aurora");
            int i = java.util.Arrays.asList(MainActivity.WALL_KEYS).indexOf(k);
            return MainActivity.WALL_NAMES[Math.max(0, i)];
        });
        nav(PERMS, () -> {
            int missing = (Perms.hasListener(this) || !Perms.listenerSupported(this) ? 0 : 1) + (Perms.hasLocation(this) ? 0 : 1)
                    + (Perms.hasTvListings(this) || !WatchNext.supported() ? 0 : 1)
                    + (Perms.hasAdmin(this) || !Perms.adminSupported(this) ? 0 : 1) + (Perms.isDefaultHome(this) ? 0 : 1);
            return missing == 0 ? "Toutes accordées" : missing + " à accorder";
        });
        nav(ABOUT, () -> "Version " + version());
        row("Réglages de la TV", () -> "", () -> Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS)));
    }

    void pageProfiles() {
        row("Profil actuel", () -> Profiles.current(this).name, () -> {
            startActivity(new Intent(this, MainActivity.class).putExtra("pick_profile", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        row("Avatar", () -> Avatars.name(this, Profiles.current(this).pic), () -> {
            Profiles.Profile me = Profiles.current(this);
            Avatars.pick(this, me.pic, picked -> {
                me.pic = picked;
                Profiles.update(this, me);
                showAndFocus(PROFILES, "Avatar");  // new name here, new picture on the page icon
            });
        });
        row("Choisir le profil au réveil", () -> Profiles.askOnWake(this) ? "Activé" : "Désactivé",
                () -> Profiles.setAskOnWake(this, !Profiles.askOnWake(this)));
    }

    /** Continue Watching shows for this profile (switched on and allowed). */
    boolean cwOn() {
        return Profiles.homePrefs(this).getBoolean("cw_enabled", true) && WatchNext.granted(this);
    }

    boolean appRowsOn() {
        return Profiles.homePrefs(this).getBoolean("app_rows", true) && WatchNext.granted(this);
    }

    /** On / Off for a source that reads the TV's lists, or what it still needs. */
    String tvSourceState(String key) {
        if (!WatchNext.supported()) return "Pas sur cette TV";
        if (!WatchNext.granted(this)) return "Autoriser\u2026";
        return Profiles.homePrefs(this).getBoolean(key, true) ? "Activé" : "Désactivé";
    }

    /** Toggles a TV-list source; asks for the TV-listings permission first when it's missing. */
    void toggleTvSource(String key, String label) {
        if (!WatchNext.supported()) {
            Toast.makeText(this, label + " nécessite Android TV 8 ou plus récent.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!WatchNext.granted(this)) {
            Profiles.homePrefs(this).edit().putBoolean(key, true).apply();
            Perms.requestTvListings(this);
            return;
        }
        Profiles.homePrefs(this).edit().putBoolean(key, !Profiles.homePrefs(this).getBoolean(key, true)).apply();
        AppRows.invalidate();
        showAndFocus(SHELF, label);  // each source's options appear or go
    }

    /**
     * Top Shelf: every source of the carousel as a switch, in the carousel's own order, then each
     * one's options. (Continue Watching used to have its own page; it plays here, so it lives here.)
     */
    void pageShelf() {
        row("À reprendre", () -> tvSourceState("cw_enabled"), () -> toggleTvSource("cw_enabled", "À reprendre"));
        row("Depuis vos apps", () -> tvSourceState("app_rows"), () -> toggleTvSource("app_rows", "Depuis vos apps"));
        row("Recommandations", () -> Suggestions.showRecs(this) ? "Activé" : "Désactivé", () -> {
            Suggestions.setShelf(this, !Suggestions.showRecs(this), Suggestions.showNews(this));
            showAndFocus(SHELF, "Recommandations");
        });
        row("Actualités", () -> Suggestions.showNews(this) ? "Activé" : "Désactivé", () -> {
            Suggestions.setShelf(this, Suggestions.showRecs(this), !Suggestions.showNews(this));
            showAndFocus(SHELF, "Actualités");
        });
        if (cwOn()) {
            section("À reprendre");
            row("Effacer l\u2019historique", () -> "", () -> {
                WatchNext.dismissAll(this);
                Toast.makeText(this, "« À reprendre » effacé", Toast.LENGTH_SHORT).show();
            });
        }
        if (appRowsOn()) {
            section("Depuis vos apps");
            row("Apps", () -> {
                int n = AppRows.hidden(this).size();
                return n == 0 ? "Toutes" : n + (n == 1 ? " masquée" : " masquées");
            }, this::chooseRowApps);
        }
        if (Suggestions.showRecs(this)) {
            section("Recommandations");
            row("Source", () -> Suggestions.sourceName(Suggestions.source(this)), this::chooseSource);
            row("Contenu", () -> {
                String t = Suggestions.types(this);
                return t.equals("movie") ? "Films" : t.equals("tv") ? "Séries" : "Films et séries";
            }, this::chooseTypes);
        }
        if (Suggestions.showRecs(this) && Suggestions.source(this).equals(Suggestions.SRC_STREMIO)) {
            row("S\u2019ouvre dans", () -> {
                Suggestions.Provider p = Suggestions.stremioApp(this);
                return Suggestions.installed(this, p.pkg) ? p.name : "Stremio (non installé)";
            }, () -> Toast.makeText(this, "Les titres s\u2019ouvrent dans Stremio, ou dans Nuvio si seul Nuvio est installé.",
                    Toast.LENGTH_LONG).show());
        } else if (Suggestions.showRecs(this)) {
            row("Services de streaming", () -> {
                int n = Suggestions.providers(this).size();
                return n == Suggestions.PROVIDERS.size() ? "Tous" : n + (n == 1 ? " sélectionné" : " sélectionnés");
            }, this::chooseProviders);
            row("Langues", () -> {
                Set<String> l = Suggestions.languages(this);
                if (l.isEmpty()) return "Toutes";
                List<String> names = new ArrayList<>();
                for (int i = 0; i < Suggestions.LANG_CODES.length; i++) {
                    if (l.contains(Suggestions.LANG_CODES[i])) names.add(Suggestions.LANG_NAMES[i]);
                }
                return names.size() > 2 ? names.size() + " langues" : String.join(", ", names);
            }, this::chooseLanguages);
            row("Jeton TMDB", () -> Suggestions.hasToken(this) ? "Connecté" : "Non défini", this::enterToken);
        }
        if (Suggestions.showNews(this)) {
            section("Actualités");
            row("Rédactions", () -> {
                int n = News.sources(this).size();
                return n == News.SOURCES.size() ? "All" : n + " selected";
            }, this::chooseNewsrooms);
            row("Infos locales pour", () -> News.city(this).isEmpty() ? "Inconnue" : News.city(this), () ->
                    Toast.makeText(this, "Les infos locales suivent le lieu de la météo (Réglages \u2192 Météo \u2192 Lieu).",
                            Toast.LENGTH_LONG).show());
            row("Actualiser les infos", () -> "Toutes les " + News.refreshMinutes(this) + " minutes", () -> {
                int[] mins = News.REFRESH_CHOICES;
                String[] names = new String[mins.length];
                int sel = 1;
                for (int i = 0; i < mins.length; i++) {
                    names[i] = mins[i] + " minutes" + (mins[i] == 30 ? "  (par défaut)" : "");
                    if (mins[i] == News.refreshMinutes(this)) sel = i;
                }
                dialog("Actualiser les infos").setSingleChoiceItems(names, sel, (d, w) -> {
                    Suggestions.prefs(this).edit().putInt(News.K_REFRESH, mins[w]).apply();
                    refreshAll();
                    d.dismiss();
                }).show();
            });
        }
        if (Suggestions.enabled(this)) {
            section("Carrousel");
            row("Défilement automatique", () -> {
                int sec = Suggestions.advanceSeconds(this);
                return sec == 0 ? "Désactivé" : "Toutes les " + sec + " s";
            }, this::chooseAdvance);
            row("Actualiser maintenant", () -> "", () -> {
                Suggestions.changed(this);
                Toast.makeText(this, "Le carrousel s\u2019actualisera sur l\u2019accueil", Toast.LENGTH_SHORT).show();
            });
        }
    }

    /** Which apps' rows the carousel uses (apps that publish rows on this TV). */
    void chooseRowApps() {
        new Thread(() -> {
            List<String> pkgs = new ArrayList<>(AppRows.load(this).byApp.keySet());
            runOnUiThread(() -> {
                if (pkgs.isEmpty()) {
                    Toast.makeText(this, "Aucune de vos apps ne publie encore de rangées sur cette TV. Ouvrez-les une fois, puis revenez ici.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                String[] names = new String[pkgs.size()];
                boolean[] on = new boolean[names.length];
                Set<String> hidden = AppRows.hidden(this);
                for (int i = 0; i < names.length; i++) {
                    try {
                        names[i] = String.valueOf(getPackageManager().getApplicationLabel(
                                getPackageManager().getApplicationInfo(pkgs.get(i), 0)));
                    } catch (Exception e) {
                        names[i] = pkgs.get(i);
                    }
                    on[i] = !hidden.contains(pkgs.get(i));
                }
                dialog("Apps dans le carrousel").setMultiChoiceItems(names, on, (d, w, checked) -> on[w] = checked)
                        .setPositiveButton("Terminé", (d, w) -> {
                            for (int i = 0; i < on.length; i++) AppRows.setHidden(this, pkgs.get(i), !on[i]);
                            refreshAll();
                        }).setNegativeButton("Annuler", null).show();
            });
        }).start();
    }

    void chooseNewsrooms() {
        String[] names = new String[News.SOURCES.size()];
        boolean[] on = new boolean[names.length];
        Set<String> cur = News.sources(this);
        for (int i = 0; i < names.length; i++) {
            names[i] = News.SOURCES.get(i).name;
            on[i] = cur.contains(News.SOURCES.get(i).id);
        }
        dialog("Rédactions").setMultiChoiceItems(names, on, (d, w, checked) -> on[w] = checked)
                .setPositiveButton("Terminé", (d, w) -> {
                    Set<String> ids = new java.util.HashSet<>();
                    for (int i = 0; i < on.length; i++) if (on[i]) ids.add(News.SOURCES.get(i).id);
                    if (ids.isEmpty()) ids.add("toi");
                    News.setSources(this, ids);
                    Suggestions.changed(this);
                    refreshAll();
                }).setNegativeButton("Annuler", null).show();
    }

    void pagePresentation() {
        row("Mode Présentation", () -> com.petrobot.hume.present.Presenter.enabled(this) ? "Activé" : "Désactivé", () -> {
            boolean on = !com.petrobot.hume.present.Presenter.enabled(this);
            com.petrobot.hume.present.Presenter.set(this, com.petrobot.hume.present.Presenter.KEY_ENABLED, on);
            if (on) Toast.makeText(this, "La présentation est activée. Envoyez du contenu depuis un navigateur.", Toast.LENGTH_LONG).show();
        });
        row("Démarrer avec la TV", () -> com.petrobot.hume.present.Presenter.startOnBoot(this) ? "Activé" : "Désactivé", () ->
                com.petrobot.hume.present.Presenter.set(this, com.petrobot.hume.present.Presenter.KEY_BOOT,
                        !com.petrobot.hume.present.Presenter.startOnBoot(this)));
        row("Garder l\u2019écran allumé", () -> com.petrobot.hume.present.Presenter.keepAwake(this) ? "Activé" : "Désactivé", () ->
                com.petrobot.hume.present.Presenter.set(this, com.petrobot.hume.present.Presenter.KEY_AWAKE,
                        !com.petrobot.hume.present.Presenter.keepAwake(this)));
        row("Adresse et code d\u2019appairage", () -> com.petrobot.hume.present.Presenter.enabled(this)
                ? com.petrobot.hume.present.Presenter.address().replace("http://", "") : "À activer d\u2019abord", this::showPresentInfo);
        row("Lancer la présentation", () -> "", () -> {
            if (!com.petrobot.hume.present.Presenter.enabled(this)) {
                com.petrobot.hume.present.Presenter.set(this, com.petrobot.hume.present.Presenter.KEY_ENABLED, true);
            }
            com.petrobot.hume.present.Presenter.open(this);
        });
    }

    void pageInputs() {
        row("Carte Sources sur l\u2019accueil", () -> Inputs.showCard(this) ? "Activé" : "Désactivé", () ->
                Inputs.prefs(this).edit().putBoolean(Inputs.K_CARD, !Inputs.showCard(this)).apply());
        section("Cette TV");
        for (Inputs.Input in : Inputs.list(this)) {
            row(in.label, () -> in.disconnected ? "Non connecté" : "", () -> {
                if (!Inputs.open(this, in)) Toast.makeText(this, "Impossible de passer sur " + in.label, Toast.LENGTH_SHORT).show();
            });
        }
    }

    /** PSN pill: off by default; the PSN ID to follow (stats from PSNProfiles, no sign-in). */
    void pagePsn() {
        row("Afficher les stats PlayStation", () -> Psn.enabled(this) ? "Activé" : "Désactivé", () -> {
            Psn.prefs(this).edit().putBoolean(Psn.K_ON, !Psn.enabled(this)).apply();
            Psn.changed(this);
            if (Psn.enabled(this) && Psn.user(this).isEmpty()) enterPsnUser();
        });
        row("ID PSN", () -> Psn.user(this).isEmpty() ? "Non défini" : Psn.user(this), this::enterPsnUser);
        row("État", () -> {
            if (!Psn.enabled(this)) return "Désactivé";
            String e = Psn.error(this);
            Psn.Data d = Psn.cached(this);
            if (!e.isEmpty()) return e;
            if (d == null) return "En attente de la première mise à jour";
            return "Mis à jour à " + android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(d.at));
        }, () -> {
            Psn.changed(this);
            Toast.makeText(this, "Se met à jour au retour sur l\u2019accueil", Toast.LENGTH_SHORT).show();
        });
    }

    void enterPsnUser() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint("ID en ligne, par ex. Joueur_PS");
        in.setText(Psn.user(this));
        in.setSelection(in.getText().length());
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        int pad = Ui.px(this, 14);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        dialog("ID PSN")
                .setMessage("L\u2019ID PlayStation Network à suivre. Ses trophées doivent être visibles par tous.")
                .setView(box)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    Psn.setUser(this, in.getText().toString());
                    Psn.prefs(this).edit().putBoolean(Psn.K_ON, true).apply();
                    refreshAll();
                })
                .setNegativeButton("Annuler", null)
                .show();
        in.requestFocus();
    }

    void pageWeather() {
        row("Afficher la météo", () -> Weather.enabled(this) ? "Activé" : "Désactivé", () -> {
            Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, !Weather.enabled(this)).apply();
            Weather.changed(this);
        });
        row("Lieu", () -> Weather.manual(this) ? Weather.place(this)
                : Weather.place(this).isEmpty() ? "Automatique" : "Automatique \u00b7 " + Weather.place(this), this::chooseLocation);
        row("Température", () -> {
            String u = Weather.prefs(this).getString(Weather.K_UNITS, "auto");
            return u.equals("c") ? "Celsius" : u.equals("f") ? "Fahrenheit" : "Automatique";
        }, () -> {
            String[] keys = {"auto", "c", "f"};
            String[] names = {"Automatique", "Celsius (\u00b0C)", "Fahrenheit (\u00b0F)"};
            String cur = Weather.prefs(this).getString(Weather.K_UNITS, "auto");
            int sel = cur.equals("c") ? 1 : cur.equals("f") ? 2 : 0;
            dialog("Température").setSingleChoiceItems(names, sel, (d, w) -> {
                Weather.prefs(this).edit().putString(Weather.K_UNITS, keys[w]).apply();
                Weather.changed(this);
                refreshAll();
                d.dismiss();
            }).show();
        });
        section("Mises à jour");
        row("Source", () -> Weather.sourceName(Weather.source(this)), () -> {
            String[] keys = Weather.SOURCES;
            String[] names = {"Open-Meteo", "MET Norway (Yr)", "wttr.in  (3 jours)"};
            int sel = java.util.Arrays.asList(keys).indexOf(Weather.source(this));
            dialog("Source météo").setSingleChoiceItems(names, Math.max(0, sel), (d, w) -> {
                Weather.prefs(this).edit().putString(Weather.K_SOURCE, keys[w]).apply();
                Weather.changed(this);
                refreshAll();
                d.dismiss();
                Toast.makeText(this, "Si elle est injoignable, les autres sources prennent le relais.", Toast.LENGTH_LONG).show();
            }).show();
        });
        row("Actualiser", () -> Weather.refreshMinutes(this) + " minutes", () -> {
            int[] mins = Weather.REFRESH_CHOICES;
            String[] names = new String[mins.length];
            int sel = 1;
            for (int i = 0; i < mins.length; i++) {
                names[i] = mins[i] + " minutes" + (mins[i] == 15 ? "  (par défaut)" : "");
                if (mins[i] == Weather.refreshMinutes(this)) sel = i;
            }
            dialog("Actualiser la météo").setSingleChoiceItems(names, sel, (d, w) -> {
                Weather.prefs(this).edit().putInt(Weather.K_REFRESH, mins[w]).apply();
                refreshAll();
                d.dismiss();
            }).show();
        });
    }

    /** Apps hidden completely from Home: pick one to put it back on the Home screen. */
    void showRemovedApps() {
        android.content.SharedPreferences hp = Profiles.homePrefs(this);
        java.util.List<String> keys = new java.util.ArrayList<>(hp.getStringSet(MainActivity.PREF_REMOVED, java.util.Collections.emptySet()));
        if (keys.isEmpty()) {
            Toast.makeText(this, "Aucune app masquée", Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.pm.PackageManager pm = getPackageManager();
        String[] names = new String[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(keys.get(i));
            String label = keys.get(i);
            try {
                if (cn != null) label = pm.getActivityInfo(cn, 0).loadLabel(pm).toString();
            } catch (Exception ignored) {  // uninstalled since: shown by its name
            }
            names[i] = label;
        }
        dialog("Remettre sur l\u2019accueil").setItems(names, (d, w) -> {
            java.util.Set<String> rm = new java.util.HashSet<>(hp.getStringSet(MainActivity.PREF_REMOVED, java.util.Collections.emptySet()));
            rm.remove(keys.get(w));
            hp.edit().putStringSet(MainActivity.PREF_REMOVED, rm).apply();
            Toast.makeText(this, names[w] + " est de retour sur l\u2019accueil", Toast.LENGTH_SHORT).show();
            showAndFocus(HOME, "Apps masquées");
        }).show();
    }

    void pageHome() {
        row("Écran d\u2019accueil", () -> Perms.isDefaultHome(this) ? "Hume Launcher+" : "Faire de Hume l\u2019écran d\u2019accueil\u2026",
                () -> Perms.requestDefaultHome(this));
        row("Fond d\u2019écran", () -> {
            String k = Profiles.homePrefs(this).getString("wallpaper", "aurora");
            int i = java.util.Arrays.asList(MainActivity.WALL_KEYS).indexOf(k);
            return MainActivity.WALL_NAMES[Math.max(0, i)];
        }, () -> {
            String k = Profiles.homePrefs(this).getString("wallpaper", "aurora");
            int cur = Math.max(0, java.util.Arrays.asList(MainActivity.WALL_KEYS).indexOf(k));
            dialog("Fond d\u2019écran").setSingleChoiceItems(MainActivity.WALL_NAMES, cur, (d, w) -> {
                Profiles.homePrefs(this).edit().putString("wallpaper", MainActivity.WALL_KEYS[w]).apply();
                refreshAll();
                d.dismiss();
            }).show();
        });
        row("Apps masquées", () -> {
            int n = Profiles.homePrefs(this).getStringSet(MainActivity.PREF_REMOVED, java.util.Collections.emptySet()).size();
            return n == 0 ? "Aucune" : String.valueOf(n);
        }, this::showRemovedApps);
        row("Le bouton Accueil ouvre Hume", () -> PowerService.HomeButton.isDefaultHome(this) ? "Pas nécessaire"
                : !PowerService.HomeButton.enabled(this) ? "Désactivé"
                : Perms.hasAccessibility(this) ? "Activé" : "Nécessite l\u2019interrupteur de Hume\u2026", this::toggleHomeButton);
        section("Démarrage avec la TV");
        row("Ouvrir", () -> Startup.label(this), this::chooseStartup);
        if (!Startup.target(this).isEmpty()) {
            row("Quand", () -> Startup.onWake(this) ? "À l\u2019allumage ou au réveil" : "À l\u2019allumage", () -> {
                String[] names = {"À l\u2019allumage", "À l\u2019allumage ou en sortie de veille"};
                dialog("Ouvrir").setSingleChoiceItems(names, Startup.onWake(this) ? 1 : 0, (d, w) -> {
                    Startup.setOnWake(this, w == 1);
                    d.dismiss();
                    refreshAll();
                    if (w == 1 && !Perms.hasAccessibility(this)) {
                        Perms.requestAccessibility(this, "Android ne laisse Hume ouvrir une app au réveil de la TV "
                                + "que si l\u2019interrupteur d\u2019accessibilité de Hume est activé.");
                    }
                }).show();
            });
            if (Startup.onWake(this) && !Perms.hasAccessibility(this)) {
                row("Ouverture au réveil", () -> "Nécessite l\u2019interrupteur de Hume\u2026", () -> Perms.requestAccessibility(this,
                        "Android ne laisse Hume ouvrir une app au réveil de la TV que si l\u2019interrupteur d\u2019accessibilité de Hume est activé."));
            }
            if (com.petrobot.hume.present.Presenter.enabled(this) && com.petrobot.hume.present.Presenter.startOnBoot(this)) {
                row("Au démarrage", () -> "La présentation démarre à la place", () -> show(PRESENT));
            }
        }
        section("Animations et sons");
        row("Animations", () -> Motion.NAMES[Motion.level()], () -> {
            String[] names = {"Complètes  (par défaut)", "Réduites \u2014 plus courtes, sans dérive ni balancement", "Désactivées \u2014 changements instantanés"};
            dialog("Animations").setSingleChoiceItems(names, Motion.level(), (d, w) -> {
                Motion.set(this, w);
                d.dismiss();
                refreshAll();
            }).show();
        });
        row("Sons de navigation", () -> Clicks.enabled(this) ? "Activé" : "Désactivé",
                () -> Clicks.setEnabled(this, !Clicks.enabled(this)));
        section("Inactivité");
        row("Carrousel plein écran en cas d\u2019inactivité", () -> {
            int sec = Profiles.homePrefs(this).getInt("idle_sec", 20);
            return sec == 0 ? "Désactivé" : "Après " + sec + " s";
        }, () -> {
            int[] secs = {0, 10, 20, 30, 60};
            String[] names = {"Désactivé", "Après 10 secondes", "Après 20 secondes  (par défaut)", "Après 30 secondes", "Après 1 minute"};
            int cur = Profiles.homePrefs(this).getInt("idle_sec", 20), sel = 2;
            for (int i = 0; i < secs.length; i++) if (secs[i] == cur) sel = i;
            dialog("Carrousel plein écran").setMessage(null).setSingleChoiceItems(names, sel, (d, w) -> {
                Profiles.homePrefs(this).edit().putInt("idle_sec", secs[w]).apply();
                refreshAll();
                d.dismiss();
            }).show();
        });
        section("Économiseur d\u2019écran");
        row("Économiseur sur l\u2019accueil", () -> screensaverAllowed() ? "Économiseur du système" : "Carrousel Hume", () -> {
            String[] names = {"Économiseur du système  (" + dreamLabel() + ")", "Carrousel Hume en plein écran"};
            dialog("Économiseur sur l\u2019accueil").setSingleChoiceItems(names, screensaverAllowed() ? 0 : 1, (d, w) -> {
                getSharedPreferences("screensaver", MODE_PRIVATE).edit().putBoolean("allow", w == 0).apply();
                refreshAll();
                d.dismiss();
                Toast.makeText(this, w == 0 ? "L\u2019économiseur de la TV démarre quand l\u2019accueil est inactif"
                        : "L\u2019accueil reste affiché et lance le carrousel en plein écran à la place", Toast.LENGTH_LONG).show();
            }).show();
        });
        row("Économiseur d\u2019écran", () -> dreamLabel(), this::openDreamSettings);
        row("Lancer l\u2019économiseur", () -> "", () -> {
            Intent i = new Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (!Suggestions.start(this, i)) Toast.makeText(this, "Cette TV ne permet pas à une app de lancer l\u2019économiseur", Toast.LENGTH_LONG).show();
        });
        section("Recherche");
        row("Films et séries dans la recherche", () -> Search.titlesEnabled(this) ? "Activé" : "Désactivé", () -> {
            Search.prefs(this).edit().putBoolean(Search.K_TITLES, !Search.titlesEnabled(this)).apply();
            refreshAll();
        });
        row("Recherches récentes", () -> Search.recent(this).isEmpty() ? "Aucune" : "Clear", () -> {
            Search.clearRecent(this);
            refreshAll();
        });
        section("Configuration");
        row("Relancer la configuration", () -> "", () -> startActivity(new Intent(this, SetupActivity.class)));
        row("Réinitialiser la disposition des apps", () -> "", () -> {
            Profiles.homePrefs(this).edit().remove("order").apply();
            Toast.makeText(this, "Disposition réinitialisée", Toast.LENGTH_SHORT).show();
        });
    }

    /** Home button override: on/off, asking for the accessibility switch when it's needed. */
    void toggleHomeButton() {
        if (PowerService.HomeButton.isDefaultHome(this)) {
            Toast.makeText(this, "Hume est déjà l\u2019app d\u2019accueil : le bouton Accueil l\u2019ouvre.", Toast.LENGTH_LONG).show();
            return;
        }
        boolean on = PowerService.HomeButton.enabled(this);
        if (on && !Perms.hasAccessibility(this)) {
            Perms.requestAccessibility(this, "Cette TV garde son propre écran d\u2019accueil. Avec l\u2019interrupteur de Hume activé, Hume s\u2019ouvre "
                    + "dès que cet écran apparaît : le bouton Accueil vous ramène donc ici.");
            return;
        }
        PowerService.HomeButton.setEnabled(this, !on);
        refreshAll();
    }

    /** Start with the TV: Home Screen, a TV input, or an app. */
    void chooseStartup() {
        List<String> targets = new ArrayList<>(), names = new ArrayList<>();
        targets.add("");
        names.add("Écran d\u2019accueil  (par défaut)");
        for (Inputs.Input in : Inputs.list(this)) {
            if (in.usb) continue;
            targets.add("input:" + in.id);
            names.add(in.label);
        }
        for (String[] app : Startup.apps(this)) {
            targets.add("app:" + app[0]);
            names.add(app[1]);
        }
        int sel = Math.max(0, targets.indexOf(Startup.target(this)));
        dialog("À l\u2019allumage, ouvrir").setSingleChoiceItems(names.toArray(new String[0]), sel, (d, w) -> {
            Startup.set(this, targets.get(w), w == 0 ? "" : names.get(w));
            d.dismiss();
            showAndFocus(HOME, "Ouvrir");
        }).show();
    }

    void pagePermissions() {
        row("Bouton Accueil et démarrage avec la TV", () -> Perms.hasAccessibility(this) ? "Autorisé" : "Autoriser\u2026",
                () -> Perms.requestAccessibility(this, "L\u2019interrupteur de Hume lui permet de s\u2019ouvrir quand l\u2019écran d\u2019accueil "
                        + "de la TV apparaît, et d\u2019ouvrir l\u2019app de votre choix au réveil."));
        row("À reprendre (listes de vos apps)", () -> Perms.hasTvListings(this) ? "Autorisé"
                : WatchNext.supported() ? "Autoriser\u2026" : "Pas sur cette TV", () -> Perms.requestTvListings(this));
        row("Ne pas déranger", () -> Perms.hasListener(this) ? "Autorisé"
                : Perms.listenerSupported(this) ? "Autoriser\u2026" : "Pas sur cette TV",
                () -> Perms.requestListener(this));
        row("Éteindre et minuterie de veille", () -> Perms.hasAdmin(this) ? "Autorisé"
                : Perms.adminSupported(this) ? "Autoriser\u2026" : "Pas sur cette TV", () -> Perms.requestAdmin(this));
        row("Hume comme écran d\u2019accueil", () -> Perms.isDefaultHome(this) ? "Oui" : "Définir\u2026",
                () -> Perms.requestDefaultHome(this));
        row("Nom du Wi-Fi dans le Centre de contrôle", () -> Perms.hasLocation(this) ? "Autorisé" : "Autoriser\u2026",
                () -> Perms.requestLocation(this));
    }

    void pageAbout() {
        row("Hume Launcher+", () -> "Version " + version(), () -> { });
        row("Créé par", () -> "Petrobot", () -> { });
        row("Météo", () -> Weather.sourceName(Weather.source(this)), () -> { });
        row("Actualités", () -> News.sources(this).size() + " rédactions indiennes", () -> { });
    }

    boolean screensaverAllowed() {
        return getSharedPreferences("screensaver", MODE_PRIVATE).getBoolean("allow", true);
    }

    /** The TV's chosen screensaver (readable without permission), e.g. "Vues aériennes". */
    String dreamLabel() {
        String comp = Settings.Secure.getString(getContentResolver(), "screensaver_components");
        if ("0".equals(Settings.Secure.getString(getContentResolver(), "screensaver_enabled"))) return "Désactivé dans les réglages TV";
        if (comp == null || comp.isEmpty()) return "Par défaut de la TV";
        android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(comp.split(",")[0]);
        if (cn == null) return "Par défaut de la TV";
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(cn.getPackageName(), 0)).toString();
        } catch (Exception e) {
            return "Par défaut de la TV";
        }
    }

    /** Choose / set up the screensaver: the TV's screensaver settings, else the screensaver app itself. */
    void openDreamSettings() {
        if (Suggestions.start(this, new Intent("android.settings.DREAM_SETTINGS"))) return;
        String comp = Settings.Secure.getString(getContentResolver(), "screensaver_components");
        android.content.ComponentName cn = comp == null ? null : android.content.ComponentName.unflattenFromString(comp.split(",")[0]);
        if (cn != null) {
            Intent l = getPackageManager().getLeanbackLaunchIntentForPackage(cn.getPackageName());
            if (l == null) l = getPackageManager().getLaunchIntentForPackage(cn.getPackageName());
            if (l != null && Suggestions.start(this, l)) return;
        }
        Toast.makeText(this, "Choisissez l\u2019économiseur dans Réglages de la TV \u2192 Préférences de l\u2019appareil \u2192 Économiseur d\u2019écran", Toast.LENGTH_LONG).show();
        Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS));
    }

    String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    void section(String name) {
        TextView t = new TextView(this);
        t.setText(name.toUpperCase());
        t.setTextColor(0x8CFFFFFF);
        t.setTypeface(Ui.font(this, "medium"));
        t.setLetterSpacing(0.06f);
        Ui.textSize(t, 11.5f);
        t.setPadding(Ui.px(this, 14), Ui.px(this, 18), 0, Ui.px(this, 6));
        list.addView(t);
    }

    interface Value {
        String get();
    }

    /** A row that opens a section page: label, current value and a chevron. */
    void nav(String target, Value value) {
        android.view.View r = row(target, () -> value.get() + "   \u203a", () -> show(target));
        navRows.put(target, r);
    }

    /** A settings row: label left, current value right; focus lifts it onto a light plate. */
    /** This page's rows by label, so a page rebuilt after a choice can keep focus on that row. */
    final java.util.Map<String, android.view.View> rowsByLabel = new java.util.HashMap<>();

    /** Rebuilds a page (its rows can change after a choice) and keeps focus on the row just used. */
    void showAndFocus(String p, String label) {
        show(p);
        android.view.View v = rowsByLabel.get(label);
        if (v != null) v.requestFocus();
    }

    android.view.View row(String label, Value value, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        rowsByLabel.put(label, r);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        int padH = Ui.px(this, 16);
        r.setPadding(padH, 0, padH, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.px(this, 10));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Color.WHITE);
        l.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(l, 15);
        l.setSingleLine(true);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setTextColor(0x99FFFFFF);
        v.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(v, 15);
        v.setSingleLine(true);
        r.addView(v);
        Runnable refresh = () -> v.setText(value.get());
        refresh.run();
        refreshers.add(refresh);

        r.setOnFocusChangeListener((view, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111114 : Color.WHITE);
            v.setTextColor(has ? 0x99111114 : 0x99FFFFFF);
            view.animate().withLayer().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f).setDuration(Motion.d(180)).start();
        });
        r.setOnClickListener(view -> {
            action.run();
            for (Runnable x : refreshers) x.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(this, 40));
        lp.bottomMargin = Ui.px(this, 6);
        list.addView(r, lp);
        return r;
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent ev) {
        MainActivity.navSound(this, ev);
        return super.dispatchKeyEvent(ev) || Clicks.moveFocus(getWindow(), ev);
    }

    AlertDialog.Builder dialog(String title) {
        return Clicks.dialog(this).setTitle(title);
    }

    void refreshAll() {
        for (Runnable x : refreshers) x.run();
    }

    void chooseSource() {
        String[] keys = {Suggestions.SRC_JUSTWATCH, Suggestions.SRC_TMDB, Suggestions.SRC_STREMIO};
        String[] names = {"JustWatch  (rien à configurer)", "TMDB  (jeton gratuit nécessaire)", "Stremio  (s\u2019ouvre dans Stremio ou Nuvio)"};
        int cur = Math.max(0, java.util.Arrays.asList(keys).indexOf(Suggestions.source(this)));
        dialog("Source des recommandations").setSingleChoiceItems(names, cur, (d, w) -> {
            Suggestions.prefs(this).edit().putString(Suggestions.K_SOURCE, keys[w]).apply();
            Suggestions.changed(this);
            refreshAll();
            d.dismiss();
            showAndFocus(SHELF, "Source");
            if (w == 1 && !Suggestions.hasToken(this)) enterToken();
        }).show();
    }

    /** Token entry on the TV itself (no adb needed). Accepts the v4 read token or the short v3 API key. */
    void enterToken() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint("Clé API v3 (32 caractères) ou jeton d\u2019accès en lecture");
        in.setText(Suggestions.prefs(this).getString(Suggestions.K_TOKEN, ""));
        in.setSelection(in.getText().length());
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        int pad = Ui.px(this, 14);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        dialog("Jeton TMDB")
                .setMessage("Gratuit sur themoviedb.org \u2192 Settings \u2192 API. La courte « API Key » est la plus simple à saisir.")
                .setView(box)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    Suggestions.prefs(this).edit().putString(Suggestions.K_TOKEN, in.getText().toString().trim()).apply();
                    Suggestions.changed(this);
                    refreshAll();
                })
                .setNeutralButton("Clear", (d, w) -> {
                    Suggestions.prefs(this).edit().remove(Suggestions.K_TOKEN).apply();
                    Suggestions.changed(this);
                    refreshAll();
                })
                .setNegativeButton("Annuler", null)
                .show();
        in.requestFocus();
    }

    /** Automatic (from the internet address) or a city found by name. */
    void chooseLocation() {
        String[] names = {"Automatique", "Choisir une ville\u2026"};
        dialog("Lieu de la météo").setSingleChoiceItems(names, Weather.manual(this) ? 1 : 0, (d, w) -> {
            d.dismiss();
            if (w == 0) {
                Weather.setPlace(this, null);
                refreshAll();
            } else {
                searchCity();
            }
        }).show();
    }

    void searchCity() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint("Nom de la ville, par ex. Paris");
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int pad = Ui.px(this, 14);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        dialog("Choisir une ville")
                .setView(box)
                .setPositiveButton("Recherche", (d, w) -> {
                    String q = in.getText().toString().trim();
                    if (q.isEmpty()) return;
                    new Thread(() -> {
                        List<Weather.Place> found;
                        try {
                            found = Weather.search(q);
                        } catch (Exception e) {
                            found = null;
                        }
                        List<Weather.Place> res = found;
                        runOnUiThread(() -> showCities(q, res));
                    }).start();
                })
                .setNegativeButton("Annuler", null)
                .show();
        in.requestFocus();
    }

    void showCities(String q, List<Weather.Place> res) {
        if (isFinishing()) return;
        if (res == null) {
            Toast.makeText(this, "Recherche impossible. Vérifiez la connexion internet.", Toast.LENGTH_LONG).show();
            return;
        }
        if (res.isEmpty()) {
            Toast.makeText(this, "Aucune ville nommée \u201c" + q + "\u201d", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[res.size()];
        for (int i = 0; i < names.length; i++) {
            Weather.Place pl = res.get(i);
            names[i] = pl.detail.isEmpty() ? pl.name : pl.name + "  \u00b7  " + pl.detail;
        }
        dialog("Choisir une ville").setItems(names, (d, w) -> {
            Weather.setPlace(this, res.get(w));
            refreshAll();
        }).setNegativeButton("Annuler", null).show();
    }

    /** Address, QR and pairing code for sending content from a phone or computer. */
    void showPresentInfo() {
        if (!com.petrobot.hume.present.Presenter.enabled(this)) {
            Toast.makeText(this, "Activez d\u2019abord le mode Présentation", Toast.LENGTH_SHORT).show();
            return;
        }
        com.petrobot.hume.present.Presenter.ensureRunning(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.px(this, 16);
        box.setPadding(pad, pad, pad, pad / 2);
        android.widget.ImageView qr = new android.widget.ImageView(this);
        qr.setImageBitmap(com.petrobot.hume.present.Presenter.qr(Ui.px(this, 110)));
        qr.setBackgroundColor(Color.WHITE);
        qr.setPadding(Ui.px(this, 6), Ui.px(this, 6), Ui.px(this, 6), Ui.px(this, 6));
        box.addView(qr, new LinearLayout.LayoutParams(Ui.px(this, 122), Ui.px(this, 122)));
        TextView info = new TextView(this);
        String pin = com.petrobot.hume.present.Presenter.pin(this);
        info.setText("Ouvrez sur un téléphone ou un ordinateur du même Wi-Fi :\n\n"
                + com.petrobot.hume.present.Presenter.address() + "\n\nCode d\u2019appairage\n"
                + pin.substring(0, 3) + " " + pin.substring(3));
        info.setTextColor(Color.WHITE);
        info.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(info, 14);
        info.setPadding(Ui.px(this, 18), 0, 0, 0);
        box.addView(info);
        dialog("Envoyer vers cette TV").setView(box).setPositiveButton("Terminé", null).show();
    }

    void chooseTypes() {
        String[] keys = {"both", "movie", "tv"};
        String[] names = {"Films et séries", "Films", "Séries"};
        int cur = java.util.Arrays.asList(keys).indexOf(Suggestions.types(this));
        dialog("Contenu").setSingleChoiceItems(names, cur, (d, w) -> {
            Suggestions.prefs(this).edit().putString(Suggestions.K_TYPES, keys[w]).apply();
            Suggestions.changed(this);
            refreshAll();
            d.dismiss();
        }).show();
    }

    void chooseProviders() {
        int n = Suggestions.PROVIDERS.size();
        String[] names = new String[n];
        boolean[] on = new boolean[n];
        Set<String> cur = Suggestions.providers(this);
        for (int i = 0; i < n; i++) {
            Suggestions.Provider p = Suggestions.PROVIDERS.get(i);
            names[i] = p.name + (Suggestions.installed(this, p.pkg) ? "" : "  (non installé)");
            on[i] = cur.contains(String.valueOf(p.id));
        }
        dialog("Services de streaming").setMultiChoiceItems(names, on, (d, w, c) -> on[w] = c)
                .setPositiveButton("Terminé", (d, w) -> {
                    Set<String> sel = new java.util.HashSet<>();
                    for (int i = 0; i < n; i++) if (on[i]) sel.add(String.valueOf(Suggestions.PROVIDERS.get(i).id));
                    if (sel.isEmpty()) {
                        Toast.makeText(this, "Gardez au moins un service", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Suggestions.prefs(this).edit().putStringSet(Suggestions.K_PROVIDERS, sel).apply();
                    Suggestions.changed(this);
                    refreshAll();
                }).setNegativeButton("Annuler", null).show();
    }

    void chooseLanguages() {
        int n = Suggestions.LANG_CODES.length;
        boolean[] on = new boolean[n];
        Set<String> cur = Suggestions.languages(this);
        for (int i = 0; i < n; i++) on[i] = cur.contains(Suggestions.LANG_CODES[i]);
        dialog(Suggestions.source(this).equals(Suggestions.SRC_TMDB) ? "Langues (aucune = toutes)" : "Langues (source TMDB uniquement)")
                .setMultiChoiceItems(Suggestions.LANG_NAMES, on, (d, w, c) -> on[w] = c)
                .setPositiveButton("Terminé", (d, w) -> {
                    Set<String> sel = new java.util.HashSet<>();
                    for (int i = 0; i < n; i++) if (on[i]) sel.add(Suggestions.LANG_CODES[i]);
                    Suggestions.prefs(this).edit().putStringSet(Suggestions.K_LANGS, sel).apply();
                    Suggestions.changed(this);
                    refreshAll();
                }).setNegativeButton("Annuler", null).show();
    }

    void chooseAdvance() {
        int[] secs = {0, 6, 8, 12};
        String[] names = {"Désactivé", "Toutes les 6 s", "Toutes les 8 s", "Toutes les 12 s"};
        int cur = 0;
        for (int i = 0; i < secs.length; i++) if (secs[i] == Suggestions.advanceSeconds(this)) cur = i;
        dialog("Défilement automatique").setSingleChoiceItems(names, cur, (d, w) -> {
            Suggestions.prefs(this).edit().putInt(Suggestions.K_ADVANCE, secs[w]).apply();
            refreshAll();
            d.dismiss();
        }).show();
    }
}
