/* SPDX-License-Identifier: Apache-2.0 */
package com.google.android.systemui.power.batteryhealth;

import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;

/** Wire-compatible subset used by the inspected Settings Intelligence battery pages. */
final class HealthServiceBinder extends Binder {
    static final String DESCRIPTOR =
            "com.google.android.systemui.power.batteryhealth.IHealthService";

    interface Backend {
        int[] getHealthData();
        boolean setChargingPolicy(int policy);
        boolean isPulsarEnabled();
        void setPulsarEnabled(boolean enabled);
    }

    private final Backend mBackend;
    private final Runnable mCheckCaller;

    HealthServiceBinder(Backend backend, Runnable checkCaller) {
        mBackend = backend;
        mCheckCaller = checkCaller;
        attachInterface(null, DESCRIPTOR);
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code == INTERFACE_TRANSACTION) {
            reply.writeString(DESCRIPTOR);
            return true;
        }
        if (code < FIRST_CALL_TRANSACTION || code > LAST_CALL_TRANSACTION) {
            return super.onTransact(code, data, reply, flags);
        }
        data.enforceInterface(DESCRIPTOR);
        // Validate the original caller before clearing identity for HAL/settings access.
        mCheckCaller.run();
        if (code != 1 && code != 6 && code != 7 && code != 8) {
            // Do not pretend to implement listeners, charger diagnostics or newer controls.
            return false;
        }
        if ((flags & FLAG_ONEWAY) != 0 || reply == null) {
            return false;
        }
        long identity = Binder.clearCallingIdentity();
        try {
            switch (code) {
                case 1:
                    data.enforceNoDataAvail();
                    int[] health = mBackend.getHealthData();
                    reply.writeNoException();
                    // HealthData is an unstructured Parcelable: presence, then four ints.
                    reply.writeInt(1);
                    for (int value : health) reply.writeInt(value);
                    return true;
                case 6:
                    int policy = data.readInt();
                    data.enforceNoDataAvail();
                    boolean success = (policy == 1 || policy == 2)
                            && mBackend.setChargingPolicy(policy);
                    reply.writeNoException();
                    reply.writeBoolean(success);
                    return true;
                case 7:
                    data.enforceNoDataAvail();
                    boolean enabled = mBackend.isPulsarEnabled();
                    reply.writeNoException();
                    reply.writeBoolean(enabled);
                    return true;
                case 8:
                    boolean requested = data.readBoolean();
                    data.enforceNoDataAvail();
                    mBackend.setPulsarEnabled(requested);
                    reply.writeNoException();
                    return true;
                default:
                    return false;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }
}
