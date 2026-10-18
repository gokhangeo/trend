package com.robot.arkadas;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    public static final String EXTRA_LISTEN = "listen";
    static final String DEFAULT_MODEL = "claude-haiku-5-5";

    private static final int REQ_MIC = 7;
    private static final int REQ_CAMERA = 8;
    private static final int REQ_PICK = 9;
    private static final int REQ_STORAGE = 10;
    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    private WebView web;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private SpeechRecognizer recognizer;
    private SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean pageReady = false;
    private boolean listenWhenReady = false;
    private boolean wantOverlay = false;
    private MediaPlayer player;
    private int speakSeq = 0;
    private boolean cloudErrorShown = false;
    private Uri photoUri;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("robot", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.parseColor("#141414"));
        getWindow().setNavigationBarColor(Color.parseColor("#141414"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#141414"));
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(true); // pages downloaded from GitHub live in internal storage
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (listenWhenReady) {
                    listenWhenReady = false;
                    js("startTalk");
                }
            }
        });
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        web.loadUrl(WebAssets.url(this, "index.html"));
        checkForUpdate(false);
        handleIntent(getIntent());
        ensureOverlay(this);

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS) {
                    js("onTtsError", "Konuşma motoru başlatılamadı");
                    return;
                }
                int r = tts.setLanguage(new Locale("tr", "TR"));
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    js("onTtsError", "Telefonda Türkçe ses paketi yok. Ayarlar > Metin okuma bölümünden Türkçe'yi indirin.");
                }
                applyVoice();
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String id) { js("onSpeakStart"); }

                    @Override
                    public void onDone(String id) { js("onSpeakEnd"); }

                    @Override
                    public void onError(String id) { js("onSpeakEnd"); }
                });
                ttsReady = true;
            }
        });

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_LISTEN, false)) return;
        intent.removeExtra(EXTRA_LISTEN);
        if (pageReady) js("startTalk");
        else listenWhenReady = true;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (code == REQ_MIC && !granted) {
            js("onListenError", "Mikrofon izni olmadan seni duyamam.");
        } else if (code == REQ_STORAGE) {
            if (granted) launchCamera();
            else js("onPhotoCancel");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        RobotOverlayService.setHidden(true);
        if (wantOverlay) {
            wantOverlay = false;
            if (Settings.canDrawOverlays(this)) startOverlay();
            else js("onOverlayDenied");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Leaving the app (home button, switching apps) brings the floating robot back if it was stopped.
        ensureOverlay(this);
    }

    /** Starts the floating robot when the parent turned it on and the permission is still granted. */
    static void ensureOverlay(android.content.Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("robot", MODE_PRIVATE);
        if (!p.getBoolean("overlay", false) || RobotOverlayService.isRunning() || !Settings.canDrawOverlays(ctx)) return;
        try {
            ctx.startForegroundService(new Intent(ctx, RobotOverlayService.class));
        } catch (Exception ignored) { }
    }

    @Override
    protected void onPause() {
        super.onPause();
        RobotOverlayService.setHidden(false);
        if (recognizer != null) recognizer.cancel();
        stopAllSpeech();
        js("onPaused");
    }

    @Override
    protected void onDestroy() {
        if (player != null) player.release();
        if (recognizer != null) recognizer.destroy();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    /** Calls window[fn](...args) in the page; every argument is passed as a JS string. */
    private void js(final String fn, final String... args) {
        main.post(new Runnable() {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder("window.").append(fn).append("&&window.").append(fn).append("(");
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(JSONObject.quote(args[i]));
                }
                sb.append(")");
                web.evaluateJavascript(sb.toString(), null);
            }
        });
    }

    // ---------- voice ----------

    private void applyVoice() {
        tts.setSpeechRate(prefs.getFloat("rate", 1.0f));
        tts.setPitch(prefs.getFloat("pitch", 1.15f));
        String name = prefs.getString("voice", "");
        Voice chosen = null;
        Voice female = null;
        Set<Voice> voices = null;
        try { voices = tts.getVoices(); } catch (Exception ignored) { }
        if (voices == null) return;
        for (Voice v : voices) {
            if (!"tr".equals(v.getLocale().getLanguage())) continue;
            if (v.getName().equals(name)) chosen = v;
            if (female == null && v.getName().toLowerCase(Locale.ROOT).contains("female")) female = v;
        }
        if (chosen == null) chosen = female;
        if (chosen != null) tts.setVoice(chosen);
    }

    private String voicesJson() {
        JSONArray out = new JSONArray();
        if (!ttsReady) return out.toString();
        try {
            ArrayList<Voice> list = new ArrayList<Voice>();
            for (Voice v : tts.getVoices()) {
                if ("tr".equals(v.getLocale().getLanguage())) list.add(v);
            }
            java.util.Collections.sort(list, new java.util.Comparator<Voice>() {
                @Override
                public int compare(Voice a, Voice b) { return a.getName().compareTo(b.getName()); }
            });
            for (Voice v : list) {
                out.put(new JSONObject()
                        .put("name", v.getName())
                        .put("network", v.isNetworkConnectionRequired())
                        .put("female", v.getName().toLowerCase(Locale.ROOT).contains("female")));
            }
        } catch (Exception ignored) { }
        return out.toString();
    }

    // ---------- listening ----------

    private void startListening() {
        startListening("tr-TR", 1500);
    }

    /** lang: recognizer language ("tr-TR" or "en-US"); silenceMs: how long a pause ends the turn. */
    private void startListening(String lang, long silenceMs) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            js("onListenError", "Önce mikrofon iznini vermelisin.");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            js("onListenError", "Bu telefonda ses tanıma yok. Google uygulamasını yükleyin.");
            return;
        }
        stopAllSpeech();
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle b) { js("onListenStart"); }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rms) { js("onLevel", String.valueOf(rms)); }
                @Override public void onBufferReceived(byte[] buf) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onEvent(int type, Bundle b) { }

                @Override
                public void onError(int error) {
                    if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                        js("onListenError", "İnternet bağlantısı yok gibi görünüyor.");
                    } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        js("onListenError", "Mikrofon izni gerekli.");
                    } else {
                        js("onNothingHeard");
                    }
                }

                @Override
                public void onResults(Bundle b) {
                    ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty() && list.get(0).trim().length() > 0) {
                        js("onHeard", list.get(0));
                    } else {
                        js("onNothingHeard");
                    }
                }

                @Override
                public void onPartialResults(Bundle b) {
                    ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty()) js("onPartial", list.get(0));
                }
            });
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs);
        recognizer.startListening(i);
    }

    // ---------- homework photo ----------

    private void takePhoto() {
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        launchCamera();
    }

    private void launchCamera() {
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, "odev_" + System.currentTimeMillis() + ".jpg");
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RobotArkadas");
            photoUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            deletePhoto();
            // No camera app: let the parent pick an existing picture instead.
            try {
                Intent p = new Intent(Intent.ACTION_GET_CONTENT);
                p.setType("image/*");
                startActivityForResult(p, REQ_PICK);
            } catch (Exception e2) {
                js("onPhotoError", "Kamera açılamadı.");
            }
        }
    }

    private void deletePhoto() {
        if (photoUri == null) return;
        try { getContentResolver().delete(photoUri, null, null); } catch (Exception ignored) { }
        photoUri = null;
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_CAMERA && req != REQ_PICK) return;
        final Uri uri = req == REQ_CAMERA ? photoUri : (data != null ? data.getData() : null);
        if (result != RESULT_OK || uri == null) {
            if (req == REQ_CAMERA) deletePhoto();
            js("onPhotoCancel");
            return;
        }
        final boolean fromCamera = req == REQ_CAMERA;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String b64 = encodePhoto(uri);
                    js("onPhoto", b64);
                } catch (Exception e) {
                    js("onPhotoError", "Fotoğrafı okuyamadım.");
                } finally {
                    if (fromCamera) main.post(new Runnable() {
                        @Override public void run() { deletePhoto(); }
                    });
                }
            }
        }).start();
    }

    /** Loads, rotates and shrinks the photo to at most 1280px, returned as base64 JPEG. */
    private String encodePhoto(Uri uri) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = getContentResolver().openInputStream(uri);
        BitmapFactory.decodeStream(in, null, o);
        in.close();
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= 1280) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        in = getContentResolver().openInputStream(uri);
        Bitmap bmp = BitmapFactory.decodeStream(in, null, o2);
        in.close();

        int rotate = 0;
        try {
            in = getContentResolver().openInputStream(uri);
            int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            in.close();
            if (ori == ExifInterface.ORIENTATION_ROTATE_90) rotate = 90;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rotate = 180;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rotate = 270;
        } catch (Exception ignored) { }

        float scale = Math.min(1f, 1280f / Math.max(bmp.getWidth(), bmp.getHeight()));
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        m.postRotate(rotate);
        Bitmap out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 82, bo);
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }

    // ---------- floating robot ----------

    private void startOverlay() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
        prefs.edit().putBoolean("overlay", true).apply();
        startForegroundService(new Intent(this, RobotOverlayService.class));
        RobotOverlayService.setHidden(true);
    }

    // ---------- speech output ----------

    private void stopAllSpeech() {
        speakSeq++;
        if (tts != null) tts.stop();
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }

    private void speakPhone(String text, boolean english, float rateMul) {
        if (!ttsReady) {
            js("onSpeakEnd");
            return;
        }
        if (english) {
            tts.setLanguage(Locale.US);
            tts.setSpeechRate(prefs.getFloat("rate", 1.0f) * rateMul);
            tts.setPitch(prefs.getFloat("pitch", 1.15f));
        } else {
            tts.setLanguage(new Locale("tr", "TR"));
            applyVoice();
            tts.setSpeechRate(prefs.getFloat("rate", 1.0f) * rateMul);
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u" + System.currentTimeMillis());
    }

    /** Speaks with a cloud voice (ElevenLabs or Google); falls back to the phone voice on any error. */
    private void speak(final String text, final boolean english, final float rateMul) {
        stopAllSpeech();
        final String provider = prefs.getString("ttsProvider", "phone");
        if ("phone".equals(provider)) {
            speakPhone(text, english, rateMul);
            return;
        }
        final int seq = speakSeq;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final File mp3 = cloudAudio(provider, text, english, rateMul);
                    main.post(new Runnable() {
                        @Override
                        public void run() { if (seq == speakSeq) playFile(mp3, seq); }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (seq != speakSeq) return;
                            if (!cloudErrorShown) {
                                cloudErrorShown = true;
                                js("onCloudTtsError", e.getMessage() == null ? "Bulut sesi alınamadı" : e.getMessage());
                            }
                            speakPhone(text, english, rateMul);
                        }
                    });
                }
            }
        }).start();
    }

    private void playFile(File f, final int seq) {
        try {
            player = new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) { if (seq == speakSeq) js("onSpeakEnd"); }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    if (seq == speakSeq) js("onSpeakEnd");
                    return true;
                }
            });
            player.prepare();
            player.start();
            js("onSpeakStart");
        } catch (Exception e) {
            js("onSpeakEnd");
        }
    }

    /** Returns an MP3 for the text, reusing a cached copy so repeated phrases cost nothing. */
    private File cloudAudio(String provider, String text, boolean english, float rateMul) throws Exception {
        String voice = prefs.getString("cloudVoice", "");
        float rate = prefs.getFloat("rate", 1.0f) * rateMul, pitch = prefs.getFloat("pitch", 1.15f);
        File dir = new File(getCacheDir(), "voice");
        dir.mkdirs();
        File f = new File(dir, Integer.toHexString((provider + voice + rate + pitch + english + text).hashCode()) + ".mp3");
        if (f.length() > 0) return f;

        byte[] audio;
        if ("eleven".equals(provider)) {
            String key = prefs.getString("elevenKey", "").trim();
            if (key.isEmpty() || voice.isEmpty()) throw new Exception("ElevenLabs anahtarı veya sesi seçilmemiş.");
            JSONObject body = new JSONObject()
                    .put("text", text)
                    .put("model_id", "eleven_multilingual_v2")
                    .put("voice_settings", new JSONObject()
                            .put("stability", 0.45).put("similarity_boost", 0.8).put("style", 0.35)
                            .put("speed", Math.max(0.7, Math.min(1.2, rate))));
            audio = http("https://api.elevenlabs.io/v1/text-to-speech/" + voice + "?output_format=mp3_44100_128",
                    "xi-api-key", key, body.toString());
        } else {
            String key = prefs.getString("googleKey", "").trim();
            if (key.isEmpty()) throw new Exception("Google anahtarı girilmemiş.");
            if (voice.isEmpty()) voice = "tr-TR-Chirp3-HD-Aoede";
            String lang = "tr-TR";
            if (english) {
                // Chirp 3 HD and most Google voices exist under the same name in en-US.
                lang = "en-US";
                voice = voice.replace("tr-TR", "en-US");
            }
            JSONObject cfg = new JSONObject().put("audioEncoding", "MP3").put("speakingRate", rate);
            if (!voice.contains("Chirp")) cfg.put("pitch", Math.max(-20, Math.min(20, (pitch - 1f) * 10f)));
            JSONObject body = new JSONObject()
                    .put("input", new JSONObject().put("text", text))
                    .put("voice", new JSONObject().put("languageCode", lang).put("name", voice))
                    .put("audioConfig", cfg);
            byte[] res = http("https://texttospeech.googleapis.com/v1/text:synthesize?key=" + key, null, null, body.toString());
            audio = Base64.decode(new JSONObject(new String(res, "UTF-8")).getString("audioContent"), Base64.DEFAULT);
        }
        File tmp = new File(dir, f.getName() + ".tmp");
        FileOutputStream fo = new FileOutputStream(tmp);
        fo.write(audio);
        fo.close();
        tmp.renameTo(f);
        trimCache(dir);
        return f;
    }

    private static void trimCache(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= 200) return;
        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
        });
        for (int i = 0; i < files.length - 150; i++) files[i].delete();
    }

    /** Small HTTP helper for the voice services: POST when body is set, GET otherwise. */
    private static byte[] http(String url, String headerName, String headerValue, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        if (headerName != null) c.setRequestProperty(headerName, headerValue);
        if (body != null) {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("content-type", "application/json");
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        if (in != null) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
        }
        c.disconnect();
        if (code == 401 || code == 403) throw new Exception("Ses servisi anahtarı geçersiz veya yetkisiz.");
        if (code == 429) throw new Exception("Ses servisinin kotası doldu.");
        if (code >= 400) throw new Exception("Ses servisi hatası (" + code + ")");
        return bo.toByteArray();
    }

    /** Lists the voices a cloud provider offers, as [{id, label}] JSON. */
    private String cloudVoices(String provider) throws Exception {
        JSONArray out = new JSONArray();
        if ("eleven".equals(provider)) {
            String key = prefs.getString("elevenKey", "").trim();
            JSONObject res = new JSONObject(new String(http("https://api.elevenlabs.io/v1/voices", "xi-api-key", key, null), "UTF-8"));
            JSONArray voices = res.getJSONArray("voices");
            ArrayList<JSONObject> female = new ArrayList<JSONObject>(), other = new ArrayList<JSONObject>();
            for (int i = 0; i < voices.length(); i++) {
                JSONObject v = voices.getJSONObject(i);
                JSONObject labels = v.optJSONObject("labels");
                String gender = labels == null ? "" : labels.optString("gender");
                String age = labels == null ? "" : labels.optString("age");
                String label = v.optString("name")
                        + ("female".equals(gender) ? " · kadın" : "male".equals(gender) ? " · erkek" : "")
                        + (age.contains("young") ? " · genç" : "");
                JSONObject o = new JSONObject().put("id", v.optString("voice_id")).put("label", label);
                if ("female".equals(gender)) female.add(o); else other.add(o);
            }
            for (JSONObject o : female) out.put(o);
            for (JSONObject o : other) out.put(o);
        } else {
            String key = prefs.getString("googleKey", "").trim();
            JSONObject res = new JSONObject(new String(http(
                    "https://texttospeech.googleapis.com/v1/voices?languageCode=tr-TR&key=" + key, null, null, null), "UTF-8"));
            JSONArray voices = res.optJSONArray("voices");
            String[] tiers = {"Chirp3-HD", "Chirp-HD", "Neural2", "Wavenet", "Standard"};
            String[] tierNames = {"en doğal", "çok doğal", "doğal", "iyi", "basit"};
            for (String gender : new String[]{"FEMALE", "MALE"}) {
                for (int t = 0; t < tiers.length; t++) {
                    for (int i = 0; voices != null && i < voices.length(); i++) {
                        JSONObject v = voices.getJSONObject(i);
                        String name = v.optString("name");
                        if (!name.startsWith("tr-TR") || !name.contains("-" + tiers[t] + "-") || !gender.equals(v.optString("ssmlGender"))) continue;
                        String shortName = name.substring(name.lastIndexOf('-') + 1);
                        out.put(new JSONObject().put("id", name).put("label",
                                shortName + " · " + ("FEMALE".equals(gender) ? "kadın" : "erkek") + " · " + tierNames[t]));
                    }
                }
            }
        }
        return out.toString();
    }

    // ---------- updates from GitHub ----------

    /** Fetches newer web files in the background; the page decides when to reload (when it is idle). */
    private void checkForUpdate(final boolean manual) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int v = WebAssets.update(MainActivity.this);
                    if (v > 0) {
                        RobotOverlayService.reloadPage();
                        js("onUpdateReady", String.valueOf(v));
                    } else if (manual) {
                        js("onUpdateResult", "En güncel sürümdesiniz (web sürümü " + WebAssets.currentVersion(MainActivity.this) + ").");
                    }
                } catch (Exception e) {
                    if (manual) js("onUpdateResult", "Güncelleme kontrol edilemedi: " + e.getMessage());
                }
            }
        }).start();
    }

    // ---------- Claude ----------

    /** Sends the conversation to the Claude Messages API and returns the reply text. */
    private String callClaude(String system, JSONArray messages, String effort) throws Exception {
        String key = prefs.getString("apiKey", "").trim();
        if (key.isEmpty()) throw new UserError("Önce ayarlardan Claude API anahtarını girin.");
        String model = prefs.getString("model", DEFAULT_MODEL);

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_tokens", 4096);
        body.put("system", system);
        body.put("messages", messages);
        body.put("output_config", new JSONObject().put("effort", effort));
        boolean fallback = model.startsWith("claude-opus") || model.startsWith("claude-sonnet");
        if (fallback) body.put("fallbacks", "default");

        HttpURLConnection c = (HttpURLConnection) new URL(API_URL).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setRequestProperty("content-type", "application/json");
        c.setRequestProperty("x-api-key", key);
        c.setRequestProperty("anthropic-version", "2023-06-01");
        if (fallback) c.setRequestProperty("anthropic-beta", "server-side-fallback-2026-07-01");

        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();

        int code = c.getResponseCode();
        String text = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        c.disconnect();

        if (code == 401) throw new UserError("API anahtarı geçersiz. Ayarlardan kontrol edin.");
        if (code == 429) throw new UserError("Çok hızlı konuştuk, biraz dinlenelim. Birazdan tekrar dene.");
        if (code == 529 || code >= 500) throw new UserError("Beynim şu an çok meşgul, birazdan tekrar dene.");
        if (code >= 400) {
            String msg = text;
            try { msg = new JSONObject(text).getJSONObject("error").getString("message"); } catch (Exception ignored) { }
            if (msg.toLowerCase(Locale.ROOT).contains("credit")) throw new UserError("API hesabında kredi bitmiş görünüyor.");
            throw new UserError("Hata (" + code + "): " + msg);
        }

        JSONObject res = new JSONObject(text);
        if ("refusal".equals(res.optString("stop_reason"))) {
            return "Bu konuyu konuşamam ama başka bir şey sorabilirsin. İstersen bir bilmece sorayım mı?";
        }
        JSONArray content = res.getJSONArray("content");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.getJSONObject(i);
            if ("text".equals(block.optString("type"))) out.append(block.optString("text"));
        }
        return out.toString().trim();
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    private static float parseFloatOr(String s, float def) {
        try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }

    private static class UserError extends Exception {
        UserError(String m) { super(m); }
    }

    /** Methods callable from index.html as window.Android.* */
    public class Bridge {
        @JavascriptInterface
        public void listen() {
            main.post(new Runnable() {
                @Override public void run() { startListening(); }
            });
        }

        @JavascriptInterface
        public void stopListening() {
            main.post(new Runnable() {
                @Override public void run() { if (recognizer != null) recognizer.cancel(); }
            });
        }

        @JavascriptInterface
        public void speak(final String text) {
            speakWith(text, "tr", "1");
        }

        @JavascriptInterface
        public void speakWith(final String text, final String lang, final String rateMul) {
            final float mul = parseFloatOr(rateMul, 1f);
            main.post(new Runnable() {
                @Override public void run() { MainActivity.this.speak(text, "en".equals(lang), mul); }
            });
        }

        @JavascriptInterface
        public void listenWith(final String lang, final String silenceMs) {
            final long silence = (long) parseFloatOr(silenceMs, 1500);
            main.post(new Runnable() {
                @Override public void run() { startListening(lang, silence); }
            });
        }

        @JavascriptInterface
        public void setLook(String json) {
            prefs.edit().putString("look", json).apply();
            RobotOverlayService.refreshLook();
        }

        @JavascriptInterface
        public void stopSpeaking() {
            main.post(new Runnable() {
                @Override public void run() { stopAllSpeech(); }
            });
        }

        @JavascriptInterface
        public void loadCloudVoices(final String provider) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        js("onCloudVoices", cloudVoices(provider));
                    } catch (Exception e) {
                        js("onCloudVoicesError", e.getMessage() == null ? "Sesler alınamadı" : e.getMessage());
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public void ask(final String system, final String messagesJson, final String effort) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        js("onAnswer", callClaude(system, new JSONArray(messagesJson), effort));
                    } catch (UserError e) {
                        js("onAskError", e.getMessage());
                    } catch (java.net.UnknownHostException e) {
                        js("onAskError", "İnternet bağlantısı yok gibi görünüyor.");
                    } catch (java.net.SocketTimeoutException e) {
                        js("onAskError", "Cevap çok gecikti, tekrar dener misin?");
                    } catch (Exception e) {
                        js("onAskError", "Bir sorun oldu: " + e.getMessage());
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public String get(String key, String def) {
            return prefs.getString(key, def);
        }

        @JavascriptInterface
        public void set(String key, String value) {
            prefs.edit().putString(key, value).apply();
            cloudErrorShown = false;
            if ("botName".equals(key)) RobotWidget.refresh(MainActivity.this);
        }

        @JavascriptInterface
        public void setVoice(String rate, String pitch, String name) {
            try {
                prefs.edit().putFloat("rate", Float.parseFloat(rate)).putFloat("pitch", Float.parseFloat(pitch))
                        .putString("voice", name).apply();
            } catch (NumberFormatException ignored) { }
        }

        @JavascriptInterface
        public String getVoice() {
            return prefs.getFloat("rate", 1.0f) + "," + prefs.getFloat("pitch", 1.15f) + "," + prefs.getString("voice", "");
        }

        @JavascriptInterface
        public String voices() {
            return voicesJson();
        }

        @JavascriptInterface
        public void openTtsSettings() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
                    } catch (Exception ignored) { }
                }
            });
        }

        @JavascriptInterface
        public void takePhoto() {
            main.post(new Runnable() {
                @Override public void run() { MainActivity.this.takePhoto(); }
            });
        }

        @JavascriptInterface
        public void reloadApp() {
            main.post(new Runnable() {
                @Override public void run() { web.loadUrl(WebAssets.url(MainActivity.this, "index.html")); }
            });
        }

        @JavascriptInterface
        public void checkUpdate() {
            checkForUpdate(true);
        }

        @JavascriptInterface
        public String versionInfo() {
            return "Uygulama " + WebAssets.appVersion(MainActivity.this) + " · web " + WebAssets.currentVersion(MainActivity.this);
        }

        @JavascriptInterface
        public void openBatterySettings() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored) { }
                    }
                }
            });
        }

        @JavascriptInterface
        public boolean overlayOn() {
            return prefs.getBoolean("overlay", false);
        }

        @JavascriptInterface
        public void setOverlay(final boolean on) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    if (!on) {
                        prefs.edit().putBoolean("overlay", false).apply();
                        stopService(new Intent(MainActivity.this, RobotOverlayService.class));
                    } else if (Settings.canDrawOverlays(MainActivity.this)) {
                        startOverlay();
                    } else {
                        wantOverlay = true;
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    }
                }
            });
        }
    }
}
