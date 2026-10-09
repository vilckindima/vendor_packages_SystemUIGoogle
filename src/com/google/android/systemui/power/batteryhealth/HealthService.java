/* SPDX-License-Identifier: Apache-2.0 */
package com.google.android.systemui.power.batteryhealth;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import java.util.Set;

/** Google battery settings bridge for the caimito vendor stack; not a stock SystemUI port. */
public final class HealthService extends Service implements HealthServiceBinder.Backend {
    private static final String TAG = "EvoBatteryHealth";
    private static final String PULSAR_SETTING = "pulsar_sysprop_enabled";
    private static final Set<String> CALLERS = Set.of(
            "com.android.settings", "com.android.systemui",
            "com.google.android.settings.intelligence");
    private final HealthServiceBinder mBinder =
            new HealthServiceBinder(this, this::enforceCaller);

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void enforceCaller() {
        int uid = Binder.getCallingUid();
        PackageManager pm = getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        if (packages != null) {
            for (String name : packages) {
                if (!CALLERS.contains(name)) continue;
                try {
                    ApplicationInfo app = pm.getApplicationInfoAsUser(name, 0,
                            UserHandle.getUserId(uid));
                    if (app.uid == uid && (app.flags & (ApplicationInfo.FLAG_SYSTEM
                            | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
                        return;
                    }
                } catch (PackageManager.NameNotFoundException ignored) {
                    // Package changed since getPackagesForUid(). Deny the request.
                }
            }
        }
        throw new SecurityException("Unsupported battery-health caller UID " + uid);
    }

    @Override
    public int[] getHealthData() {
        try {
            return GoogleBattery.connect().getHealthData();
        } catch (RemoteException | RuntimeException e) {
            Log.w(TAG, "Cannot read battery health", e);
            // Stock HealthData uses -1 for unavailable values; never invent good health.
            return new int[] {-1, -1, -1, -1};
        }
    }

    @Override
    public boolean setChargingPolicy(int policy) {
        return ChargingPolicyController.getInstance(this).setChargingPolicy(policy);
    }

    @Override
    // Preference only: no Pulsar charge control exists on this ROM. A vendor property
    // would need platform_app to set vendor props, which Treble policy forbids.
    public boolean isPulsarEnabled() {
        return Settings.Secure.getIntForUser(getContentResolver(), PULSAR_SETTING, 1,
                ActivityManager.getCurrentUser()) != 0;
    }

    @Override
    public synchronized void setPulsarEnabled(boolean enabled) {
        if (!Settings.Secure.putIntForUser(getContentResolver(), PULSAR_SETTING,
                enabled ? 1 : 0, ActivityManager.getCurrentUser())) {
            // Use an exception type that Parcel can marshal back to the client.
            throw new IllegalStateException("Cannot save Pulsar preference");
        }
        Log.i(TAG, "Pulsar preference saved: " + enabled
                + "; charging-control implementation is not included");
    }
}
