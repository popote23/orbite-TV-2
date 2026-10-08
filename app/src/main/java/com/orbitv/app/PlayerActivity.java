package com.orbitv.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lecteur plein écran façon récepteur :
 * haut/bas = chaîne suivante/précédente, chiffres = numéro de chaîne,
 * OK ou gauche = liste des chaînes, droite = format d'image.
 */
public class PlayerActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());

    private List<Channel> queue;
    private int current = 0;
    private int altTry = 0;
    private boolean zoom = false;

    private static final String DEFAULT_UA = "Mozilla/5.0 (Linux; Android) OrbiTV/1.0";
    private ExoPlayer player;
    private DefaultHttpDataSource.Factory http;
    private FrameLayout root;
    private SurfaceView surface;
    private ProgressBar spinner;
    private TextView errorText, digitsText, numText, nameText, subText;
    private View banner, listPanel;
    private ListView list;
    private ChannelAdapter adapter;
    private TextView listTitle;

    private int videoW = 0, videoH = 0;
    private float pixelRatio = 1f;
    private final StringBuilder digits = new StringBuilder();

    private final Runnable hideBanner = new Runnable() {
        @Override
        public void run() {
            banner.setVisibility(View.GONE);
        }
    };
    private final Runnable commitDigits = new Runnable() {
        @Override
        public void run() {
            String s = digits.toString();
            digits.setLength(0);
            digitsText.setVisibility(View.GONE);
            try {
                int n = Integer.parseInt(s);
                if (n >= 1 && n <= queue.size()) {
                    play(n - 1);
                } else {
                    Toast.makeText(PlayerActivity.this, "Chaîne " + n + " introuvable", Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException ignored) {
            }
        }
    };
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.getPlaybackState() != Player.STATE_READY) failOver();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.immersive(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        queue = Catalog.queue;
        if (queue == null || queue.isEmpty()) {
            finish();
            return;
        }
        current = Math.max(0, Math.min(Catalog.queueIndex, queue.size() - 1));

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(Ui.u(this, 64), Ui.u(this, 64), Gravity.CENTER);
        root.addView(spinner, sp);

        errorText = Ui.text(this, "", 20, 0xFFFFFFFF, true);
        errorText.setGravity(Gravity.CENTER);
        errorText.setBackgroundDrawable(Ui.rect(this, 0xCC000000, 12));
        int ep = Ui.u(this, 20);
        errorText.setPadding(ep, ep, ep, ep);
        errorText.setVisibility(View.GONE);
        root.addView(errorText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // bandeau d'information
        LinearLayout bn = new LinearLayout(this);
        bn.setOrientation(LinearLayout.HORIZONTAL);
        bn.setGravity(Gravity.CENTER_VERTICAL);
        bn.setBackgroundDrawable(Ui.rect(this, 0xE60A0F24, 14));
        int bp = Ui.u(this, 18);
        bn.setPadding(bp, bp / 2, bp * 2, bp / 2);
        numText = Ui.text(this, "001", 34, Ui.CYAN, true);
        bn.addView(numText);
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(Ui.u(this, 16), 0, 0, 0);
        nameText = Ui.text(this, "", 26, Ui.TEXT, true);
        nameText.setSingleLine(true);
        subText = Ui.text(this, "", 14, Ui.MUTED, false);
        names.addView(nameText);
        names.addView(subText);
        bn.addView(names);
        banner = bn;
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.bottomMargin = Ui.u(this, 30);
        root.addView(bn, bl);

        digitsText = Ui.text(this, "", 56, Ui.GOLD, true);
        digitsText.setBackgroundDrawable(Ui.rect(this, 0xCC000000, 12));
        digitsText.setPadding(Ui.u(this, 20), Ui.u(this, 6), Ui.u(this, 20), Ui.u(this, 6));
        digitsText.setVisibility(View.GONE);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        dl.topMargin = Ui.u(this, 28);
        dl.rightMargin = Ui.u(this, 28);
        root.addView(digitsText, dl);

        // liste rapide des chaînes
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xEE0A0F24);
        listTitle = Ui.text(this, Catalog.queueTitle, 20, Ui.GOLD, true);
        listTitle.setPadding(Ui.u(this, 18), Ui.u(this, 16), Ui.u(this, 18), Ui.u(this, 8));
        panel.addView(listTitle);
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(Ui.u(this, 3));
        list.setSelector(Ui.listSelector(this));
        list.setVerticalScrollBarEnabled(false);
        list.setCacheColorHint(0);
        adapter = new ChannelAdapter(this, queue);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                hideList();
                play(position);
            }
        });
        panel.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        listPanel = panel;
        listPanel.setVisibility(View.GONE);
        root.addView(panel, new FrameLayout.LayoutParams(Ui.u(this, 400), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));

        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listPanel.getVisibility() == View.VISIBLE) hideList();
                else showList();
            }
        });
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int bm, int ol, int ot, int or, int ob) {
                fitSurface();
            }
        });

        setContentView(root);
    }

    // =====================================================================
    //  Lecture
    // =====================================================================

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        if (queue == null || queue.isEmpty()) return;
        if (player == null) {
            http = new DefaultHttpDataSource.Factory()
                    .setUserAgent(DEFAULT_UA)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(10000)
                    .setReadTimeoutMs(15000);
            player = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                    .build();
            player.setVideoSurfaceView(surface);
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_BUFFERING) {
                        spinner.setVisibility(View.VISIBLE);
                    } else if (state == Player.STATE_READY) {
                        h.removeCallbacks(watchdog);
                        spinner.setVisibility(View.GONE);
                        errorText.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onVideoSizeChanged(VideoSize size) {
                    videoW = size.width;
                    videoH = size.height;
                    pixelRatio = size.pixelWidthHeightRatio;
                    fitSurface();
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && player != null) {
                        player.seekToDefaultPosition();
                        player.prepare();
                        return;
                    }
                    failOver();
                }
            });
            play(current);
        }
    }

    @Override
    protected void onPause() {
        h.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onPause();
    }

    private void play(int index) {
        if (player == null) return;
        current = index;
        altTry = 0;
        errorText.setVisibility(View.GONE);
        videoW = 0;
        videoH = 0;
        fitSurface();
        startStream(queue.get(index));
        adapter.setCurrent(index);
        showBanner();
    }

    private void startStream(Channel ch) {
        if (player == null || http == null) return;
        Map<String, String> props = new HashMap<String, String>();
        if (ch.referrer.length() > 0) props.put("Referer", ch.referrer);
        http.setDefaultRequestProperties(props);
        http.setUserAgent(ch.userAgent.length() > 0 ? ch.userAgent : DEFAULT_UA);
        startUrl(ch.url);
    }

    private void startUrl(String url) {
        if (player == null) return;
        MediaItem.Builder mi = new MediaItem.Builder().setUri(url);
        String low = url.toLowerCase(Locale.ROOT);
        if (low.contains(".m3u8")) mi.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (low.contains(".mpd")) mi.setMimeType(MimeTypes.APPLICATION_MPD);
        spinner.setVisibility(View.VISIBLE);
        player.setMediaItem(mi.build());
        player.prepare();
        player.setPlayWhenReady(true);
        h.removeCallbacks(watchdog);
        h.postDelayed(watchdog, 25000);
    }

    /** Flux en panne : on essaie les flux de secours, sinon on prévient. */
    private void failOver() {
        if (player == null) return;
        Channel c = queue.get(current);
        if (altTry < c.alts.size()) {
            Channel alt = c.alts.get(altTry);
            altTry++;
            startStream(alt);
            return;
        }
        h.removeCallbacks(watchdog);
        spinner.setVisibility(View.GONE);
        errorText.setText("Flux indisponible pour l'instant\nHaut / Bas : changer de chaîne");
        errorText.setVisibility(View.VISIBLE);
    }

    private void step(int delta) {
        int n = queue.size();
        play(((current + delta) % n + n) % n);
    }

    // =====================================================================
    //  Interface
    // =====================================================================

    private void showBanner() {
        Channel c = queue.get(current);
        numText.setText(String.format(Locale.US, "%03d", current + 1));
        nameText.setText(c.title);
        subText.setText(Catalog.queueTitle + "   •   EN DIRECT");
        banner.setVisibility(View.VISIBLE);
        h.removeCallbacks(hideBanner);
        h.postDelayed(hideBanner, 4500);
    }

    private void showList() {
        listPanel.setVisibility(View.VISIBLE);
        adapter.setCurrent(current);
        list.setSelection(current);
        list.requestFocus();
    }

    private void hideList() {
        listPanel.setVisibility(View.GONE);
        root.requestFocus();
    }

    private void toggleAspect() {
        zoom = !zoom;
        fitSurface();
        Toast.makeText(this, zoom ? "Image : plein écran (zoom)" : "Image : format d'origine", Toast.LENGTH_SHORT).show();
    }

    private void fitSurface() {
        if (root == null || surface == null) return;
        int W = root.getWidth(), H = root.getHeight();
        if (W <= 0 || H <= 0) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) surface.getLayoutParams();
        int w, hh;
        if (videoW <= 0 || videoH <= 0) {
            w = W;
            hh = H;
        } else {
            float aspect = videoW * pixelRatio / videoH;
            float screen = (float) W / H;
            boolean widthLimited = zoom ? screen < aspect : screen > aspect;
            if (widthLimited) {
                hh = H;
                w = Math.round(H * aspect);
            } else {
                w = W;
                hh = Math.round(W / aspect);
            }
        }
        if (lp.width != w || lp.height != hh) {
            lp.width = w;
            lp.height = hh;
            lp.gravity = Gravity.CENTER;
            surface.setLayoutParams(lp);
        }
    }

    private void typeDigit(int d) {
        if (digits.length() >= 4) digits.setLength(0);
        digits.append(d);
        digitsText.setText(digits.toString());
        digitsText.setVisibility(View.VISIBLE);
        h.removeCallbacks(commitDigits);
        h.postDelayed(commitDigits, 1800);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (listPanel != null && listPanel.getVisibility() == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == KeyEvent.KEYCODE_MENU) {
                hideList();
                return true;
            }
            return super.onKeyDown(keyCode, e);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                step(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                step(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MENU:
                showList();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                toggleAspect();
                return true;
            case KeyEvent.KEYCODE_INFO:
                showBanner();
                return true;
            default:
                break;
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            typeDigit(keyCode - KeyEvent.KEYCODE_0);
            return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            typeDigit(keyCode - KeyEvent.KEYCODE_NUMPAD_0);
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.immersive(this);
    }
}
