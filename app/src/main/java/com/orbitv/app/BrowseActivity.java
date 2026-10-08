package com.orbitv.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Liste d'une rubrique : groupes à gauche, chaînes à droite. */
public class BrowseActivity extends Activity {
    private String key;
    private List<Catalog.Group> groups = new ArrayList<Catalog.Group>();
    private ListView groupList, chanList;
    private GroupAdapter groupAdapter;
    private ChannelAdapter chanAdapter;
    private TextView emptyView;
    private int currentGroup = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.immersive(this);
        key = getIntent().getStringExtra("section");
        if (key == null) key = "tv";

        String titleText;
        if (key.equals("fav")) {
            titleText = "Favoris";
        } else {
            Catalog.Section s = Catalog.section(key);
            titleText = s == null ? "Chaînes" : s.title;
            if (s != null) groups = s.groups;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundDrawable(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0A0F24, 0xFF141038}));

        // en-tête
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.u(this, 28), Ui.u(this, 14), Ui.u(this, 28), Ui.u(this, 10));
        TextView title = Ui.text(this, titleText, 28, Ui.TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView hint = Ui.text(this, "OK : regarder   •   Appui long : favori   •   Retour : menu", 13, Ui.MUTED, false);
        hint.setGravity(Gravity.END);
        header.addView(hint);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // corps
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setPadding(Ui.u(this, 20), 0, Ui.u(this, 20), Ui.u(this, 16));
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        groupList = new ListView(this);
        styleList(groupList);
        groupAdapter = new GroupAdapter();
        groupList.setAdapter(groupAdapter);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f);
        gp.rightMargin = Ui.u(this, 12);
        body.addView(groupList, gp);

        chanList = new ListView(this);
        styleList(chanList);
        chanAdapter = new ChannelAdapter(this, new ArrayList<Channel>());
        chanList.setAdapter(chanAdapter);
        body.addView(chanList, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 7f));

        emptyView = Ui.text(this, "", 18, Ui.MUTED, false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // navigation explicite entre la liste des groupes et celle des chaînes
        groupList.setId(View.generateViewId());
        chanList.setId(View.generateViewId());
        groupList.setNextFocusRightId(chanList.getId());
        chanList.setNextFocusLeftId(groupList.getId());

        setContentView(root);

        groupList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                showGroup(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        groupList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showGroup(position);
                chanList.requestFocus();
            }
        });
        chanList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                play(position);
            }
        });
        chanList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                toggleFavorite(position);
                return true;
            }
        });

        loadGroups();
    }

    private void styleList(ListView l) {
        l.setDivider(null);
        l.setDividerHeight(Ui.u(this, 3));
        l.setSelector(Ui.listSelector(this));
        l.setVerticalScrollBarEnabled(false);
        l.setCacheColorHint(0);
        l.setFocusable(true);
        l.setScrollingCacheEnabled(false);
    }

    private void loadGroups() {
        if (key.equals("fav")) {
            groups = new ArrayList<Catalog.Group>();
            List<Channel> fav = Favorites.all(this);
            if (!fav.isEmpty()) groups.add(newGroup("Mes favoris", fav));
        }
        groupAdapter.notifyDataSetChanged();
        if (groups.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(key.equals("fav")
                    ? "Aucun favori pour l'instant.\nDans une liste, appuyez longuement sur OK sur une chaîne pour l'ajouter."
                    : "Aucune chaîne dans cette rubrique.");
            chanAdapter.setData(new ArrayList<Channel>());
            return;
        }
        emptyView.setVisibility(View.GONE);
        if (currentGroup >= groups.size()) currentGroup = 0;
        groupList.setSelection(currentGroup);
        showGroup(currentGroup);
        groupList.requestFocus();
    }

    private Catalog.Group newGroup(String t, List<Channel> l) {
        return Catalog.newGroup(t, l);
    }

    private void showGroup(int position) {
        if (position < 0 || position >= groups.size()) return;
        currentGroup = position;
        groupAdapter.notifyDataSetChanged();
        chanAdapter.setData(groups.get(position).channels);
        chanList.setSelection(0);
    }

    private void play(int position) {
        if (currentGroup >= groups.size()) return;
        Catalog.Group g = groups.get(currentGroup);
        Catalog.queue = g.channels;
        Catalog.queueIndex = position;
        Catalog.queueTitle = g.title;
        startActivity(new Intent(this, PlayerActivity.class));
    }

    private void toggleFavorite(int position) {
        if (currentGroup >= groups.size()) return;
        List<Channel> list = groups.get(currentGroup).channels;
        if (position < 0 || position >= list.size()) return;
        boolean now = Favorites.toggle(this, list.get(position));
        Toast.makeText(this, now ? "Ajoutée aux favoris" : "Retirée des favoris", Toast.LENGTH_SHORT).show();
        if (key.equals("fav")) {
            loadGroups();
        } else {
            chanAdapter.refreshFavorites();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if ((keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_PROG_YELLOW) && chanList.hasFocus()) {
            toggleFavorite(chanList.getSelectedItemPosition());
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override
    public void onBackPressed() {
        if (chanList.hasFocus() && groups.size() > 1) {
            groupList.requestFocus();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        if (chanAdapter != null) chanAdapter.refreshFavorites();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.immersive(this);
    }

    /** Liste de gauche : les groupes. */
    private class GroupAdapter extends BaseAdapter {
        @Override public int getCount() { return groups.size(); }
        @Override public Object getItem(int i) { return groups.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            TextView t;
            if (convert == null) {
                t = Ui.text(BrowseActivity.this, "", 17, Ui.TEXT, true);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setPadding(Ui.u(BrowseActivity.this, 14), 0, Ui.u(BrowseActivity.this, 10), 0);
                t.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.u(BrowseActivity.this, 66)));
            } else {
                t = (TextView) convert;
            }
            Catalog.Group g = groups.get(position);
            t.setText(g.title + "\n" + g.channels.size() + " chaînes");
            boolean sel = position == currentGroup;
            t.setTextColor(sel ? Ui.CYAN : Ui.TEXT);
            t.setBackgroundDrawable(sel ? Ui.rect(BrowseActivity.this, Ui.SURFACE2, 8) : null);
            return t;
        }
    }
}
