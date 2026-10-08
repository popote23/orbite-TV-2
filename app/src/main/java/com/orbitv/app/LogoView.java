package com.orbitv.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Logo OrbiTV animé : planète-lecteur, orbite aux couleurs qui défilent,
 * satellite avec traînée, ondes de signal, étoiles scintillantes et étoile filante.
 * full = true : animation d'intro (zoom + rotation) et ciel étoilé.
 */
public class LogoView extends View {
    private static final float A = 2.1f;   // demi-grand axe de l'orbite (en rayons de planète)
    private static final float B = 0.7f;   // demi-petit axe
    private static final float TILT = -22f;

    private final boolean full;
    private final long start = SystemClock.uptimeMillis();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final RectF oval = new RectF(-A, -B, A, B);
    private final Path tri = new Path();

    private final float[] starX = new float[80];
    private final float[] starY = new float[80];
    private final float[] starR = new float[80];
    private final float[] starPh = new float[80];

    private float r = 1f;
    private Shader planetShader, haloShader, triShader, satGlow;
    private SweepGradient sweep;

    public LogoView(Context c, boolean full) {
        super(c);
        this.full = full;
        Random rnd = new Random(11);
        for (int i = 0; i < starX.length; i++) {
            starX[i] = rnd.nextFloat();
            starY[i] = rnd.nextFloat();
            starR[i] = 0.6f + rnd.nextFloat() * 1.8f;
            starPh[i] = rnd.nextFloat() * 6.28f;
        }
        tri.moveTo(-0.33f, -0.52f);
        tri.lineTo(-0.33f, 0.52f);
        tri.lineTo(0.59f, 0f);
        tri.close();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        r = Math.min(w, h) * (full ? 0.17f : 0.2f);
        planetShader = new RadialGradient(-0.35f, -0.35f, 1.5f,
                new int[]{0xFF6078FF, 0xFF2A3AB0, 0xFF0A1042}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        haloShader = new RadialGradient(0, 0, 2.8f,
                new int[]{0x773C6EFF, 0x003C6EFF}, null, Shader.TileMode.CLAMP);
        triShader = new LinearGradient(0, -0.5f, 0, 0.5f, 0xFFFFFFFF, 0xFFFFD678, Shader.TileMode.CLAMP);
        satGlow = new RadialGradient(0, 0, 1f,
                new int[]{0xDDFFC24B, 0x00FFC24B}, null, Shader.TileMode.CLAMP);
        sweep = new SweepGradient(0, 0,
                new int[]{Ui.CYAN, Ui.VIOLET, Ui.GOLD, Ui.CYAN}, new float[]{0f, 0.35f, 0.7f, 1f});
    }

    private static float overshoot(float k) {
        float s = 1.70158f;
        k = k - 1f;
        return k * k * ((s + 1f) * k + s) + 1f;
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (planetShader == null) return;
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        int w = getWidth(), h = getHeight();

        if (full) {
            drawStars(cv, w, h, t);
            drawShootingStar(cv, w, h, t);
        }

        float k = full ? Math.min(1f, t / 1.3f) : 1f;
        float e = full ? overshoot(k) : 1f;
        if (e < 0.02f) {
            postInvalidateOnAnimation();
            return;
        }

        cv.save();
        cv.translate(w / 2f, h / 2f);
        cv.scale(r * e, r * e);
        cv.rotate((1f - k) * -200f);

        // halo qui respire
        float breathe = 1f + 0.06f * (float) Math.sin(t * 2.0);
        cv.save();
        cv.scale(breathe, breathe);
        p.setStyle(Paint.Style.FILL);
        p.setShader(haloShader);
        cv.drawCircle(0, 0, 2.8f, p);
        p.setShader(null);
        cv.restore();

        // ondes de signal
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.035f);
        for (int i = 0; i < 3; i++) {
            float ph = (t * 0.45f + i / 3f) % 1f;
            p.setColor(Ui.CYAN);
            p.setAlpha((int) ((1f - ph) * 110));
            cv.drawCircle(0, 0, 1.1f + 1.7f * ph, p);
        }
        p.setAlpha(255);

        float phi = t * 1.1f;

        // orbite : moitié arrière (+ satellite s'il passe derrière)
        cv.save();
        cv.rotate(TILT);
        drawOrbit(cv, t, 180f);
        if (Math.sin(phi) < 0) drawSatellite(cv, phi);
        cv.restore();

        // planète
        p.setStyle(Paint.Style.FILL);
        p.setShader(planetShader);
        cv.drawCircle(0, 0, 1f, p);

        // bouton lecture qui pulse
        float pulse = 1f + 0.05f * (float) Math.sin(t * 3.0);
        cv.save();
        cv.scale(pulse, pulse);
        p.setShader(triShader);
        cv.drawPath(tri, p);
        cv.restore();
        p.setShader(null);

        // orbite : moitié avant (+ satellite s'il passe devant)
        cv.save();
        cv.rotate(TILT);
        drawOrbit(cv, t, 0f);
        if (Math.sin(phi) >= 0) drawSatellite(cv, phi);
        cv.restore();

        cv.restore();
        postInvalidateOnAnimation();
    }

