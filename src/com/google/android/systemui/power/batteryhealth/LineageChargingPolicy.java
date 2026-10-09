/* SPDX-License-Identifier: Apache-2.0 */
package com.google.android.systemui.power.batteryhealth;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

import lineageos.health.IHealthInterface;

/** Compile against the same generated AIDL interface as Lineage Health. */
final class LineageChargingPolicy {
    static boolean set(int policy, boolean restore) throws RemoteException {
        return call(ServiceManager.checkService("lineagehealth"), policy, restore);
    }

    static boolean call(IBinder remote, int policy, boolean restore) throws RemoteException {
        if (remote == null) throw new RemoteException("Charging owner service not ready");
        if (!IHealthInterface.DESCRIPTOR.equals(remote.getInterfaceDescriptor())) {
            throw new RemoteException("Unexpected charging owner service");
        }
        return IHealthInterface.Stub.asInterface(remote).setGoogleChargingPolicy(policy, restore);
    }
}
