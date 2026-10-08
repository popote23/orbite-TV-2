package com.orbitv.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Une chaîne TV (avec d'éventuels flux de secours). */
public class Channel {
    public final String title;      // nom propre affiché
    public final String url;        // flux principal
    public final String logo;
    public final String tvgId;
    public final String referrer;   // en-tête Referer exigé par certains flux
    public final String userAgent;  // user-agent exigé par certains flux
    public final List<Channel> alts = new ArrayList<Channel>(); // flux de secours

    public Channel(String rawName, String url, String logo, String tvgId) {
        this(rawName, url, logo, tvgId, "", "");
    }

    public Channel(String rawName, String url, String logo, String tvgId, String referrer, String userAgent) {
        this.title = cleanName(rawName);
        this.url = url;
        this.logo = logo == null ? "" : logo;
        this.tvgId = tvgId == null ? "" : tvgId;
        this.referrer = referrer == null ? "" : referrer;
        this.userAgent = userAgent == null ? "" : userAgent;
    }

    /** Copie (sans les flux de secours). */
    public Channel(Channel o) {
        this.title = o.title;
        this.url = o.url;
        this.logo = o.logo;
        this.tvgId = o.tvgId;
        this.referrer = o.referrer;
        this.userAgent = o.userAgent;
    }

    static String cleanName(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s*\\((\\d{3,4}[pi]|DVR)\\)", "");
        s = s.replaceAll("\\s*\\[[^\\]]*\\]", "");
        return s.trim();
    }

    /** Code pays déduit de tvg-id ("Nom.ma@SD" -> "ma"). */
    public String countryCode() {
        String s = tvgId;
        int at = s.indexOf('@');
        if (at >= 0) s = s.substring(0, at);
        int dot = s.lastIndexOf('.');
        if (dot < 0 || dot == s.length() - 1) return "";
        return s.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Nom normalisé pour comparer (minuscules, sans symboles). */
    public String normName() {
        return norm(title);
    }

    static String norm(String s) {
        StringBuilder b = new StringBuilder();
        String l = s.toLowerCase(Locale.ROOT);
        for (int i = 0; i < l.length(); i++) {
            char c = l.charAt(i);
            if (Character.isLetterOrDigit(c)) b.append(c);
        }
        return b.toString();
    }

    /** Idem norm() mais sans suffixe hd / tv (pour les noms courts). */
    static String core(String normalized) {
        String s = normalized;
        boolean changed = true;
        while (changed) {
            changed = false;
            if (s.length() > 3 && s.endsWith("hd")) { s = s.substring(0, s.length() - 2); changed = true; }
            if (s.length() > 3 && s.endsWith("tv")) { s = s.substring(0, s.length() - 2); changed = true; }
        }
        return s;
    }
}
