package com.orbitv.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Charge les listes de chaînes gratuites (iptv-org) et les range comme sur un récepteur :
 * pays, cinéma, documentaires, musique, autres chaînes, satellites.
 */
public final class Catalog {
    private Catalog() {}

    public static final class Group {
        public final String title;
        public final List<Channel> channels;
        Group(String title, List<Channel> channels) {
            this.title = title;
            this.channels = channels;
        }
    }

    public static final class Section {
        public final String key, title, subtitle;
        public final List<Group> groups = new ArrayList<Group>();
        Section(String key, String title, String subtitle) {
            this.key = key;
            this.title = title;
            this.subtitle = subtitle;
        }
        public int count() {
            int n = 0;
            for (Group g : groups) n += g.channels.size();
            return n;
        }
    }

    // ---- état du chargement (lu par l'interface) ----
    public static volatile boolean loading = false;
    public static volatile boolean loaded = false;
    public static final AtomicInteger done = new AtomicInteger(0);
    public static volatile int total = 1;

    // ---- file de lecture partagée avec le lecteur ----
    public static List<Channel> queue = new ArrayList<Channel>();
    public static int queueIndex = 0;
    public static String queueTitle = "";

    private static volatile Map<String, Section> built = new LinkedHashMap<String, Section>();

    public static Section section(String key) {
        return built.get(key);
    }

    public static Group newGroup(String title, List<Channel> channels) {
        return new Group(title, channels);
    }

    public static int totalChannels() {
        int n = 0;
        for (Section s : built.values()) n += s.count();
        return n;
    }

    // ---- sources ----
    private static final String BASE = "https://iptv-org.github.io/iptv/";
    private static final long CACHE_MS = 6L * 60L * 60L * 1000L;

