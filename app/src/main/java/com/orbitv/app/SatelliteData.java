package com.orbitv.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lit assets/satellites.txt : "@ Nom du satellite" puis une chaîne par ligne. */
public final class SatelliteData {
    private SatelliteData() {}

    public static Map<String, List<String>> load(Context ctx) {
        Map<String, List<String>> out = new LinkedHashMap<String, List<String>>();
        BufferedReader r = null;
        try {
            InputStream in = ctx.getAssets().open("satellites.txt");
            r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            List<String> cur = null;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.startsWith("#")) continue;
                if (line.startsWith("@")) {
                    cur = new ArrayList<String>();
                    out.put(line.substring(1).trim(), cur);
                } else if (cur != null) {
                    cur.add(line);
                }
            }
        } catch (Throwable t) {
            // fichier absent : pas de satellites
        } finally {
            if (r != null) {
                try { r.close(); } catch (Throwable ignored) { }
            }
        }
        return out;
    }
}
