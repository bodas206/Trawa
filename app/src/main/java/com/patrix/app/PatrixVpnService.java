package com.patrix.app;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

public class PatrixVpnService extends VpnService {
    private ParcelFileDescriptor tunnel;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Builder builder = new Builder()
                .setSession("Patrix")
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0);
            tunnel = builder.establish();
        } catch (Exception ignored) { }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        try { if (tunnel != null) tunnel.close(); } catch (Exception ignored) { }
        tunnel = null;
        super.onDestroy();
    }
}
