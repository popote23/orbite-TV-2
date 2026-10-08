package com.orbitv.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Chaînes favorites (mémorisées sur la box). */
public final class Favorites {
    private static final String PREFS = "orbitv_fav";
    private static final String KEY = "list";
    private static final String SEP = "\u0001";
    private Favorites() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static List<String> raw(Context c) {
        String s = sp(c).getString(KEY, "");
        List<String> out = new ArrayList<String>();
        if (s.length() == 0) return out;
        for (String line : s.split("\n")) {
            if (line.length() > 0) out.add(line);
        }
        return out;
    }

    private static void save(Context c, List<String> lines) {
        StringBuilder b = new StringBuilder();
        for (String l : lines) b.append(l).append('\n');
        sp(c).edit().putString(KEY, b.toString()).apply();
    }

    public static List<Channel> all(Context c) {
        List<Channel> out = new ArrayList<Channel>();
        for (String l : raw(c)) {
            String[] p = l.split(SEP, -1);
            if (p.length >= 5) out.add(new Channel(p[0], p[1], p[2], "", p[3], p[4]));
            else if (p.length >= 3) out.add(new Channel(p[0], p[1], p[2], ""));
        }
        return out;
    }

    public static Set<String> urls(Context c) {
        Set<String> s = new HashSet<String>();
        for (String l : raw(c)) {
            String[] p = l.split(SEP, -1);
            if (p.length >= 2) s.add(p[1]);
        }
        return s;
    }

    /** @return true si la chaîne est maintenant en favoris. */
    public static boolean toggle(Context c, Channel ch) {
        List<String> lines = raw(c);
        for (int i = 0; i < lines.size(); i++) {
            String[] p = lines.get(i).split(SEP, -1);
            if (p.length >= 2 && p[1].equals(ch.url)) {
                lines.remove(i);
                save(c, lines);
                return false;
            }
        }
        lines.add(ch.title + SEP + ch.url + SEP + ch.logo + SEP + ch.referrer + SEP + ch.userAgent);
        save(c, lines);
        return true;
    }
}
