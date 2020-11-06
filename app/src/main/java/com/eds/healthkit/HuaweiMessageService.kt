package com.eds.healthkit

import android.util.Log
import com.google.gson.GsonBuilder
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage

class HuaweiMessageService: HmsMessageService() {

    companion object {
        private val TAG: String = HuaweiMessageService::class.java.simpleName
    }

    override fun onNewToken(token: String?) {
        super.onNewToken(token)
        Log.v(TAG, "Token in service is $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        super.onMessageReceived(remoteMessage)
        Log.v(TAG, "Remote message is ${GsonBuilder().setPrettyPrinting().create().toJson(remoteMessage)}")
    }
}