    private void drawOrbit(Canvas cv, float t, float startAngle) {
        m.setRotate(t * 40f);
        sweep.setLocalMatrix(m);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.13f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setShader(sweep);
        cv.drawArc(oval, startAngle, 180f, false, p);
        p.setShader(null);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawSatellite(Canvas cv, float phi) {
        float x = (float) (A * Math.cos(phi));
        float y = (float) (B * Math.sin(phi));
        float depth = 0.85f + 0.15f * (float) Math.sin(phi);

        // traînée
        p.setStyle(Paint.Style.FILL);
        for (int i = 1; i <= 7; i++) {
            float ph = phi - i * 0.1f;
            float tx = (float) (A * Math.cos(ph));
            float ty = (float) (B * Math.sin(ph));
            p.setColor(Ui.GOLD);
            p.setAlpha((int) ((1f - i / 8f) * 150));
            cv.drawCircle(tx, ty, 0.07f * depth * (1f - i / 9f), p);
        }
        p.setAlpha(255);

        cv.save();
        cv.translate(x, y);
        cv.scale(depth, depth);

        cv.save();
        cv.scale(0.6f, 0.6f);
        p.setShader(satGlow);
        cv.drawCircle(0, 0, 1f, p);
        p.setShader(null);
        cv.restore();

        p.setColor(Ui.CYAN);
        cv.drawRect(-0.40f, -0.045f, -0.14f, 0.045f, p);
        cv.drawRect(0.14f, -0.045f, 0.40f, 0.045f, p);
        p.setColor(Ui.GOLD);
        cv.drawCircle(0, 0, 0.11f, p);
        p.setColor(0xFFFFFFFF);
        cv.drawCircle(0, 0, 0.045f, p);
        cv.restore();
    }

    private void drawStars(Canvas cv, int w, int h, float t) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        float unit = Ui.unit(getContext());
        for (int i = 0; i < starX.length; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 1.6f + starPh[i]);
            p.setAlpha((int) (60 + 195 * tw));
            cv.drawCircle(starX[i] * w, starY[i] * h, starR[i] * unit, p);
        }
        p.setAlpha(255);
    }

    private void drawShootingStar(Canvas cv, int w, int h, float t) {
        float cycle = 3.4f;
        int n = (int) (t / cycle);
        float local = t - n * cycle;
        if (local > 0.8f || t < 1.5f) return;
        float u = local / 0.8f;
        Random rnd = new Random(n * 31L + 5);
        float sx = w * (0.55f + rnd.nextFloat() * 0.45f);
        float sy = h * (0.02f + rnd.nextFloat() * 0.25f);
        float dx = -w * 0.35f, dy = h * 0.28f;
        float hx = sx + dx * u, hy = sy + dy * u;
        float tx = hx - dx * 0.35f, ty = hy - dy * 0.35f;
        p.setShader(new LinearGradient(tx, ty, hx, hy, 0x00FFFFFF, 0xFFFFFFFF, Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f * Ui.unit(getContext()));
        p.setAlpha((int) (255 * (1f - u * 0.4f)));
        cv.drawLine(tx, ty, hx, hy, p);
        p.setShader(null);
        p.setAlpha(255);
    }
}
