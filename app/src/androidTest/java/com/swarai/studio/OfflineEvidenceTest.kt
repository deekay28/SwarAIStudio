package com.swarai.studio

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The CI emulator job turns Wi-Fi and mobile data off before running the suite and passes expectOffline=true,
 * so every other test in the run is evidence that the offline studio works without internet.
 */
@RunWith(AndroidJUnit4::class)
class OfflineEvidenceTest {
    @Test fun reportsConnectivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        Log.i("SwarAIOffline", "internet capability present = $online")
        val expectOffline = InstrumentationRegistry.getArguments().getString("expectOffline") == "true"
        if (expectOffline) assertFalse("Device unexpectedly has internet during the offline run", online)
    }
}
