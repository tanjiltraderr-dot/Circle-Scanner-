package com.example.billing

import android.app.Activity
import android.content.Context
import com.example.UtilityHubApp
import kotlinx.coroutines.flow.StateFlow

data class SubscriptionPlan(
    val id: String,
    val title: String,
    val price: String,
    val period: String,
    val badge: String? = null,
    val isBestValue: Boolean = false
)

object BillingManager {
    const val PRODUCT_ID_MONTHLY = "utilityhub_pro_monthly"
    const val PRODUCT_ID_YEARLY = "utilityhub_pro_yearly"
    const val PRODUCT_ID_LIFETIME = "utilityhub_pro_lifetime"

    val availablePlans = listOf(
        SubscriptionPlan(
            id = PRODUCT_ID_MONTHLY,
            title = "Monthly Pass",
            price = "$2.99",
            period = "/ month"
        ),
        SubscriptionPlan(
            id = PRODUCT_ID_YEARLY,
            title = "Annual Pass",
            price = "$19.99",
            period = "/ year",
            badge = "Save 45%",
            isBestValue = true
        ),
        SubscriptionPlan(
            id = PRODUCT_ID_LIFETIME,
            title = "Lifetime Access",
            price = "$39.99",
            period = "one-time",
            badge = "Unlimited"
        )
    )

    fun isProUser(): StateFlow<Boolean> {
        return UtilityHubApp.instance.repository.isProUnlocked
    }

    /**
     * Architecture hook for Google Play Billing Client purchase flow.
     * Can toggle pro status in development/testing sandbox.
     */
    fun launchPurchaseFlow(activity: Activity, plan: SubscriptionPlan, onComplete: (Boolean) -> Unit) {
        // Ready for BillingClient.launchBillingFlow
        UtilityHubApp.instance.repository.setProUnlocked(true)
        onComplete(true)
    }

    fun restorePurchases(activity: Activity, onComplete: (Boolean) -> Unit) {
        // Ready for BillingClient.queryPurchasesAsync
        onComplete(UtilityHubApp.instance.repository.isProUnlocked.value)
    }
}
