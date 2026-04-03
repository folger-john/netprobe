package com.netprobe.app

import android.app.Application
import com.google.android.gms.ads.MobileAds

class NetProbeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)
    }
}
