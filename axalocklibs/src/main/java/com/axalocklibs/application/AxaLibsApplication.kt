package com.kolonishare.authentication.application

import android.content.Context
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.BuildConfig
import com.androidnetworking.interceptors.HttpLoggingInterceptor
import com.pixplicity.easyprefs.library.Prefs

/**
 * Application class
 */
class AxaLibsApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        try {
            AndroidNetworking.initialize(applicationContext)

            if (BuildConfig.DEBUG) {
                AndroidNetworking.enableLogging(HttpLoggingInterceptor.Level.BODY)
            }

            Prefs.Builder()
                    .setContext(this)
                    .setMode(MODE_PRIVATE)
                    .setPrefsName(packageName)
                    .setUseDefaultSharedPreference(true)
                    .build()

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        MultiDex.install(this)
    }
}