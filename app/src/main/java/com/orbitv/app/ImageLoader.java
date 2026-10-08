package com.orbitv.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Petit chargeur de logos (cache mémoire, sans bibliothèque externe). */
public final class ImageLoader {
    private ImageLoader() {}

    private static final android.util.LruCache<String, Bitmap> CACHE =
            new android.util.LruCache<String, Bitmap>(6 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap b) {
                    return b.getByteCount();
                }
            };
    private static final Set<String> FAILED = Collections.synchronizedSet(new HashSet<String>());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);

    public static void load(final ImageView iv, final String url) {
        iv.setTag(url);
        iv.setImageDrawable(null);
        if (url == null || url.length() == 0 || FAILED.contains(url)) return;
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bm = download(url);
                if (bm == null) {
                    FAILED.add(url);
                    return;
                }
                CACHE.put(url, bm);
                iv.post(new Runnable() {
                    @Override
                    public void run() {
                        if (url.equals(iv.getTag())) iv.setImageBitmap(bm);
                    }
                });
            }
        });
    }

    private static Bitmap download(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
                if (total > 1500000) return null;
            }
            byte[] data = out.toByteArray();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            int max = Math.max(o.outWidth, o.outHeight);
            while (max / sample > 160) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o2);
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
