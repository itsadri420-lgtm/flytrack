package com.meine.flytrack;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends Activity {
    WebView web;
    boolean recording = false;
    final List<double[]> rec = new ArrayList<>();
    LocationManager lm;
    final LocationListener locL = new LocationListener() {
        @Override
        public void onLocationChanged(Location l) {
            onLoc(l);
        }

        @Override
        public void onStatusChanged(String p, int s, Bundle b) {
        }

        @Override
        public void onProviderEnabled(String p) {
        }

        @Override
        public void onProviderDisabled(String p) {
        }
    };

    class Bridge {
        @JavascriptInterface
        public void openFile() {
            runOnUiThread(() -> openFileDialog());
        }

        @JavascriptInterface
        public void toggleRec() {
            runOnUiThread(() -> {
                if (recording) stopRec();
                else startRec();
            });
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF0B0F17);
        getWindow().setNavigationBarColor(0xFF0B0F17);
        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(0xFF0B0F17);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0B0F17);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        web.loadDataWithBaseURL("https://flytrack.local/", readAsset("index.html"),
                "text/html", "UTF-8", null);
    }

    String readAsset(String name) {
        try {
            InputStream in = getAssets().open(name);
            return new String(readAll(in), "UTF-8");
        } catch (Exception e) {
            return "<html><body style='background:#000;color:#fff'>Fehler: " + e.getMessage() + "</body></html>";
        }
    }

    void js(String code) {
        web.evaluateJavascript(code, null);
    }

    void sendToPage(byte[] data) {
        String b64 = Base64.encodeToString(data, Base64.NO_WRAP);
        js("window.FT&&FT.loadB64('" + b64 + "')");
    }

    // ---------- Dateien ----------
    void openFileDialog() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, 1);
    }

    @Override
    protected void onActivityResult(int rq, int rs, Intent data) {
        super.onActivityResult(rq, rs, data);
        if (rq != 1 || rs != RESULT_OK || data == null || data.getData() == null) return;
        final Uri u = data.getData();
        new Thread(() -> {
            try {
                InputStream in = getContentResolver().openInputStream(u);
                final byte[] bytes = readAll(in);
                runOnUiThread(() -> sendToPage(bytes));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Fehler: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        if (is == null) return o.toByteArray();
        byte[] buf = new byte[16384];
        int k;
        while ((k = is.read(buf)) > 0) o.write(buf, 0, k);
        is.close();
        return o.toByteArray();
    }

    // ---------- GPS-Aufnahme ----------
    void startRec() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2);
            return;
        }
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null || !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Bitte GPS einschalten", Toast.LENGTH_LONG).show();
            return;
        }
        rec.clear();
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locL);
        } catch (SecurityException e) {
            Toast.makeText(this, "Keine Standort-Erlaubnis", Toast.LENGTH_LONG).show();
            return;
        }
        recording = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        js("window.FT&&FT.recState(true)");
    }

    @Override
    public void onRequestPermissionsResult(int rq, String[] ps, int[] gr) {
        super.onRequestPermissionsResult(rq, ps, gr);
        if (rq == 2) {
            if (gr.length > 0 && gr[0] == PackageManager.PERMISSION_GRANTED) startRec();
            else Toast.makeText(this, "Ohne Standort-Erlaubnis keine Aufnahme", Toast.LENGTH_LONG).show();
        }
    }

    void onLoc(Location l) {
        if (!recording) return;
        if (l.hasAccuracy() && l.getAccuracy() > 60f) return;
        double alt = l.getAltitude();
        if (Build.VERSION.SDK_INT >= 34 && l.hasMslAltitude()) alt = l.getMslAltitudeMeters();
        double t = l.getTime() / 1000.0;
        if (!rec.isEmpty() && t <= rec.get(rec.size() - 1)[0]) return;
        rec.add(new double[]{t, l.getLatitude(), l.getLongitude(), alt});
        js(String.format(Locale.US, "window.FT&&FT.live(%.1f,%.6f,%.6f,%.1f,%.1f)",
                t, l.getLatitude(), l.getLongitude(), alt, l.getSpeed() * 3.6));
    }

    void stopRec() {
        recording = false;
        try {
            if (lm != null) lm.removeUpdates(locL);
        } catch (Exception ignored) {
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        js("window.FT&&FT.recState(false)");
        if (rec.size() < 2) {
            Toast.makeText(this, "Zu wenige Punkte, nichts gespeichert", Toast.LENGTH_LONG).show();
            return;
        }
        List<double[]> copy = new ArrayList<>(rec);
        byte[] gpx = saveGpx(copy);
        if (gpx != null) sendToPage(gpx);
    }

    byte[] saveGpx(List<double[]> l) {
        byte[] bytes;
        String name = "flug-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".gpx";
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<gpx version=\"1.1\" creator=\"FlyTrack 3D\" xmlns=\"http://www.topografix.com/GPX/1/1\">"
                    + "<trk><name>Flug</name><trkseg>\n");
            for (double[] q : l) {
                sb.append(String.format(Locale.US,
                        "<trkpt lat=\"%.6f\" lon=\"%.6f\"><ele>%.1f</ele><time>%s</time></trkpt>\n",
                        q[1], q[2], q[3], f.format(new Date((long) (q[0] * 1000)))));
            }
            sb.append("</trkseg></trk></gpx>\n");
            bytes = sb.toString().getBytes("UTF-8");
        } catch (Exception e) {
            Toast.makeText(this, "GPX-Fehler: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        }
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
            cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            OutputStream o = getContentResolver().openOutputStream(u);
            o.write(bytes);
            o.close();
            Toast.makeText(this, "Gespeichert: Download/" + name, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Speichern fehlgeschlagen: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return bytes;
    }

    @Override
    protected void onDestroy() {
        try {
            if (lm != null) lm.removeUpdates(locL);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}