    // {clé, chemin1, chemin de secours...}
    private static final String[][] JOBS = {
            {"c:ma", "countries/ma.m3u", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/ma.m3u"},
            {"c:dz", "countries/dz.m3u", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/dz.m3u"},
            {"c:tn", "countries/tn.m3u", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/tn.m3u"},
            {"c:eg", "countries/eg.m3u", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/eg.m3u"},
            {"c:fr", "countries/fr.m3u", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/fr.m3u"},
            {"c:uk", "countries/uk.m3u", "countries/gb.m3u", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/uk.m3u"},
            {"c:us", "countries/us.m3u", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us.m3u"},
            {"l:ara", "languages/ara.m3u"},
            {"l:fra", "languages/fra.m3u"},
            {"l:eng", "languages/eng.m3u"},
            {"k:movies", "categories/movies.m3u"},
            {"k:documentary", "categories/documentary.m3u"},
            {"k:music", "categories/music.m3u"},
            {"k:news", "categories/news.m3u"},
            {"k:sports", "categories/sports.m3u"},
            {"k:kids", "categories/kids.m3u"},
            {"k:animation", "categories/animation.m3u"},
            {"k:family", "categories/family.m3u"},
            {"k:entertainment", "categories/entertainment.m3u"},
            {"k:series", "categories/series.m3u"},
            {"k:comedy", "categories/comedy.m3u"},
            {"k:general", "categories/general.m3u"},
            {"k:religious", "categories/religious.m3u"},
            {"k:culture", "categories/culture.m3u"},
            {"k:education", "categories/education.m3u"},
            {"k:science", "categories/science.m3u"},
            {"k:lifestyle", "categories/lifestyle.m3u"},
            {"k:cooking", "categories/cooking.m3u"},
            {"k:travel", "categories/travel.m3u"},
            {"k:outdoor", "categories/outdoor.m3u"},
            {"k:relax", "categories/relax.m3u"},
            {"k:classic", "categories/classic.m3u"},
            {"k:weather", "categories/weather.m3u"},
            {"k:business", "categories/business.m3u"},
    };

    private static final Set<String> AR_CC = new HashSet<String>(Arrays.asList(
            "eg", "ma", "dz", "tn", "sa", "ae", "lb", "sy", "jo", "iq", "kw", "qa", "bh", "om", "ye", "ly", "sd", "ps", "mr"));
    private static final Set<String> FR_CC = new HashSet<String>(Arrays.asList("fr"));
    private static final Set<String> EN_CC = new HashSet<String>(Arrays.asList("uk", "gb", "us"));

    private static final Pattern ADULT = Pattern.compile(
            "(?i)\\b(xxx|adult|porn\\w*|erotic\\w*|playboy|hustler|brazzers|sexy|sex)\\b");

    private static final Comparator<Channel> BY_TITLE = new Comparator<Channel>() {
        @Override
        public int compare(Channel a, Channel b) {
            return a.title.toLowerCase(Locale.ROOT).compareTo(b.title.toLowerCase(Locale.ROOT));
        }
    };

    // =====================================================================
    //  Chargement
    // =====================================================================

    public static synchronized void startLoad(final Context ctx, final boolean force) {
        if (loading) return;
        loading = true;
        loaded = false;
        done.set(0);
        total = JOBS.length;
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runLoad(app, force);
                } catch (Throwable t) {
                    // on garde ce qui a pu être construit
                } finally {
                    loading = false;
                }
            }
        }, "orbitv-load").start();
    }

    private static void runLoad(final Context app, final boolean force) {
        final Map<String, List<Channel>> data = new ConcurrentHashMap<String, List<Channel>>();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (final String[] job : JOBS) {
            futures.add(pool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        String txt = fetch(app, job, force);
                        if (txt != null) data.put(job[0], M3uParser.parse(txt));
                    } catch (Throwable ignored) {
                    }
                    done.incrementAndGet();
                }
            }));
        }
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Throwable ignored) {
            }
        }
        pool.shutdown();

        Map<String, Section> out = build(app, data);
        built = out;
        loaded = totalChannels() > 0;
    }

    // =====================================================================
    //  Construction des rubriques
    // =====================================================================

    private static final class Lang {
        final Set<String> ara, fra, eng;
        Lang(Set<String> a, Set<String> f, Set<String> e) {
            ara = a;
            fra = f;
            eng = e;
        }
        boolean ar(Channel c) { return ara.contains(c.url) || AR_CC.contains(c.countryCode()); }
        boolean fr(Channel c) { return fra.contains(c.url) || FR_CC.contains(c.countryCode()); }
        boolean en(Channel c) { return eng.contains(c.url) || EN_CC.contains(c.countryCode()); }
    }

    private static Set<String> urlSet(List<Channel> l) {
        Set<String> s = new HashSet<String>();
        if (l != null) for (Channel c : l) s.add(c.url);
        return s;
    }

    private static Map<String, Section> build(Context ctx, Map<String, List<Channel>> d) {
        Lang lang = new Lang(urlSet(d.get("l:ara")), urlSet(d.get("l:fra")), urlSet(d.get("l:eng")));
        Map<String, Section> out = new LinkedHashMap<String, Section>();

        // 1) Chaînes par pays
        Section tv = new Section("tv", "Chaînes TV", "Maroc · Algérie · Tunisie · Égypte · France · UK · USA");
        addGroup(tv, "Chaînes marocaines", d.get("c:ma"), true);
        addGroup(tv, "Chaînes algériennes", d.get("c:dz"), true);
        addGroup(tv, "Chaînes tunisiennes", d.get("c:tn"), true);
        addGroup(tv, "Chaînes égyptiennes", d.get("c:eg"), true);
        addGroup(tv, "Chaînes françaises", d.get("c:fr"), true);
        addGroup(tv, "Chaînes anglaises", d.get("c:uk"), true);
        addGroup(tv, "Chaînes USA", d.get("c:us"), true);
        out.put("tv", tv);

        // 2) Cinéma
        Section cinema = new Section("cinema", "Cinéma", "Arabe · Égyptien · Français · Anglais");
        List<Channel> movies = nn(d.get("k:movies"));
        addGroup(cinema, "Cinéma arabe et égyptien", filter(movies, lang, 'a'), true);
        addGroup(cinema, "Cinéma français", filter(movies, lang, 'f'), true);
        addGroup(cinema, "Cinéma anglais et américain", filter(movies, lang, 'e'), true);
        addGroup(cinema, "Cinéma - autres pays", filter(movies, lang, 'o'), true);
        out.put("cinema", cinema);

        // 3) Documentaires
        Section docs = new Section("docs", "Documentaires", "Arabe · Français · Monde");
        List<Channel> doc = nn(d.get("k:documentary"));
        addGroup(docs, "Documentaires arabes", filter(doc, lang, 'a'), true);
        addGroup(docs, "Documentaires français", filter(doc, lang, 'f'), true);
        addGroup(docs, "Documentaires du monde", filter(doc, lang, 'w'), true);
        out.put("docs", docs);

        // 4) Musique
        Section music = new Section("music", "Musique", "Arabe · Français · Monde");
        List<Channel> mus = nn(d.get("k:music"));
        addGroup(music, "Musique arabe", filter(mus, lang, 'a'), true);
        addGroup(music, "Musique française", filter(mus, lang, 'f'), true);
        addGroup(music, "Musique du monde", filter(mus, lang, 'w'), true);
        out.put("music", music);

        // 5) Autres chaînes (arabe / français / anglais)
        Section other = new Section("other", "Autres chaînes", "Infos · Sport · Enfants · Séries · Religion…");
        addGroup(other, "Infos", wanted(lang, d, "k:news"), true);
        addGroup(other, "Sport", wanted(lang, d, "k:sports"), true);
        addGroup(other, "Enfants et animation", wanted(lang, d, "k:kids", "k:animation", "k:family"), true);
        addGroup(other, "Divertissement", wanted(lang, d, "k:entertainment"), true);
        addGroup(other, "Séries", wanted(lang, d, "k:series"), true);
        addGroup(other, "Comédie", wanted(lang, d, "k:comedy"), true);
        addGroup(other, "Généralistes", wanted(lang, d, "k:general"), true);
        addGroup(other, "Religion", wanted(lang, d, "k:religious"), true);
        addGroup(other, "Culture et éducation", wanted(lang, d, "k:culture", "k:education", "k:science"), true);
        addGroup(other, "Style de vie, cuisine, voyage",
                wanted(lang, d, "k:lifestyle", "k:cooking", "k:travel", "k:outdoor", "k:relax"), true);
        addGroup(other, "Classiques", wanted(lang, d, "k:classic"), true);
        addGroup(other, "Météo et business", wanted(lang, d, "k:weather", "k:business"), true);
        out.put("other", other);

        // 6) Satellites (Nilesat en premier)
        out.put("sat", buildSatellites(ctx, d));
        return out;
    }

    private static List<Channel> nn(List<Channel> l) {
        return l == null ? new ArrayList<Channel>() : l;
    }

    /** a = arabe/égyptien, f = français, e = anglais/US, o = ni l'un ni l'autre, w = tout sauf arabe et français. */
    private static List<Channel> filter(List<Channel> src, Lang lang, char mode) {
        List<Channel> out = new ArrayList<Channel>();
        for (Channel c : src) {
            boolean ar = lang.ar(c), fr = lang.fr(c), en = lang.en(c);
            boolean ok;
            switch (mode) {
                case 'a': ok = ar; break;
                case 'f': ok = fr; break;
                case 'e': ok = en; break;
                case 'o': ok = !ar && !fr && !en; break;
                default: ok = !ar && !fr; break;
            }
            if (ok) out.add(c);
        }
        return out;
    }

    /** Chaînes de ces catégories, limitées à l'arabe / français / anglais. */
    private static List<Channel> wanted(Lang lang, Map<String, List<Channel>> d, String... keys) {
        List<Channel> out = new ArrayList<Channel>();
        Set<String> seen = new HashSet<String>();
        for (String k : keys) {
            for (Channel c : nn(d.get(k))) {
                if (!seen.add(c.url)) continue;
                if (lang.ar(c) || lang.fr(c) || lang.en(c)) out.add(c);
            }
        }
        return out;
    }

    private static boolean isAdult(Channel c) {
        return ADULT.matcher(c.title).find() || c.title.contains("18+");
    }

    private static void addGroup(Section s, String title, List<Channel> src, boolean sort) {
        if (src == null || src.isEmpty()) return;
        List<Channel> clean = new ArrayList<Channel>();
        for (Channel c : src) if (!isAdult(c)) clean.add(c);
        if (sort) Collections.sort(clean, BY_TITLE);
        List<Channel> merged = mergeDuplicates(clean);
        if (!merged.isEmpty()) s.groups.add(new Group(title, merged));
    }

    /** Même chaîne présente plusieurs fois : une seule ligne, les autres flux servent de secours. */
    private static List<Channel> mergeDuplicates(List<Channel> in) {
        Map<String, Channel> map = new LinkedHashMap<String, Channel>();
        for (Channel c : in) {
            String key = c.normName();
            if (key.length() == 0) continue;
            Channel ex = map.get(key);
            if (ex == null) {
                map.put(key, new Channel(c));
            } else if (!ex.url.equals(c.url) && ex.alts.size() < 4 && !hasAlt(ex, c.url)) {
                ex.alts.add(new Channel(c));
            }
        }
        return new ArrayList<Channel>(map.values());
    }

    private static boolean hasAlt(Channel ex, String url) {
        for (Channel a : ex.alts) if (a.url.equals(url)) return true;
        return false;
    }

    // ---- satellites ----

    private static Section buildSatellites(Context ctx, Map<String, List<Channel>> d) {
        Section sat = new Section("sat", "Satellites", "Nilesat · Hotbird · Arabsat · Eutelsat 5W");
        Map<String, List<String>> defs = SatelliteData.load(ctx);
        if (defs.isEmpty()) return sat;

        // tous les flux connus, sans doublons
        List<Channel> pool = new ArrayList<Channel>();
        Set<String> seen = new HashSet<String>();
        for (List<Channel> l : d.values()) {
            for (Channel c : l) {
                if (isAdult(c) || !seen.add(c.url)) continue;
                pool.add(c);
            }
        }
        Collections.sort(pool, BY_TITLE);
        String[] norms = new String[pool.size()];
        for (int i = 0; i < norms.length; i++) norms[i] = pool.get(i).normName();

        for (Map.Entry<String, List<String>> e : defs.entrySet()) {
            List<Channel> found = new ArrayList<Channel>();
            for (String entry : e.getValue()) {
                String en = Channel.norm(entry);
                if (en.length() == 0) continue;
                int hits = 0;
                for (int i = 0; i < norms.length && hits < 4; i++) {
                    if (matches(en, norms[i])) {
                        found.add(pool.get(i));
                        hits++;
                    }
                }
            }
            addGroup(sat, e.getKey(), found, false);
        }
        return sat;
    }

    private static String stripSuffix(String s) {
        boolean changed = true;
        while (changed) {
            changed = false;
            if (s.length() > 2 && s.endsWith("hd")) { s = s.substring(0, s.length() - 2); changed = true; }
            if (s.length() > 2 && s.endsWith("tv")) { s = s.substring(0, s.length() - 2); changed = true; }
        }
        return s;
    }

    private static boolean matches(String entryNorm, String chNorm) {
        if (entryNorm.length() <= 4) {
            return stripSuffix(chNorm).equals(stripSuffix(entryNorm));
        }
        return chNorm.startsWith(entryNorm);
    }

    // =====================================================================
    //  Réseau et cache
    // =====================================================================

    private static String fetch(Context ctx, String[] job, boolean force) {
        File dir = new File(ctx.getFilesDir(), "m3u");
        dir.mkdirs();
        for (int i = 1; i < job.length; i++) {
            String p = job[i];
            File f = new File(dir, p.replaceAll("[^A-Za-z0-9._-]", "_"));
            if (!force && f.exists() && System.currentTimeMillis() - f.lastModified() < CACHE_MS) {
                String t = readFile(f);
                if (t != null && t.length() > 20) return t;
            }
            String t = download(p.startsWith("http") ? p : BASE + p);
            if (t != null && t.length() > 20) {
                writeFile(f, t);
                return t;
            }
            if (f.exists()) {
                t = readFile(f);
                if (t != null && t.length() > 20) return t;
            }
        }
        return null;
    }

    private static String download(String address) {
        HttpURLConnection c = null;
        try {
            String url = address;
            for (int hop = 0; hop < 4; hop++) {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(25000);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) OrbiTV/1.0");
                int code = c.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String loc = c.getHeaderField("Location");
                    c.disconnect();
                    if (loc == null) return null;
                    url = new URL(new URL(url), loc).toString();
                    continue;
                }
                if (code != 200) return null;
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"), 65536);
                StringBuilder b = new StringBuilder(1 << 16);
                char[] buf = new char[16384];
                int n;
                while ((n = r.read(buf)) > 0) {
                    b.append(buf, 0, n);
                    if (b.length() > 30000000) break;
                }
                r.close();
                return b.toString();
            }
            return null;
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String readFile(File f) {
        try {
            InputStream in = new FileInputStream(f);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"), 65536);
            StringBuilder b = new StringBuilder((int) Math.min(f.length(), 30000000L));
            char[] buf = new char[16384];
            int n;
            while ((n = r.read(buf)) > 0) b.append(buf, 0, n);
            r.close();
            return b.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void writeFile(File f, String text) {
        try {
            FileOutputStream o = new FileOutputStream(f);
            o.write(text.getBytes("UTF-8"));
            o.close();
        } catch (Throwable ignored) {
        }
    }
}
