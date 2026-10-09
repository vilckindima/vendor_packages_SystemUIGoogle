/* SPDX-License-Identifier: Apache-2.0 */
package com.google.android.systemui.power.batteryhealth;

import static org.junit.Assert.*;
import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;
import lineageos.health.IHealthInterface;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
public class LineageChargingPolicyTest {
    static class Endpoint extends IHealthInterface.Stub {
        boolean accepted = true;
        int policy; boolean restore;
        @Override public boolean setGoogleChargingPolicy(int p, boolean r) {
            policy=p; restore=r; return accepted;
        }
        // Any accidental call to a different generated method must fail this test.
        private AssertionError wrongMethod() { return new AssertionError("Wrong health method"); }
        @Override public boolean isChargingControlSupported() { throw wrongMethod(); }
        @Override public boolean getChargingControlEnabled() { throw wrongMethod(); }
        @Override public boolean setChargingControlEnabled(boolean v) { throw wrongMethod(); }
        @Override public int getChargingControlMode() { throw wrongMethod(); }
        @Override public boolean setChargingControlMode(int v) { throw wrongMethod(); }
        @Override public int getChargingControlStartTime() { throw wrongMethod(); }
        @Override public boolean setChargingControlStartTime(int v) { throw wrongMethod(); }
        @Override public int getChargingControlTargetTime() { throw wrongMethod(); }
        @Override public boolean setChargingControlTargetTime(int v) { throw wrongMethod(); }
        @Override public int getChargingControlLimit() { throw wrongMethod(); }
        @Override public boolean setChargingControlLimit(int v) { throw wrongMethod(); }
        @Override public boolean resetChargingControl() { throw wrongMethod(); }
        @Override public boolean allowFineGrainedSettings() { throw wrongMethod(); }
        @Override public boolean isFastChargeSupported() { throw wrongMethod(); }
        @Override public int[] getSupportedFastChargeModes() { throw wrongMethod(); }
        @Override public int getFastChargeMode() { throw wrongMethod(); }
        @Override public boolean setFastChargeMode(int v) { throw wrongMethod(); }
    }
    static class RemoteEndpoint extends Binder {
        final Endpoint endpoint = new Endpoint();
        boolean fail;
        RemoteEndpoint() { attachInterface(null, IHealthInterface.DESCRIPTOR); }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            // queryLocalInterface returns null: exercise the generated Proxy and Stub.
            if (fail) throw new RemoteException("Service disconnected");
            return endpoint.onTransact(code, data, reply, flags);
        }
    }
    @Test public void generatedProxyCallsOnlyTheRequestedMethod() throws Exception {
        RemoteEndpoint e = new RemoteEndpoint();
        assertTrue(LineageChargingPolicy.call(e, 2, true));
        assertEquals(2, e.endpoint.policy); assertTrue(e.endpoint.restore);
        e.endpoint.accepted = false;
        assertFalse(LineageChargingPolicy.call(e, 1, false));
        assertEquals(1, e.endpoint.policy); assertFalse(e.endpoint.restore);
    }
    @Test public void binderFailureDoesNotFallBackToDirectHal() {
        RemoteEndpoint e = new RemoteEndpoint(); e.fail = true;
        assertThrows(RemoteException.class, () -> LineageChargingPolicy.call(e, 2, false));
    }
    @Test public void rejectsWrongOrMissingService() {
        Binder e = new Binder(); e.attachInterface(null, "wrong");
        assertThrows(RemoteException.class, () -> LineageChargingPolicy.call(e, 2, true));
        assertThrows(RemoteException.class, () -> LineageChargingPolicy.call(null, 2, true));
    }
}
