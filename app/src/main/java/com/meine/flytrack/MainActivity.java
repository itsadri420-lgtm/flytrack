package com.meine.flytrack;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    // Track
    int n = 0;
    double[] T;
    float[] X, Y, A, SP, VA;
    double lat0, lon0, mLon, mLat, t0, total, dist;
    float minA, maxA, maxSp, cX, cY;
    // Gelaende
    int gnx, gny;
    float gx0, gy0, gdx, gdy;
    float[] TH, sx, sy, sz;
    int[] CC;
    boolean hasReal = false;
    int terrainVer = 0;
    String status = "";
    // Ansicht
    float yaw = 0.6f, pitch = 0.5f, camDist = 5000, camDist0 = 5000;
    float[] exs = {1f, 1.5f, 2f, 3f, 5f};
    int ei = 2;
    int[] speeds = {1, 5, 20, 60, 200, 600};
    int spi = 2;
    String[] modes = {"Steigen", "Speed", "Höhe"};
    int colorMode = 0;
    boolean follow = false, playing = false, userSeek = false;
    double ct = 0;
    float dp;
    V v;
    SeekBar bar;
    Button bPlay, bSpeed, bMode, bView, bEx;

    static float cl(float x, float a, float b) {
        return x < a ? a : (x > b ? b : x);
    }

    int d(int x) {
        return (int) (x * dp);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(0xFF0B1022);
        getWindow().setNavigationBarColor(0xFF0B1022);
        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(0xFF0B1022);
        v = new V(this);
        root.addView(v);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(d(4), d(6), d(4), 0);
        addW(top, mk("Datei", x -> openFile()));
        addW(top, mk("Einfügen", x -> pasteText()));
        addW(top, mk("Demo", x -> loadDemo()));
        addW(top, mk("Gelände", x -> {
            if (n > 1) fetchTerrain();
        }));
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(d(4), 0, d(4), d(6));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        bPlay = mk("▶", x -> togglePlay());
        bSpeed = mk(speeds[spi] + "×", x -> {
            spi = (spi + 1) % speeds.length;
            bSpeed.setText(speeds[spi] + "×");
        });
        bMode = mk(modes[0], x -> {
            colorMode = (colorMode + 1) % 3;
            bMode.setText(modes[colorMode]);
        });
        bView = mk("Frei", x -> {
            follow = !follow;
            camDist = follow ? Math.min(camDist, 2500f) : camDist0;
            bView.setText(follow ? "Folgen" : "Frei");
        });
        bEx = mk("Höhe ×" + fmtEx(), x -> {
            ei = (ei + 1) % exs.length;
            bEx.setText("Höhe ×" + fmtEx());
        });
        addW(row, bPlay);
        addW(row, bSpeed);
        addW(row, bMode);
        addW(row, bView);
        addW(row, bEx);
        bottom.addView(row, new LinearLayout.LayoutParams(-1, -2));
        bar = new SeekBar(this);
        bar.setMax(1000);
        bar.setPadding(d(14), d(10), d(14), d(4));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int pr, boolean u) {
                if (u && n > 1) {
                    ct = total * pr / 1000.0;
                    v.invalidate();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                userSeek = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                userSeek = false;
            }
        });
        bottom.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);
    }

    String fmtEx() {
        float e = exs[ei];
        return e == (int) e ? String.valueOf((int) e) : String.valueOf(e);
    }

    Button mk(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(0xFFFFFFFF);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(d(4), d(9), d(4), d(9));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xCC101830);
        g.setCornerRadius(d(10));
        g.setStroke(d(1), 0xFF00E5FF);
        b.setBackground(g);
        b.setStateListAnimator(null);
        b.setOnClickListener(l);
        return b;
    }

    void addW(LinearLayout r, View b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(d(3), 0, d(3), 0);
        r.addView(b, p);
    }

    void togglePlay() {
        if (n < 2) return;
        if (!playing && ct >= total) ct = 0;
        playing = !playing;
        bPlay.setText(playing ? "❚❚" : "▶");
        v.invalidate();
    }

    // ---------- Dateien ----------
    void openFile() {
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
                final String s = new String(readAll(in), "ISO-8859-1");
                runOnUiThread(() -> loadText(s));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Fehler: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    void pasteText() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) {
            Toast.makeText(this, "Zwischenablage ist leer", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData.Item it = cm.getPrimaryClip().getItemAt(0);
        loadText(it.coerceToText(this).toString());
    }

    static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        if (is == null) return o.toByteArray();
        byte[] buf = new byte[16384];
        int k;
        while ((k = is.read(buf)) > 0) o.write(buf, 0, k);
        return o.toByteArray();
    }

    void loadText(String s) {
        List<double[]> l = s.contains("<trkpt") ? parseGpx(s) : parseIgc(s);
        if (l.size() < 2) {
            Toast.makeText(this, "Keine Trackpunkte gefunden (IGC oder GPX erwartet)", Toast.LENGTH_LONG).show();
            return;
        }
        setTrack(l);
    }

    List<double[]> parseIgc(String s) {
        List<double[]> l = new ArrayList<>();
        double off = 0, prev = -1;
        for (String line : s.split("\r?\n")) {
            if (line.length() < 35 || line.charAt(0) != 'B') continue;
            try {
                double t = Integer.parseInt(line.substring(1, 3)) * 3600.0
                        + Integer.parseInt(line.substring(3, 5)) * 60.0
                        + Integer.parseInt(line.substring(5, 7)) + off;
                if (prev >= 0 && t < prev) {
                    off += 86400;
                    t += 86400;
                }
                prev = t;
                double lat = Integer.parseInt(line.substring(7, 9))
                        + Integer.parseInt(line.substring(9, 14)) / 60000.0;
                if (line.charAt(14) == 'S') lat = -lat;
                double lon = Integer.parseInt(line.substring(15, 18))
                        + Integer.parseInt(line.substring(18, 23)) / 60000.0;
                if (line.charAt(23) == 'W') lon = -lon;
                int press = Integer.parseInt(line.substring(25, 30).trim());
                int gps = Integer.parseInt(line.substring(30, 35).trim());
                l.add(new double[]{t, lat, lon, gps != 0 ? gps : press});
            } catch (Exception ignored) {
            }
        }
        return l;
    }

    static String g(String s, String re) {
        Matcher m = Pattern.compile(re).matcher(s);
        return m.find() ? m.group(1) : null;
    }

    List<double[]> parseGpx(String s) {
        List<double[]> l = new ArrayList<>();
        Matcher m = Pattern.compile("<trkpt\\s([^>]*)>(.*?)</trkpt>", Pattern.DOTALL).matcher(s);
        while (m.find()) {
            try {
                String at = m.group(1), in = m.group(2);
                double lat = Double.parseDouble(g(at, "lat=\"([^\"]+)\""));
                double lon = Double.parseDouble(g(at, "lon=\"([^\"]+)\""));
                String e = g(in, "<ele>([^<]+)</ele>");
                String tm = g(in, "<time>([^<]+)</time>");
                double alt = e == null ? 0 : Double.parseDouble(e.trim());
                double t;
                if (tm != null && tm.length() >= 19) {
                    t = Integer.parseInt(tm.substring(8, 10)) * 86400.0
                            + Integer.parseInt(tm.substring(11, 13)) * 3600.0
                            + Integer.parseInt(tm.substring(14, 16)) * 60.0
                            + Integer.parseInt(tm.substring(17, 19));
                } else {
                    t = l.size();
                }
                l.add(new double[]{t, lat, lon, alt});
            } catch (Exception ignored) {
            }
        }
        return l;
    }

    void loadDemo() {
        List<double[]> l = new ArrayList<>();
        double cosl = Math.cos(Math.toRadians(47.2));
        for (int i = 0; i < 3000; i++) {
            double x = i * 7.0 + 220 * Math.cos(i * 0.045);
            double y = 260 * Math.sin(i * 0.045) + 900 * Math.sin(i * 0.0021);
            double alt = 2300 + 650 * Math.sin(i * 0.0026 - 1) + 120 * Math.sin(i * 0.045);
            l.add(new double[]{11 * 3600.0 + i, 47.2 + y / 110540.0, 11.3 + x / (111320 * cosl), alt});
        }
        setTrack(l);
    }

    // ---------- Track aufbauen ----------
    void setTrack(List<double[]> l) {
        int stride = Math.max(1, (l.size() + 5999) / 6000);
        int m = (l.size() + stride - 1) / stride;
        double[] ta = new double[m], la = new double[m], lo = new double[m];
        float[] al = new float[m];
        double minLa = 90, maxLa = -90, minLo = 180, maxLo = -180;
        for (int i = 0; i < m; i++) {
            double[] p = l.get(i * stride);
            ta[i] = p[0];
            la[i] = p[1];
            lo[i] = p[2];
            al[i] = (float) p[3];
            minLa = Math.min(minLa, p[1]);
            maxLa = Math.max(maxLa, p[1]);
            minLo = Math.min(minLo, p[2]);
            maxLo = Math.max(maxLo, p[2]);
        }
        lat0 = (minLa + maxLa) / 2;
        lon0 = (minLo + maxLo) / 2;
        mLat = 110540;
        mLon = 111320 * Math.cos(Math.toRadians(lat0));
        t0 = ta[0];
        T = new double[m];
        X = new float[m];
        Y = new float[m];
        A = al;
        SP = new float[m];
        VA = new float[m];
        float minX = 1e9f, maxX = -1e9f, minY = 1e9f, maxY = -1e9f;
        minA = 1e9f;
        maxA = -1e9f;
        for (int i = 0; i < m; i++) {
            T[i] = ta[i] - t0;
            X[i] = (float) ((lo[i] - lon0) * mLon);
            Y[i] = (float) ((la[i] - lat0) * mLat);
            minX = Math.min(minX, X[i]);
            maxX = Math.max(maxX, X[i]);
            minY = Math.min(minY, Y[i]);
            maxY = Math.max(maxY, Y[i]);
            minA = Math.min(minA, A[i]);
            maxA = Math.max(maxA, A[i]);
        }
        n = m;
        total = Math.max(1, T[m - 1]);
        cX = (minX + maxX) / 2;
        cY = (minY + maxY) / 2;
        maxSp = 0;
        dist = 0;
        for (int i = 0; i < m; i++) {
            int a = Math.max(0, i - 3), b = Math.min(m - 1, i + 3);
            double dt = T[b] - T[a];
            if (dt > 0) {
                double dx = X[b] - X[a], dy = Y[b] - Y[a];
                SP[i] = (float) (Math.sqrt(dx * dx + dy * dy) / dt * 3.6);
                VA[i] = (float) ((A[b] - A[a]) / dt);
            }
            maxSp = Math.max(maxSp, SP[i]);
            if (i > 0) {
                double dx = X[i] - X[i - 1], dy = Y[i] - Y[i - 1];
                dist += Math.sqrt(dx * dx + dy * dy);
            }
        }
        float wx = maxX - minX, wy = maxY - minY;
        float mg = Math.max(1500f, 0.15f * Math.max(wx, wy));
        buildGrid(minX - mg, minY - mg, wx + 2 * mg, wy + 2 * mg);
        camDist0 = Math.max(wx + 2 * mg, wy + 2 * mg) * 1.1f;
        camDist = camDist0;
        follow = false;
        bView.setText("Frei");
        playing = false;
        bPlay.setText("▶");
        ct = 0;
        bar.setProgress(0);
        fetchTerrain();
        v.invalidate();
    }

    // ---------- Gelaende ----------
    void buildGrid(float x0, float y0, float W, float H) {
        terrainVer++;
        hasReal = false;
        float cell = (float) Math.sqrt(W * H / 1500f);
        gnx = Math.max(8, Math.min(70, Math.round(W / cell)));
        gny = Math.max(8, Math.min(70, Math.round(H / cell)));
        gx0 = x0;
        gy0 = y0;
        gdx = W / gnx;
        gdy = H / gny;
        int vn = (gnx + 1) * (gny + 1);
        TH = new float[vn];
        Arrays.fill(TH, minA - 60);
        sx = new float[vn];
        sy = new float[vn];
        sz = new float[vn];
        CC = new int[gnx * gny];
        colorize();
    }

    void fetchTerrain() {
        final int ver = ++terrainVer;
        status = "Lade Höhendaten…";
        final int nx = gnx, ny = gny;
        final float x0 = gx0, y0 = gy0, dx = gdx, dy = gdy;
        new Thread(() -> {
            try {
                int vn = (nx + 1) * (ny + 1);
                float[] th = new float[vn];
                for (int s = 0; s < vn; s += 100) {
                    if (ver != terrainVer) return;
                    int e = Math.min(vn, s + 100);
                    StringBuilder la = new StringBuilder(), lo = new StringBuilder();
                    for (int k = s; k < e; k++) {
                        int i = k % (nx + 1), j = k / (nx + 1);
                        double lat = lat0 + (y0 + j * dy) / mLat;
                        double lon = lon0 + (x0 + i * dx) / mLon;
                        if (k > s) {
                            la.append(',');
                            lo.append(',');
                        }
                        la.append(String.format(Locale.US, "%.5f", lat));
                        lo.append(String.format(Locale.US, "%.5f", lon));
                    }
                    URL u = new URL("https://api.open-meteo.com/v1/elevation?latitude=" + la
                            + "&longitude=" + lo);
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(20000);
                    int rc = c.getResponseCode();
                    String body = new String(readAll(rc < 400 ? c.getInputStream() : c.getErrorStream()), "UTF-8");
                    if (rc >= 400) throw new Exception("HTTP " + rc);
                    JSONArray arr = new JSONObject(body).getJSONArray("elevation");
                    for (int k = s; k < e; k++) th[k] = (float) arr.getDouble(k - s);
                    final int pr = e * 100 / vn;
                    runOnUiThread(() -> {
                        if (ver == terrainVer) status = "Höhendaten " + pr + " %";
                    });
                }
                runOnUiThread(() -> {
                    if (ver != terrainVer) return;
                    TH = th;
                    hasReal = true;
                    colorize();
                    status = "";
                    Toast.makeText(this, "Gelände geladen", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                final String msg = String.valueOf(e.getMessage());
                runOnUiThread(() -> {
                    if (ver == terrainVer) status = "Gelände-Fehler: " + msg + " (Internet?)";
                });
            }
        }).start();
    }

    static final float[] HT = {0f, .3f, .55f, .8f, 1f};
    static final int[][] HC = {{70, 130, 70}, {150, 160, 80}, {150, 120, 85}, {165, 160, 155}, {250, 250, 250}};

    static int hyps(float t, float sh) {
        t = cl(t, 0f, 1f);
        int k = 0;
        while (k < 3 && t > HT[k + 1]) k++;
        float f = (t - HT[k]) / (HT[k + 1] - HT[k]);
        int r = (int) ((HC[k][0] + (HC[k + 1][0] - HC[k][0]) * f) * sh);
        int g = (int) ((HC[k][1] + (HC[k + 1][1] - HC[k][1]) * f) * sh);
        int b = (int) ((HC[k][2] + (HC[k + 1][2] - HC[k][2]) * f) * sh);
        return Color.rgb(Math.min(255, r), Math.min(255, g), Math.min(255, b));
    }

    void colorize() {
        float lo = 1e9f, hi = -1e9f;
        for (float h : TH) {
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
        }
        if (hi - lo < 1) hi = lo + 1;
        int st = gnx + 1;
        for (int j = 0; j < gny; j++) {
            for (int i = 0; i < gnx; i++) {
                int k = j * st + i;
                float h = (TH[k] + TH[k + 1] + TH[k + st] + TH[k + st + 1]) / 4f;
                float dhx = ((TH[k + 1] + TH[k + st + 1]) - (TH[k] + TH[k + st])) / (2 * gdx);
                float dhy = ((TH[k + st] + TH[k + st + 1]) - (TH[k] + TH[k + 1])) / (2 * gdy);
                float nz = 1f / (float) Math.sqrt(dhx * dhx + dhy * dhy + 1f);
                float dot = -dhx * nz * -0.45f + -dhy * nz * 0.35f + nz * 0.82f;
                float sh = cl(0.45f + 0.75f * dot, 0.35f, 1.15f);
                if (((i + j) & 1) == 0) sh *= 0.96f;
                CC[j * gnx + i] = hyps((h - lo) / (hi - lo), sh);
            }
        }
    }

    float terrainAt(float x, float y) {
        float fx = cl((x - gx0) / gdx, 0f, gnx - 0.001f), fy = cl((y - gy0) / gdy, 0f, gny - 0.001f);
        int i = (int) fx, j = (int) fy;
        float a = fx - i, b = fy - j;
        int k = j * (gnx + 1) + i, st = gnx + 1;
        float h0 = TH[k] + (TH[k + 1] - TH[k]) * a;
        float h1 = TH[k + st] + (TH[k + st + 1] - TH[k + st]) * a;
        return h0 + (h1 - h0) * b;
    }

    static int mix(int a, int b, float f) {
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f);
        int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f);
        return Color.rgb(r, g, bl);
    }

    static int ramp(float t) {
        t = cl(t, 0f, 1f);
        int blue = Color.rgb(60, 140, 255), yel = Color.rgb(255, 225, 70), red = Color.rgb(255, 70, 60);
        return t < .5f ? mix(blue, yel, t * 2) : mix(yel, red, (t - .5f) * 2);
    }

    static String fmt(double s) {
        int t = (int) s;
        return String.format(Locale.US, "%02d:%02d:%02d", t / 3600, (t / 60) % 60, t % 60);
    }

    // ---------- 3D-Ansicht ----------
    class V extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        Shader sky;
        long last = 0;
        float lx, ly, pd;
        int pc = 0;
        long[] keys = new long[0];
        float[] tsx = new float[0], tsy = new float[0], tz = new float[0];
        float[] gsx = new float[0], gsy = new float[0], gz = new float[0];
        float camx, camy, camz, fx, fy, fz, rx, ry, ux, uy, uz, cw, ch, foc, psx, psy;
        final int FOG = 0xFFB8D2EC;

        V(Context c) {
            super(c);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            sky = new LinearGradient(0, 0, 0, h,
                    new int[]{0xFF2B5C9E, 0xFF9CC4E8, 0xFFDCE9F5}, null, Shader.TileMode.CLAMP);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (e.getPointerCount() >= 2) {
                float dx = e.getX(0) - e.getX(1), dy = e.getY(0) - e.getY(1);
                float dd = (float) Math.sqrt(dx * dx + dy * dy);
                if (a == MotionEvent.ACTION_MOVE && pd > 0 && dd > 0) {
                    camDist = cl(camDist * pd / dd, 150f, camDist0 * 6f);
                }
                pd = dd;
                pc = 2;
                invalidate();
                return true;
            }
            pd = 0;
            if (a == MotionEvent.ACTION_DOWN) {
                lx = e.getX();
                ly = e.getY();
                pc = 1;
            } else if (a == MotionEvent.ACTION_MOVE) {
                if (pc != 1) {
                    pc = 1;
                    lx = e.getX();
                    ly = e.getY();
                } else {
                    yaw += (e.getX() - lx) * 0.006f;
                    pitch = cl(pitch + (e.getY() - ly) * 0.006f, 0.05f, 1.5f);
                    lx = e.getX();
                    ly = e.getY();
                }
            } else {
                pc = 0;
            }
            invalidate();
            return true;
        }

        float pj(float x, float y, float z) {
            float vx = x - camx, vy = y - camy, vz = z - camz;
            float zc = vx * fx + vy * fy + vz * fz;
            if (zc < 20f) return -1f;
            float xc = vx * rx + vy * ry;
            float yc = vx * ux + vy * uy + vz * uz;
            psx = cw / 2f + xc / zc * foc;
            psy = ch / 2f - yc / zc * foc;
            return zc;
        }

        @Override
        protected void onDraw(Canvas c) {
            cw = getWidth();
            ch = getHeight();
            long now = System.nanoTime();
            float dt = last == 0 ? 0 : (now - last) / 1e9f;
            last = now;
            if (dt > 0.1f) dt = 0.1f;
            p.setShader(sky);
            p.setStyle(Paint.Style.FILL);
            c.drawRect(0, 0, cw, ch, p);
            p.setShader(null);
            if (n < 2) {
                p.setColor(0xFF1B2A44);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(16 * dp);
                c.drawText("Öffne eine IGC- oder GPX-Datei", cw / 2, ch / 2 - 10 * dp, p);
                p.setTextSize(13 * dp);
                c.drawText("oder tippe auf \"Demo\"", cw / 2, ch / 2 + 14 * dp, p);
                return;
            }
            float ex = exs[ei];
            if (playing) {
                ct += dt * speeds[spi];
                if (ct >= total) {
                    ct = total;
                    playing = false;
                    bPlay.setText("▶");
                }
            }
            if (!userSeek) {
                int pr = (int) (ct / total * 1000);
                if (bar.getProgress() != pr) bar.setProgress(pr);
            }
            int ci = Arrays.binarySearch(T, ct);
            if (ci < 0) ci = -ci - 2;
            ci = Math.max(0, Math.min(n - 2, ci));
            double seg = T[ci + 1] - T[ci];
            float f = seg > 0 ? cl((float) ((ct - T[ci]) / seg), 0f, 1f) : 0f;
            float curX = X[ci] + (X[ci + 1] - X[ci]) * f;
            float curY = Y[ci] + (Y[ci + 1] - Y[ci]) * f;
            float curA = A[ci] + (A[ci + 1] - A[ci]) * f;
            int si = f < .5f ? ci : ci + 1;

            float tx, ty, tzz;
            if (follow) {
                tx = curX;
                ty = curY;
                tzz = curA * ex;
            } else {
                tx = cX;
                ty = cY;
                tzz = (minA + maxA) / 2f * ex;
            }
            float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
            camx = tx + camDist * cp * (float) Math.sin(yaw);
            camy = ty - camDist * cp * (float) Math.cos(yaw);
            camz = tzz + camDist * sp;
            fx = tx - camx;
            fy = ty - camy;
            fz = tzz - camz;
            float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
            fx /= fl;
            fy /= fl;
            fz /= fl;
            rx = fy;
            ry = -fx;
            float rl = (float) Math.sqrt(rx * rx + ry * ry);
            if (rl < 1e-6f) rl = 1f;
            rx /= rl;
            ry /= rl;
            ux = ry * fz;
            uy = -rx * fz;
            uz = rx * fy - ry * fx;
            foc = Math.max(cw, ch) * 0.85f;

            // Gelaende
            int st = gnx + 1;
            for (int j = 0; j <= gny; j++) {
                for (int i = 0; i <= gnx; i++) {
                    int k = j * st + i;
                    sz[k] = pj(gx0 + i * gdx, gy0 + j * gdy, TH[k] * ex);
                    sx[k] = psx;
                    sy[k] = psy;
                }
            }
            int cells = gnx * gny;
            if (keys.length < cells) keys = new long[cells];
            int cnt = 0;
            for (int j = 0; j < gny; j++) {
                for (int i = 0; i < gnx; i++) {
                    int k = j * st + i;
                    float z0 = sz[k], z1 = sz[k + 1], z2 = sz[k + st], z3 = sz[k + st + 1];
                    if (z0 < 0 || z1 < 0 || z2 < 0 || z3 < 0) continue;
                    float za = (z0 + z1 + z2 + z3) * .25f;
                    keys[cnt++] = ((long) (za * 4f) << 22) | (long) (j * gnx + i);
                }
            }
            Arrays.sort(keys, 0, cnt);
            p.setStyle(Paint.Style.FILL_AND_STROKE);
            p.setStrokeWidth(1.5f);
            for (int q = cnt - 1; q >= 0; q--) {
                int idx = (int) (keys[q] & 0x3FFFFF);
                float za = (keys[q] >> 22) / 4f;
                int j = idx / gnx, i = idx % gnx, k = j * st + i;
                path.reset();
                path.moveTo(sx[k], sy[k]);
                path.lineTo(sx[k + 1], sy[k + 1]);
                path.lineTo(sx[k + st + 1], sy[k + st + 1]);
                path.lineTo(sx[k + st], sy[k + st]);
                path.close();
                float fog = cl((za - camDist * 0.6f) / (camDist * 6f), 0f, 0.75f);
                p.setColor(mix(CC[idx], FOG, fog));
                c.drawPath(path, p);
            }

            // Track
            if (tsx.length < n) {
                tsx = new float[n];
                tsy = new float[n];
                tz = new float[n];
                gsx = new float[n];
                gsy = new float[n];
                gz = new float[n];
            }
            for (int i = 0; i < n; i++) {
                tz[i] = pj(X[i], Y[i], A[i] * ex);
                tsx[i] = psx;
                tsy[i] = psy;
                gz[i] = pj(X[i], Y[i], terrainAt(X[i], Y[i]) * ex + 2f);
                gsx[i] = psx;
                gsy[i] = psy;
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(2 * dp);
            p.setColor(0x66000000);
            for (int i = 0; i < n - 1; i++) {
                if (gz[i] > 0 && gz[i + 1] > 0) c.drawLine(gsx[i], gsy[i], gsx[i + 1], gsy[i + 1], p);
            }
            p.setStrokeWidth(1f * dp);
            p.setColor(0x33FFFFFF);
            int step = Math.max(1, n / 90);
            for (int i = 0; i < n; i += step) {
                if (tz[i] > 0 && gz[i] > 0) c.drawLine(tsx[i], tsy[i], gsx[i], gsy[i], p);
            }
            float rng = Math.max(1f, maxA - minA);
            for (int i = 0; i < n - 1; i++) {
                if (tz[i] < 0 || tz[i + 1] < 0) continue;
                if (i <= ci) {
                    float t;
                    if (colorMode == 0) t = (VA[i] + 4f) / 8f;
                    else if (colorMode == 1) t = SP[i] / Math.max(1f, maxSp);
                    else t = (A[i] - minA) / rng;
                    p.setColor(ramp(t));
                    p.setStrokeWidth(3.5f * dp);
                } else {
                    p.setColor(0x99FFFFFF);
                    p.setStrokeWidth(1.5f * dp);
                }
                c.drawLine(tsx[i], tsy[i], tsx[i + 1], tsy[i + 1], p);
            }
            // Pilot
            float zt = pj(curX, curY, curA * ex);
            float mx = psx, my = psy;
            float gnd = terrainAt(curX, curY);
            float zg = pj(curX, curY, gnd * ex + 2f);
            float gx = psx, gy = psy;
            if (zt > 0 && zg > 0) {
                p.setColor(0xCCFFFFFF);
                p.setStrokeWidth(2 * dp);
                c.drawLine(mx, my, gx, gy, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x88000000);
                c.drawCircle(gx, gy, 4 * dp, p);
            }
            if (zt > 0) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(mx, my, 7 * dp, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.5f * dp);
                p.setColor(0xFFFF3D3D);
                c.drawCircle(mx, my, 7 * dp, p);
            }

            // Anzeige
            float pw = Math.min(cw - 16 * dp, 310 * dp);
            float top = 52 * dp;
            boolean hasSt = !status.isEmpty();
            float ph = (hasSt ? 98 : 80) * dp;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xAA0B1022);
            c.drawRoundRect(8 * dp, top, 8 * dp + pw, top + ph, 12 * dp, 12 * dp, p);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(12.5f * dp);
            p.setColor(0xFFFFFFFF);
            float ty0 = top + 19 * dp, lh = 17 * dp;
            c.drawText("Höhe " + (int) curA + " m  ·  über Grund "
                    + (hasReal ? (int) (curA - gnd) + " m" : "–"), 18 * dp, ty0, p);
            c.drawText("Speed " + (int) SP[si] + " km/h  ·  Steigen "
                    + String.format(Locale.US, "%+.1f", VA[si]) + " m/s", 18 * dp, ty0 + lh, p);
            c.drawText(fmt(ct) + " / " + fmt(total) + "  ·  Uhr " + fmt((t0 + ct) % 86400), 18 * dp, ty0 + 2 * lh, p);
            p.setColor(0xFFA9C4FF);
            c.drawText("Max " + (int) maxA + " m · " + String.format(Locale.US, "%.1f", dist / 1000)
                    + " km · Vmax " + (int) maxSp + " km/h", 18 * dp, ty0 + 3 * lh, p);
            if (hasSt) {
                p.setColor(0xFFFFE066);
                c.drawText(status, 18 * dp, ty0 + 4 * lh, p);
            }
            postInvalidateOnAnimation();
        }
    }
}
