package com.axalocklibs.webservice

import android.app.Activity
import android.provider.SyncStateContract
import android.util.Log
import com.androidnetworking.common.Priority
import com.axalocklibs.`interface`.IAPIResponse
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.pixplicity.easyprefs.library.Prefs
import com.rx2androidnetworking.Rx2AndroidNetworking
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.internal.Util
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

object ApiRequest {
    private var response = ""
    fun callPOSTAPI(
        activity: Activity?,
        url: String,
        params: HashMap<String, Any>,
        tag: String,
        apiResponse: IAPIResponse,
        appVersion: String,
        authorizationHeader: String,
        bearerAuthorization: String
    ) {
        Log.e("WS_PARAM_", "$tag  : $params")
        initializeSSLContext(activity)
        val okHttpClient = OkHttpClient().newBuilder()
            .connectTimeout(2, TimeUnit.MINUTES)
            .readTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .build()
        Rx2AndroidNetworking.post(url)
            .setOkHttpClient(okHttpClient)
            .addBodyParameter("android_version", appVersion)
            .addHeaders("Authorization", authorizationHeader)
            .addBodyParameter(params)
            .setPriority(Priority.MEDIUM)
            .build()
            .stringObservable
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<String> {
                override fun onSubscribe(d: Disposable) {}
                override fun onNext(jsonObject: String) {
                    response = jsonObject + ""
                    Log.e("WS_RESP_", tag + " : " + response)
                }

                override fun onError(e: Throwable) {
                    try {
                        e.printStackTrace()
                        apiResponse.onFailure(e.localizedMessage, tag)
                    } catch (exception: Exception) {
                        exception.printStackTrace()
                    }
                }

                override fun onComplete() {
                    try {
                        apiResponse.onSuccess(response, tag)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        apiResponse.onFailure(e.localizedMessage, tag)
                    }
                }
            })
    }

    private fun initializeSSLContext(mContext: Activity?) {
        if (mContext != null) {
            try {
                SSLContext.getInstance("TLSv1.2")
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            }
            try {
                ProviderInstaller.installIfNeeded(mContext.applicationContext)
            } catch (e: GooglePlayServicesRepairableException) {
                e.printStackTrace()
            } catch (e: GooglePlayServicesNotAvailableException) {
                e.printStackTrace()
            }
        }
    }
}