package com.orbitv.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Menu principal : les rubriques comme sur un récepteur numérique. */
public class HomeActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView[] counts;
    private View[] tiles;
    private boolean wasLoading = false;

    private static final String[][] TILES = {
            // clé, titre, arabe
            {"tv", "Chaînes TV", "القنوات التلفزيونية"},
            {"cinema", "Cinéma", "السينما"},
            {"docs", "Documentaires", "الوثائقيات"},
            {"music", "Musique", "الموسيقى"},
            {"other", "Autres chaînes", "قنوات أخرى"},
            {"sat", "Satellites", "الأقمار الصناعية"},
            {"fav", "Favoris", "المفضلة"},
            {"refresh", "Actualiser", "تحديث"},
    };
    private static final int[][] COLORS = {
            {0xFF3B5BFF, 0xFF7B5CFF},
            {0xFFFF5A5F, 0xFFFF9A3C},
            {0xFF0FA3A3, 0xFF2BCB7A},
            {0xFFE0409A, 0xFF9B4DFF},
            {0xFF2E5BA8, 0xFF2AA9D8},
            {0xFFE8A317, 0xFFFF6B35},
            {0xFFD7263D, 0xFFFF6F61},
            {0xFF414B7A, 0xFF5A66A0},
    };

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            h.postDelayed(this, 400);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.immersive(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundDrawable(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0A0F24, 0xFF171040}));
        int pad = Ui.u(this, 36);
        root.setPadding(pad, Ui.u(this, 22), pad, Ui.u(this, 28));

        // ----- en-tête -----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(new LogoView(this, false), new LinearLayout.LayoutParams(Ui.u(this, 96), Ui.u(this, 76)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(Ui.u(this, 8), 0, 0, 0);
        TextView name = Ui.text(this, "OrbiTV", 34, Ui.TEXT, true);
        name.setLetterSpacing(0.05f);
        names.addView(name);
        names.addView(Ui.text(this, "Votre récepteur TV gratuit", 14, Ui.CYAN, false));
        header.addView(names, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        status = Ui.text(this, "", 14, Ui.MUTED, false);
        status.setGravity(Gravity.END);
        header.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ----- tuiles -----
        counts = new TextView[TILES.length];
        tiles = new View[TILES.length];
        View first = null;
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            rp.topMargin = Ui.u(this, 14);
            root.addView(line, rp);
            for (int col = 0; col < 4; col++) {
                int idx = row * 4 + col;
                View tile = makeTile(idx);
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                int mg = Ui.u(this, 7);
                tp.setMargins(mg, mg, mg, mg);
                line.addView(tile, tp);
                tiles[idx] = tile;
                if (first == null) first = tile;
            }
        }

        TextView hint = Ui.text(this,
                "Flèches : choisir   •   OK : ouvrir   •   Dans une liste, appui long sur OK : favori", 13, Ui.MUTED, false);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.topMargin = Ui.u(this, 10);
        root.addView(hint, hp);

        // voisins explicites : gauche/droite dans la rangée, haut/bas entre les 2 rangées
        for (int i = 0; i < tiles.length; i++) {
            int row = i / 4, col = i % 4;
            View t = tiles[i];
            t.setNextFocusLeftId(tiles[col > 0 ? i - 1 : i].getId());
            t.setNextFocusRightId(tiles[col < 3 ? i + 1 : i].getId());
            t.setNextFocusUpId(tiles[row > 0 ? i - 4 : i].getId());
            t.setNextFocusDownId(tiles[row < 1 ? i + 4 : i].getId());
        }

        setContentView(root);
        if (first != null) first.requestFocus();

        if (!Catalog.loaded && !Catalog.loading) Catalog.startLoad(this, false);
    }

    private View makeTile(final int idx) {
        final String key = TILES[idx][0];
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.BOTTOM);
        int p = Ui.u(this, 18);
        t.setPadding(p, p, p, p);
        t.setBackgroundDrawable(Ui.tileBackground(this, COLORS[idx][0], COLORS[idx][1]));
        t.setId(View.generateViewId());
        t.setFocusable(true);
        t.setClickable(true);

        TextView title = Ui.text(this, TILES[idx][1], 24, 0xFFFFFFFF, true);
        TextView ar = Ui.text(this, TILES[idx][2], 17, 0xE6FFFFFF, false);
        TextView cnt = Ui.text(this, " ", 13, 0xCCFFFFFF, false);
        counts[idx] = cnt;
        t.addView(ar);
        t.addView(title);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.topMargin = Ui.u(this, 6);
        t.addView(cnt, cp);

        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.06f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(140).start();
                v.setTranslationZ(hasFocus ? Ui.u(HomeActivity.this, 12) : 0f);
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTile(key);
            }
        });
        return t;
    }

    private void openTile(String key) {
        if (key.equals("refresh")) {
            if (Catalog.loading) {
                Toast.makeText(this, "Chargement déjà en cours…", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Mise à jour des chaînes…", Toast.LENGTH_SHORT).show();
                Catalog.startLoad(this, true);
            }
            return;
        }
        if (!key.equals("fav")) {
            if (!Catalog.loaded) {
                Toast.makeText(this, Catalog.loading
                        ? "Chargement des chaînes en cours, patientez…"
                        : "Aucune chaîne chargée : vérifiez la connexion Internet puis « Actualiser »",
                        Toast.LENGTH_LONG).show();
                return;
            }
            Catalog.Section s = Catalog.section(key);
            if (s == null || s.count() == 0) {
                Toast.makeText(this, "Aucune chaîne disponible dans cette rubrique", Toast.LENGTH_LONG).show();
                return;
            }
        }
        Intent i = new Intent(this, BrowseActivity.class);
        i.putExtra("section", key);
        startActivity(i);
    }

    private void refreshStatus() {
        if (Catalog.loading) {
            wasLoading = true;
            status.setText("Chargement… " + Catalog.done.get() + " / " + Catalog.total);
        } else if (Catalog.loaded) {
            status.setText(Catalog.totalChannels() + " chaînes disponibles");
        } else {
            status.setText("Pas de connexion : chaînes non chargées");
        }
        for (int i = 0; i < TILES.length; i++) {
            String key = TILES[i][0];
            String txt;
            if (key.equals("refresh")) {
                txt = Catalog.loading ? "En cours…" : "Mettre à jour la liste";
            } else if (key.equals("fav")) {
                txt = Favorites.all(this).size() + " chaîne(s)";
            } else {
                Catalog.Section s = Catalog.section(key);
                if (Catalog.loading && s == null) txt = "…";
                else if (s == null) txt = "-";
                else txt = s.count() + " chaînes";
            }
            counts[i].setText(txt);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        h.post(poll);
    }

    @Override
    protected void onPause() {
        h.removeCallbacks(poll);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.immersive(this);
    }
}
