/* SPDX-License-Identifier: Apache-2.0 */
package com.google.android.systemui.power.batteryhealth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

/** Boot retries and Google UI requests; Lineage Health owns all policy transitions. */
public class ChargingPolicyController {
    static final String MODE = "charge_optimization_mode";
    private static final String TAG = "EvoBatteryHealth";
    private static ChargingPolicyController sInstance;
    private final Context mContext;
    private final Handler mHandler;
    private boolean mStarted;
    private long mRetryDelay = 1000;
    private final Runnable mRestore = this::restoreWithRetry;

    public static synchronized ChargingPolicyController getInstance(Context context) {
        if (sInstance == null) {
            HandlerThread thread = new HandlerThread("GoogleBatteryPolicy");
            thread.start();
            sInstance = new ChargingPolicyController(context.getApplicationContext(),
                    new Handler(thread.getLooper()));
        }
        return sInstance;
    }

    ChargingPolicyController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    public synchronized void start() {
        if (mStarted) return;
        mStarted = true;
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(MODE), false, new ContentObserver(mHandler) {
                    @Override public void onChange(boolean selfChange) { restore(); }
                }, UserHandle.USER_ALL);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        filter.addAction(Intent.ACTION_USER_UNLOCKED);
        // These are protected system broadcasts; handle them on the worker thread.
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { restore(); }
        }, filter, null, mHandler, Context.RECEIVER_EXPORTED);
        restore();
    }

    /** Called at SystemUI start and again when locked boot completes. Never blocks main. */
    public synchronized void restore() {
        mRetryDelay = 1000;
        mHandler.removeCallbacks(mRestore);
        mHandler.post(mRestore);
    }

    private synchronized void restoreWithRetry() {
        if (restoreOnce()) return;
        // HAL/provider startup can lag behind SystemUI. Re-read settings on every retry.
        mHandler.removeCallbacks(mRestore);
        mHandler.postDelayed(mRestore, mRetryDelay);
        mRetryDelay = Math.min(mRetryDelay * 2, 30000);
    }

    synchronized boolean restoreOnce() {
        try {
            // The service rechecks the persisted owner while holding the same lock used
            // for Lineage requests. A stale UI value must never grant ownership.
            return applyToOwner(2, true);
        } catch (RemoteException | RuntimeException e) {
            Log.w(TAG, "Charging policy restore failed; will retry", e);
            return false;
        }
    }

    /** Called from the Binder thread, under the same lock as boot/observer restoration. */
    synchronized boolean setChargingPolicy(int policy) {
        if (policy != 1 && policy != 2) return false;
        try {
            if (!applyToOwner(policy, false)) return false;
            mHandler.removeCallbacks(mRestore);
            return true;
        } catch (RemoteException | RuntimeException e) {
            Log.e(TAG, "Cannot apply/save charging policy " + policy, e);
            return false;
        }
    }

    boolean applyToOwner(int policy, boolean restore) throws RemoteException {
        return LineageChargingPolicy.set(policy, restore);
    }
}
