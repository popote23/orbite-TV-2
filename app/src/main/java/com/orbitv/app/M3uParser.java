package com.orbitv.app;

import java.util.ArrayList;
import java.util.List;

/** Lecture simple d'une playlist M3U. */
public final class M3uParser {
    private M3uParser() {}

    public static List<Channel> parse(String text) {
        List<Channel> out = new ArrayList<Channel>();
        if (text == null) return out;
        String name = null, logo = null, id = null, ref = "", ua = "";
        String[] lines = text.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.length() == 0) continue;
            if (line.startsWith("#EXTINF")) {
                id = attr(line, "tvg-id");
                logo = attr(line, "tvg-logo");
                int q = line.lastIndexOf('"');
                int comma = line.indexOf(',', q < 0 ? 0 : q);
                name = comma >= 0 ? line.substring(comma + 1).trim() : "";
            } else if (line.startsWith("#EXTVLCOPT:")) {
                String o = line.substring(11);
                if (o.startsWith("http-referrer=")) ref = o.substring(14).trim();
                else if (o.startsWith("http-referer=")) ref = o.substring(13).trim();
                else if (o.startsWith("http-user-agent=")) ua = o.substring(16).trim();
            } else if (line.startsWith("#")) {
                continue;
            } else if (name != null) {
                if (line.startsWith("http")) {
                    out.add(new Channel(name, line, logo, id, ref, ua));
                }
                name = null;
                logo = null;
                id = null;
                ref = "";
                ua = "";
            }
        }
        return out;
    }

    private static String attr(String line, String key) {
        String k = key + "=\"";
        int i = line.indexOf(k);
        if (i < 0) return "";
        i += k.length();
        int j = line.indexOf('"', i);
        return j < 0 ? "" : line.substring(i, j);
    }
}
