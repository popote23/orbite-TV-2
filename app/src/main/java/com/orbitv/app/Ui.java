package com.orbitv.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

/** Petits outils d'interface. Toutes les tailles sont en "unités" d'un écran 960x540. */
public final class Ui {
    private Ui() {}

    public static final int BG = 0xFF0A0F24;
    public static final int SURFACE = 0xFF131A3A;
    public static final int SURFACE2 = 0xFF1E2858;
    public static final int CYAN = 0xFF19D3FF;
    public static final int GOLD = 0xFFFFC24B;
    public static final int VIOLET = 0xFF7B5CFF;
    public static final int TEXT = 0xFFEAF0FF;
    public static final int MUTED = 0xFF8D9AC8;

    /** Taille d'une unité en pixels (même rendu sur TV, box et téléphone). */
    public static float unit(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        float maxD = Math.max(m.widthPixels, m.heightPixels);
        float minD = Math.min(m.widthPixels, m.heightPixels);
        return Math.min(maxD / 960f, minD / 540f);
    }

    public static int u(Context c, float v) {
        return Math.round(v * unit(c));
    }

    public static TextView text(Context c, String s, float sizeUnits, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeUnits * unit(c));
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    public static GradientDrawable rect(Context c, int color, float radiusUnits) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusUnits * unit(c));
        return g;
    }

    public static GradientDrawable gradient(Context c, int c1, int c2, float radiusUnits) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setCornerRadius(radiusUnits * unit(c));
        return g;
    }

    /** Fond de tuile : normal / mis en avant quand on le sélectionne à la télécommande. */
    public static StateListDrawable tileBackground(Context c, int c1, int c2) {
        GradientDrawable normal = gradient(c, dim(c1), dim(c2), 14);
        GradientDrawable focused = gradient(c, c1, c2, 14);
        focused.setStroke(Math.max(2, u(c, 3.5f)), 0xFFFFFFFF);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{android.R.attr.state_pressed}, focused);
        s.addState(new int[]{}, normal);
        return s;
    }

    private static int dim(int color) {
        int r = (int) (((color >> 16) & 0xFF) * 0.62f);
        int g = (int) (((color >> 8) & 0xFF) * 0.62f);
        int b = (int) ((color & 0xFF) * 0.62f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Surbrillance d'une ligne de liste sélectionnée. */
    public static GradientDrawable listSelector(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x5519D3FF);
        g.setStroke(Math.max(2, u(c, 2f)), CYAN);
        g.setCornerRadius(8 * unit(c));
        return g;
    }

    public static void immersive(Activity a) {
        a.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
