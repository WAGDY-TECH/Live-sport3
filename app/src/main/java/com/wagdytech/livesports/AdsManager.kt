package com.wagdytech.livesports

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdsManager {

    private const val MIN_INTERVAL_MS = 60 * 1000L
    private const val SHOW_FROM_OPEN = 2
    private const val RETRY_DELAY_MS = 15_000L

    private var shownOpens = 0
    private var lastShownAt = 0L
    private var failures = 0
    private var loading = false
    private var initialized = false
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var interstitial: InterstitialAd? = null
    private var pendingActivity: Activity? = null

    fun init(activity: Activity) {
        pendingActivity = activity
        if (!initialized) {
            initialized = true
            MobileAds.initialize(activity) { activity.let(::load) }
        }
        load(activity)
    }

    fun load(activity: Activity) {
        pendingActivity = activity
        if (interstitial != null || loading) return
        loading = true
        InterstitialAd.load(
            activity,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    failures = 0
                    loading = false
                    interstitial = ad
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
                    loading = false
                    interstitial = null
                    failures++
                    handler.postDelayed({ pendingActivity?.let { if (!it.isFinishing) load(it) } }, RETRY_DELAY_MS)
                }
            }
        )
    }

    fun maybeShowBeforeOpen(activity: Activity, onDone: () -> Unit) {
        shownOpens++
        val now = System.currentTimeMillis()
        val ready = interstitial
        val allowed = shownOpens >= SHOW_FROM_OPEN && now - lastShownAt >= MIN_INTERVAL_MS
        if (allowed && ready != null) {
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
