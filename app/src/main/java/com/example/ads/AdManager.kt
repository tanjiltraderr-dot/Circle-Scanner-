package com.example.ads

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AdManager {
    // Official Google AdMob Test Ad Unit IDs
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    var areAdsEnabled: Boolean = true

    // Policy safety: Camera, sensitive forms, and document scanning are ad-free
    fun shouldShowBanner(screenRoute: String, isProUser: Boolean): Boolean {
        if (!areAdsEnabled || isProUser) return false
        val adFreeRoutes = listOf(
            "tool_qr_scanner",
            "tool_barcode_scanner",
            "tool_doc_scanner",
            "tool_password_gen",
            "pro"
        )
        return !adFreeRoutes.any { screenRoute.startsWith(it) }
    }

    private var actionCount = 0
    fun onUserActionCompleted(context: Context, isProUser: Boolean, onAdDismissed: () -> Unit = {}) {
        if (!areAdsEnabled || isProUser) {
            onAdDismissed()
            return
        }
        actionCount++
        // Show interstitial strictly capped at every 5th completed major utility action
        if (actionCount % 5 == 0) {
            // Simulated interstitial trigger ready for Google Mobile Ads SDK load/show
            onAdDismissed()
        } else {
            onAdDismissed()
        }
    }
}

@Composable
fun AppBannerAd(
    modifier: Modifier = Modifier,
    isProUser: Boolean = false
) {
    if (isProUser || !AdManager.areAdsEnabled) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 8.dp, horizontal = 12.dp)
            .testTag("app_banner_ad"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Ad · Upgrade to Pro for ad-free experience",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
