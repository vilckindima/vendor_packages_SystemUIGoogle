/* SPDX-License-Identifier: Apache-2.0 */
package com.google.android.systemui.power.batteryhealth;

import static org.junit.Assert.*;

import android.os.BadParcelableException;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
public class BatteryWireTest {
    private static final class Backend implements HealthServiceBinder.Backend {
        int policy;
        int calls;
        boolean enabled = true;
        boolean success = true;
        public int[] getHealthData() { return new int[] {91, 82, 73, 2}; }
        public boolean setChargingPolicy(int value) {
            calls++;
            policy = value;
            return success;
        }
        public boolean isPulsarEnabled() { return enabled; }
        public void setPulsarEnabled(boolean value) { calls++; enabled = value; }
    }

    private final Backend backend = new Backend();
    private final HealthServiceBinder service = new HealthServiceBinder(backend, () -> {});

    private Parcel request() {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(HealthServiceBinder.DESCRIPTOR);
        return data;
    }

    private Parcel transact(int code, Parcel data) throws Exception {
        Parcel reply = Parcel.obtain();
        data.setDataPosition(0);
        assertTrue(service.onTransact(code, data, reply, 0));
        reply.setDataPosition(0);
        reply.readException();
        return reply;
    }

    @Test public void chargeLimitUsesTransactionSixAndBooleanReply() throws Exception {
        Parcel data = request();
        data.writeInt(2);
        Parcel reply = transact(6, data);
        assertTrue(reply.readBoolean());
        assertEquals(2, backend.policy);
        assertEquals(0, reply.dataAvail());
        data.recycle(); reply.recycle();
    }

    @Test public void failedPolicyIsNotAcknowledged() throws Exception {
        backend.success = false;
        Parcel data = request(); data.writeInt(1);
        Parcel reply = transact(6, data);
        assertFalse(reply.readBoolean());
        data.recycle(); reply.recycle();
    }

    @Test public void invalidPolicyDoesNotReachHardware() throws Exception {
        Parcel data = request(); data.writeInt(999);
        Parcel reply = transact(6, data);
        assertFalse(reply.readBoolean());
        assertEquals(0, backend.calls);
        data.recycle(); reply.recycle();
    }

    @Test public void pulsarWriteAndReadUseEightAndSeven() throws Exception {
        Parcel write = request(); write.writeBoolean(false);
        Parcel written = transact(8, write);
        assertEquals(0, written.dataAvail());
        Parcel read = request();
        Parcel result = transact(7, read);
        assertFalse(result.readBoolean());
        write.recycle(); written.recycle(); read.recycle(); result.recycle();
    }

    @Test public void healthDataMatchesClientParcelableOrder() throws Exception {
        Parcel data = request();
        Parcel reply = transact(1, data);
        assertEquals(1, reply.readInt());
        assertArrayEquals(new int[] {91, 82, 73, 2}, new int[] {
                reply.readInt(), reply.readInt(), reply.readInt(), reply.readInt()});
        assertEquals(0, reply.dataAvail());
        data.recycle(); reply.recycle();
    }

    @Test public void trailingDataRejectedBeforeMutation() {
        Parcel data = request(); data.writeInt(2); data.writeInt(3);
        data.setDataPosition(0);
        Parcel reply = Parcel.obtain();
        assertThrows(BadParcelableException.class,
                () -> service.onTransact(6, data, reply, 0));
        assertEquals(0, backend.calls);
        data.recycle(); reply.recycle();
    }

    @Test public void rejectedCallerCannotChangeBatteryPolicy() {
        HealthServiceBinder denied = new HealthServiceBinder(backend,
                () -> { throw new SecurityException("Denied"); });
        Parcel data = request(); data.writeInt(2); data.setDataPosition(0);
        Parcel reply = Parcel.obtain();
        assertThrows(SecurityException.class, () -> denied.onTransact(6, data, reply, 0));
        assertEquals(0, backend.calls);
        data.recycle(); reply.recycle();
    }

    @Test public void transactionNineIsNotAliasedToPulsar() throws Exception {
        Parcel data = request(); data.writeBoolean(false); data.setDataPosition(0);
        Parcel reply = Parcel.obtain();
        assertFalse(service.onTransact(9, data, reply, 0));
        assertEquals(0, backend.calls);
        data.recycle(); reply.recycle();
    }

    @Test public void onewayMutationRejected() throws Exception {
        Parcel data = request(); data.writeInt(2); data.setDataPosition(0);
        assertFalse(service.onTransact(6, data, null, IBinder.FLAG_ONEWAY));
        assertEquals(0, backend.calls);
        data.recycle();
    }

    private static final class Hal extends Binder {
        int version = 5;
        int policy;
        boolean supportsPolicy = true;
        Hal() { attachInterface(null, GoogleBattery.DESCRIPTOR); }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            data.enforceInterface(GoogleBattery.DESCRIPTOR);
            if (code == 23) {
                if (!supportsPolicy) return false;
                policy = data.readInt();
                data.enforceNoDataAvail();
                reply.writeNoException();
                return true;
            }
            data.enforceNoDataAvail();
            reply.writeNoException();
            if (code == 16777215) reply.writeInt(version);
            else if (code == 15) reply.writeInt(91);
            else if (code == 14) reply.writeInt(82);
            else if (code == 13) reply.writeInt(73);
            else if (code == 16) reply.writeInt(2);
            else throw new AssertionError("Unexpected HAL transaction " + code);
            return true;
        }
    }

    @Test public void policyForwardedToHalTransactionTwentyThree() throws Exception {
        Hal hal = new Hal();
        new GoogleBattery(hal).setChargingPolicy(2);
        assertEquals(2, hal.policy);
    }

    @Test public void halHealthFieldsUseVerifiedTransactions() throws Exception {
        assertArrayEquals(new int[] {91, 82, 73, 2},
                new GoogleBattery(new Hal()).getHealthData());
    }

    @Test public void unavailableHalFails() {
        assertThrows(RemoteException.class, () -> new GoogleBattery(null));
    }

    @Test public void olderHalFailsClosed() {
        Hal hal = new Hal(); hal.version = 4;
        assertThrows(RemoteException.class, () -> new GoogleBattery(hal));
    }

    @Test public void unsupportedHalTransactionFails() throws Exception {
        Hal hal = new Hal(); hal.supportsPolicy = false;
        GoogleBattery battery = new GoogleBattery(hal);
        assertThrows(RemoteException.class, () -> battery.setChargingPolicy(2));
    }
}
