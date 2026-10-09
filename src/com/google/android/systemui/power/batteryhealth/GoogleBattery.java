/* SPDX-License-Identifier: Apache-2.0 */
package com.google.android.systemui.power.batteryhealth;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;

/** Small adapter for the caimito Google Battery stable AIDL wire interface. */
final class GoogleBattery {
    static final String DESCRIPTOR = "vendor.google.google_battery.IGoogleBattery";
    private static final int GET_VERSION = 16777215;
    private static final int GET_CAPACITY_INDEX = 13;
    private static final int GET_IMPEDANCE_INDEX = 14;
    private static final int GET_HEALTH_INDEX = 15;
    private static final int GET_HEALTH_STATUS = 16;
    private static final int SET_CHARGING_POLICY = 23;
    private final IBinder mRemote;

    GoogleBattery(IBinder remote) throws RemoteException {
        if (remote == null || !DESCRIPTOR.equals(remote.getInterfaceDescriptor())) {
            throw new RemoteException("Google Battery HAL unavailable");
        }
        mRemote = remote;
        // The inspected komodo vendor stack ships V5. Fail closed on older stacks.
        if (readInt(GET_VERSION) < 5) {
            throw new RemoteException("Google Battery HAL V5 or later required");
        }
    }

    static GoogleBattery connect() throws RemoteException {
        // Non-blocking lookup; reconnect on every request, including after HAL death.
        return new GoogleBattery(ServiceManager.checkService(DESCRIPTOR + "/default"));
    }

    void setChargingPolicy(int policy) throws RemoteException {
        if (policy != 1 && policy != 2) {
            throw new IllegalArgumentException("Unsupported charging policy: " + policy);
        }
        Parcel data = Parcel.obtain(mRemote);
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(policy);
            transact(SET_CHARGING_POLICY, data, reply);
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    int[] getHealthData() throws RemoteException {
        return new int[] {readInt(GET_HEALTH_INDEX), readInt(GET_IMPEDANCE_INDEX),
                readInt(GET_CAPACITY_INDEX), readInt(GET_HEALTH_STATUS)};
    }

    private int readInt(int code) throws RemoteException {
        Parcel data = Parcel.obtain(mRemote);
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            transact(code, data, reply);
            return reply.readInt();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private void transact(int code, Parcel data, Parcel reply) throws RemoteException {
        if (!mRemote.transact(code, data, reply, 0)) {
            throw new RemoteException("Unsupported Google Battery transaction: " + code);
        }
        reply.readException();
    }
}
