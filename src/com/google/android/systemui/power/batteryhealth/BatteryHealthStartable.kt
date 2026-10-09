/* SPDX-License-Identifier: Apache-2.0 */
package com.google.android.systemui.power.batteryhealth

import android.content.Context
import android.os.Process
import android.os.UserHandle
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import javax.inject.Inject

@SysUISingleton
class BatteryHealthStartable @Inject constructor(
    @Application private val context: Context,
) : CoreStartable {
    private val isSystemUser = Process.myUserHandle() == UserHandle.SYSTEM

    override fun start() {
        // Hardware is global; do not run competing restorers in secondary SystemUI processes.
        if (isSystemUser) ChargingPolicyController.getInstance(context).start()
    }

    override fun onBootCompleted() {
        if (isSystemUser) ChargingPolicyController.getInstance(context).restore()
    }
}
