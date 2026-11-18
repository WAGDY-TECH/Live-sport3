package com.wagdytech.livesports

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdsManager {

    private const val MIN_INTERVAL_MS = 3 * 60 * 1000L
    private var shownOpens = 0
    private var lastShownAt = 0L
    private var loading = false

    @Volatile
    private var interstitial: InterstitialAd? = null

    fun load(activity: Activity) {
        if (interstitial != null || loading) return
        loading = true
        InterstitialAd.load(
            activity,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    loading = false
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitial = null
                            load(activity)
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            interstitial = null
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    loading = false
                }
            }
        )
    }

    fun maybeShowBeforeOpen(activity: Activity, onDone: () -> Unit) {
        shownOpens++
        val now = System.currentTimeMillis()
        val ready = interstitial
        if (shownOpens > 2 && ready != null && now - lastShownAt >= MIN_INTERVAL_MS) {
            lastShownAt = now
            ready.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitial = null
                    load(activity)
                    onDone()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitial = null
                    onDone()
                }
            }
            ready.show(activity)
        } else {
            load(activity)
            onDone()
        }
    }
}
