package com.robot.arkadas;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Keeps the app's web files (index.html, robot.js, ...) up to date from GitHub so new features
 * arrive without reinstalling the APK. Files ship inside the APK as a fallback; a newer copy
 * downloaded from GitHub is kept in internal storage and used instead. Works offline with
 * whatever was downloaded last.
 */
final class WebAssets {

    static final String DEFAULT_BASE = "https://raw.githubusercontent.com/gokhangeo/trend/robot/assets/";

    private WebAssets() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("robot", Context.MODE_PRIVATE);
    }

    static File dir(Context c) {
        return new File(c.getFilesDir(), "web");
    }

    static int bundledVersion(Context c) {
        try {
            InputStream in = c.getAssets().open("version.json");
            return new JSONObject(new String(readAll(in), "UTF-8")).optInt("version", 0);
        } catch (Exception e) {
            return 0;
        }
    }

    static int downloadedVersion(Context c) {
        return new File(dir(c), "index.html").exists() ? prefs(c).getInt("webVersion", 0) : 0;
    }

    static int currentVersion(Context c) {
        return Math.max(bundledVersion(c), downloadedVersion(c));
    }

    /** URL to load for a page: the downloaded copy when it is newer than the bundled one. */
    static String url(Context c, String file) {
        File f = new File(dir(c), file);
        if (downloadedVersion(c) > bundledVersion(c) && f.exists()) return "file://" + f.getAbsolutePath();
        return "file:///android_asset/" + file;
    }

    static int appVersion(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Downloads a newer version if GitHub has one. Blocking; call off the main thread.
     * Returns the new version number, or 0 when nothing changed.
     */
    static int update(Context c) throws Exception {
        String base = prefs(c).getString("updateBase", DEFAULT_BASE);
        JSONObject manifest = new JSONObject(new String(get(base + "version.json?t=" + System.currentTimeMillis()), "UTF-8"));
        int version = manifest.optInt("version", 0);
        if (version <= currentVersion(c)) return 0;
        // Web code that needs newer Android code than this APK has is skipped until the APK is updated.
        if (manifest.optInt("minApp", 0) > appVersion(c)) return 0;

        JSONArray files = manifest.getJSONArray("files");
        File tmp = new File(c.getFilesDir(), "web_new");
        deleteDir(tmp);
        tmp.mkdirs();
        for (int i = 0; i < files.length(); i++) {
            String name = files.getString(i);
            if (!name.matches("[A-Za-z0-9_.-]+")) throw new Exception("Geçersiz dosya adı: " + name);
            byte[] data = get(base + name + "?v=" + version);
            if (data.length == 0) throw new Exception("Boş dosya: " + name);
            FileOutputStream fo = new FileOutputStream(new File(tmp, name));
            fo.write(data);
            fo.close();
        }
        if (!new File(tmp, "index.html").exists()) throw new Exception("index.html eksik");

        // Swap in the new files only once everything downloaded, so a broken download never half-applies.
        File live = dir(c);
        deleteDir(live);
        if (!tmp.renameTo(live)) throw new Exception("Dosyalar kaydedilemedi");
        prefs(c).edit().putInt("webVersion", version).apply();
        return version;
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setUseCaches(false);
        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            throw new Exception("Güncelleme sunucusu " + code + " döndü");
        }
        byte[] data = readAll(conn.getInputStream());
        conn.disconnect();
        return data;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toByteArray();
    }

    private static void deleteDir(File d) {
        File[] kids = d.listFiles();
        if (kids != null) for (File k : kids) k.delete();
        d.delete();
    }
}
