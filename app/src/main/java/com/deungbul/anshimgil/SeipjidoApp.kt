package com.deungbul.anshimgil

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk

class SeipjidoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The key comes from local.properties through BuildConfig.
        // Without it the map screen shows a message instead of crashing.
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) {
            KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        }
    }
}
