package com.orbitv.app;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Ligne de chaîne : numéro, logo, nom, repère favori. */
public class ChannelAdapter extends BaseAdapter {
    private final Context ctx;
    private List<Channel> data;
    private Set<String> favs;
    private int current = -1;

    public ChannelAdapter(Context ctx, List<Channel> data) {
        this.ctx = ctx;
        this.data = data == null ? new ArrayList<Channel>() : data;
        this.favs = Favorites.urls(ctx);
    }

    public void setData(List<Channel> d) {
        this.data = d == null ? new ArrayList<Channel>() : d;
        this.favs = Favorites.urls(ctx);
        notifyDataSetChanged();
    }

    public void refreshFavorites() {
        favs = Favorites.urls(ctx);
        notifyDataSetChanged();
    }

    public void setCurrent(int idx) {
        current = idx;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return data.size(); }
    @Override public Object getItem(int i) { return data.get(i); }
    @Override public long getItemId(int i) { return i; }

    private static final class Holder {
        TextView num, title, sub;
        ImageView logo;
        View fav;
    }

    @Override
    public View getView(int position, View convert, ViewGroup parent) {
        Holder h;
        if (convert == null) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.u(ctx, 62)));
            row.setPadding(Ui.u(ctx, 10), 0, Ui.u(ctx, 14), 0);

            h = new Holder();
            h.num = Ui.text(ctx, "", 15, Ui.MUTED, true);
            h.num.setGravity(Gravity.CENTER);
            row.addView(h.num, new LinearLayout.LayoutParams(Ui.u(ctx, 50), ViewGroup.LayoutParams.WRAP_CONTENT));

            FrameLayout box = new FrameLayout(ctx);
            box.setBackgroundDrawable(Ui.rect(ctx, Ui.SURFACE2, 8));
            h.logo = new ImageView(ctx);
            h.logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int lp = Ui.u(ctx, 5);
            h.logo.setPadding(lp, lp, lp, lp);
            box.addView(h.logo, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.u(ctx, 70), Ui.u(ctx, 46));
            bp.leftMargin = Ui.u(ctx, 6);
            bp.rightMargin = Ui.u(ctx, 14);
            row.addView(box, bp);

            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            h.title = Ui.text(ctx, "", 19, Ui.TEXT, true);
            h.title.setSingleLine(true);
            h.title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            h.sub = Ui.text(ctx, "", 12, Ui.MUTED, false);
            h.sub.setSingleLine(true);
            col.addView(h.title);
            col.addView(h.sub);
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            h.fav = new View(ctx);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(Ui.GOLD);
            h.fav.setBackgroundDrawable(dot);
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(Ui.u(ctx, 12), Ui.u(ctx, 12));
            fp.leftMargin = Ui.u(ctx, 8);
            row.addView(h.fav, fp);

            row.setTag(h);
            convert = row;
        } else {
            h = (Holder) convert.getTag();
        }

        Channel c = data.get(position);
        h.num.setText(String.format(Locale.US, "%03d", position + 1));
        h.title.setText(c.title);
        h.title.setTextColor(position == current ? Ui.CYAN : Ui.TEXT);
        int backups = c.alts.size();
        h.sub.setText(backups > 0 ? ("+" + backups + " flux de secours") : " ");
        h.fav.setVisibility(favs.contains(c.url) ? View.VISIBLE : View.INVISIBLE);
        ImageLoader.load(h.logo, c.logo);
        return convert;
    }
}
