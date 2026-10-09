/* SPDX-License-Identifier: Apache-2.0 */
package com.google.android.systemui.power.batteryhealth;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.Settings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
@LooperMode(LooperMode.Mode.PAUSED)
public class ChargingPolicyControllerTest {
    private Context context;
    private FakeController controller;
    private static final class FakeController extends ChargingPolicyController {
        final List<String> calls = new ArrayList<>();
        boolean fail, accept = true;
        FakeController(Context context) { super(context, new Handler(Looper.getMainLooper())); }
        @Override boolean applyToOwner(int policy, boolean restore) throws RemoteException {
            if (fail) throw new RemoteException("service not ready");
            calls.add(policy + ":" + restore);
            return accept;
        }
    }
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        controller = new FakeController(context);
    }
    @Test public void bootDelegatesDecisionToCanonicalOwnerEvenWithStaleUi() {
        Settings.Secure.putInt(context.getContentResolver(), ChargingPolicyController.MODE, 0);
        assertTrue(controller.restoreOnce());
        assertEquals(List.of("2:true"), controller.calls);
    }
    @Test public void explicitSwitchIsNotARestore() {
        assertTrue(controller.setChargingPolicy(1));
        assertTrue(controller.setChargingPolicy(2));
        assertEquals(List.of("1:false", "2:false"), controller.calls);
    }
    @Test public void serviceNotReadyRetries() {
        controller.fail = true;
        controller.restore();
        shadowOf(Looper.getMainLooper()).idle();
        controller.fail = false;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(List.of("2:true"), controller.calls);
    }
    @Test public void falseResultRetriesToo() {
        controller.accept = false;
        controller.restore();
        shadowOf(Looper.getMainLooper()).idle();
        controller.accept = true;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(List.of("2:true", "2:true"), controller.calls);
    }
    @Test public void bridgeNeverCommitsASecondOwnerSetting() {
        Settings.Secure.putInt(context.getContentResolver(), ChargingPolicyController.MODE, 0);
        assertTrue(controller.setChargingPolicy(2));
        assertEquals(0, Settings.Secure.getInt(context.getContentResolver(),
                ChargingPolicyController.MODE, -1));
    }
    @Test public void rejectedTransferIsReportedToGoogleUi() {
        controller.accept = false;
        assertFalse(controller.setChargingPolicy(2));
        controller.fail = true;
        assertFalse(controller.setChargingPolicy(1));
    }
    @Test public void invalidPoliciesNeverReachOwner() {
        assertFalse(controller.setChargingPolicy(0));
        assertFalse(controller.setChargingPolicy(3));
        assertTrue(controller.calls.isEmpty());
    }
}
