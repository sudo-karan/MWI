package com.ismartcoding.plain.features.sms

import android.content.Context
import android.telephony.SubscriptionManager
import com.ismartcoding.plain.platform.AndroidApp

/**
 * Lists active SIMs/subscriptions for multi-SIM send/call (spec §6 `sims`). Requires
 * READ_PHONE_STATE (and READ_PHONE_NUMBERS for the number); returns empty when not granted.
 */
object SimProvider {

    fun sims(): List<SimInfo> = runCatching {
        val sm = AndroidApp.context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        @Suppress("MissingPermission")
        val active = sm.activeSubscriptionInfoList ?: emptyList()
        active.map { info ->
            SimInfo(
                subId = info.subscriptionId,
                slotIndex = info.simSlotIndex,
                carrierName = info.carrierName?.toString() ?: "",
                displayName = info.displayName?.toString() ?: "",
                number = runCatching { info.number ?: "" }.getOrDefault(""),
            )
        }
    }.getOrDefault(emptyList())
}
