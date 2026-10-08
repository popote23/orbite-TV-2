package com.orbitv.app;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Écran d'ouverture : logo animé, puis menu principal. */
public class SplashActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView status;
    private boolean left = false;

    private final Runnable goHome = new Runnable() {
        @Override
        public void run() {
            openHome();
        }
    };

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (Catalog.loading) {
                status.setText("Chargement des chaînes…  " + Catalog.done.get() + " / " + Catalog.total);
            } else {
                status.setText("");
            }
            h.postDelayed(this, 250);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.immersive(this);

        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF050816, 0xFF140E3C});
        root.setBackgroundDrawable(bg);

        root.addView(new LogoView(this, true),
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        DisplayMetrics m = getResources().getDisplayMetrics();
        int minD = Math.min(m.widthPixels, m.heightPixels);
        float logoR = minD * 0.17f;

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        lp.topMargin = (int) (minD / 2f + logoR * 1.55f);
        root.addView(col, lp);

        SpannableString name = new SpannableString("OrbiTV");
        name.setSpan(new ForegroundColorSpan(Ui.TEXT), 0, 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        name.setSpan(new ForegroundColorSpan(Ui.GOLD), 4, 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView title = Ui.text(this, "", 58, Ui.TEXT, true);
        title.setText(name);
        title.setLetterSpacing(0.08f);
        title.setAlpha(0f);
        col.addView(title);

        TextView tag = Ui.text(this, "Toutes vos chaînes, en orbite", 18, Ui.CYAN, false);
        tag.setLetterSpacing(0.12f);
        tag.setAlpha(0f);
        col.addView(tag);

        status = Ui.text(this, "", 14, Ui.MUTED, false);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        sp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        sp.bottomMargin = Ui.u(this, 28);
        root.addView(status, sp);

        setContentView(root);

        float rise = Ui.u(this, 36);
        title.setTranslationY(rise);
        tag.setTranslationY(rise);
        ObjectAnimator ty = ObjectAnimator.ofFloat(title, "translationY", rise, 0f);
        ty.setDuration(900);
        ty.setInterpolator(new OvershootInterpolator(1.4f));
        ty.setStartDelay(1000);
        ObjectAnimator ta = ObjectAnimator.ofFloat(title, "alpha", 0f, 1f);
        ta.setDuration(700);
        ta.setStartDelay(1000);
        ta.start();
        ty.start();
        ObjectAnimator gy = ObjectAnimator.ofFloat(tag, "translationY", rise, 0f);
        gy.setDuration(800);
        gy.setInterpolator(new DecelerateInterpolator());
        gy.setStartDelay(1450);
        ObjectAnimator ga = ObjectAnimator.ofFloat(tag, "alpha", 0f, 1f);
        ga.setDuration(700);
        ga.setStartDelay(1450);
        gy.start();
        ga.start();

        Catalog.startLoad(this, false);
        h.post(poll);
        h.postDelayed(goHome, 3800);
    }

    private void openHome() {
        if (left) return;
        left = true;
        startActivity(new Intent(this, HomeActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, e);
        openHome();
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) openHome();
        return true;
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.immersive(this);
    }
}
