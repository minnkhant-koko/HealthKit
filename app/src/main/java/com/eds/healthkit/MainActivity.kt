package com.eds.healthkit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import com.eds.healthkit.databinding.ActivityMainBinding
import com.google.gson.GsonBuilder
import com.huawei.agconnect.config.AGConnectServicesConfig
import com.huawei.hmf.tasks.Task
import com.huawei.hms.aaid.HmsInstanceId
import com.huawei.hms.common.ApiException
import com.huawei.hms.hihealth.HiHealthActivities
import com.huawei.hms.hihealth.HiHealthOptions
import com.huawei.hms.hihealth.HuaweiHiHealth
import com.huawei.hms.hihealth.SettingController
import com.huawei.hms.hihealth.data.Scopes
import com.huawei.hms.iap.Iap
import com.huawei.hms.iap.IapApiException
import com.huawei.hms.iap.entity.ConsumeOwnedPurchaseReq
import com.huawei.hms.iap.entity.InAppPurchaseData
import com.huawei.hms.iap.entity.OrderStatusCode
import com.huawei.hms.iap.util.IapClientHelper
import com.huawei.hms.support.api.entity.auth.Scope
import com.huawei.hms.support.api.entity.common.CommonConstant
import com.huawei.hms.support.hwid.HuaweiIdAuthAPIManager
import com.huawei.hms.support.hwid.HuaweiIdAuthManager
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParams
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParamsHelper
import com.huawei.hms.support.hwid.result.AuthHuaweiId
import com.huawei.hms.support.hwid.service.HuaweiIdAuthService
import org.json.JSONException
import java.lang.Exception
import java.net.URI

class MainActivity: AppCompatActivity(), HuaweiLogInDelegate, HuaweiIAPDelegate {

    private lateinit var huaweiAuthService : HuaweiIdAuthService
    private lateinit var huaweiAuthParams : HuaweiIdAuthParams

    companion object {
        private var TAG = MainActivity::class.java.simpleName
        private const val REQUEST_LOGIN_CODE = 1102
        private const val HEALTH_SCHEME = "huaweischeme://healthapp/achievement?module=kit"
        private const val REQUEST_HEALTH_AUTH = 1003
        fun getIntent(context: Context) = Intent(context, MainActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        token()
    }

    private fun token() {
        object : Thread() {
            override fun run() {
                try {
                    val appId = AGConnectServicesConfig.fromContext(applicationContext).getString("client/app_id")
                    val token = HmsInstanceId.getInstance(applicationContext).getToken(appId, "HCM")
                    if (token != null) {
                        Log.v(TAG, "Token is : $token")
                    }
                } catch (e: Exception) {

                    Log.v(TAG, "Token Error is : ${e.localizedMessage}")
                }
            }
        }.start()
    }

    private fun signIn() {
        huaweiAuthParams = HuaweiIdAuthParamsHelper(HuaweiIdAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
            .setIdToken()
            .setAccessToken()
            // Scopes for Health Kits
            .setScopeList(listOf(Scope(Scopes.HEALTHKIT_STEP_BOTH), Scope(Scopes.HEALTHKIT_HEIGHTWEIGHT_BOTH)))
            .createParams()
        huaweiAuthService = HuaweiIdAuthManager.getService(this@MainActivity , huaweiAuthParams)

        val huaweiIdTasks: Task<AuthHuaweiId> = huaweiAuthService.silentSignIn()

        huaweiIdTasks.addOnSuccessListener {
            Log.v(TAG, "Silent SignIn Success :")
            Log.v(TAG, GsonBuilder().setPrettyPrinting().create().toJson(it))
            this.startActivityForResult(huaweiAuthService.signInIntent, REQUEST_LOGIN_CODE)
//            findNavController(R.id.fragmentContainer).navigate(R.id.action_logInFragment_to_homeFragment)
        }.addOnFailureListener {
            if (it is ApiException) {
                val exception: ApiException = it
                Log.v(TAG, "Silent SignIn Failure :")
                Log.v(TAG, GsonBuilder().setPrettyPrinting().create().toJson(exception))
                val signInIntent = huaweiAuthService.signInIntent
                startActivityForResult(signInIntent, REQUEST_LOGIN_CODE)
            }
        }

    }

    override fun signInWithHuawei() {
        signIn()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_LOGIN_CODE) {
            val result = HuaweiIdAuthManager.parseAuthResultFromIntent(data)
            if (result.isSuccessful) {
                Results.authResults = HuaweiIdAuthAPIManager.HuaweiIdAuthAPIService.parseHuaweiIdFromIntent(data)
                Log.v(TAG, "This is result -> ${GsonBuilder().setPrettyPrinting().create().toJson(result)}")
                checkAndAuthorizeHealthApp()
            } else if (result.isCanceled) {
                Toast.makeText(applicationContext, getString(R.string.lbl_huawei_login_canceled), Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == REQUEST_HEALTH_AUTH) {
            Log.i(TAG, "REQUEST_WAS_SUCCESS")
            findNavController(R.id.fragmentContainer).navigate(R.id.action_logInFragment_to_homeFragment)
        } else if (requestCode == 6666) {
            if (data != null) {
                val returnCode = IapClientHelper.parseRespCodeFromIntent(data)
                val purchaseResultInfo = Iap.getIapClient(this).parsePurchaseResultInfoFromIntent(data)
                when(purchaseResultInfo.returnCode) {
                    OrderStatusCode.ORDER_STATE_CANCEL -> {
                        Log.v(TAG, "User cancel the order")
                    }
                    OrderStatusCode.ORDER_STATE_FAILED -> {
                        Log.v(TAG, "User failed to purchase order")
                    }
                    OrderStatusCode.ORDER_PRODUCT_OWNED -> {
                        Log.v(TAG, "User already owned the product")
                    }
                    OrderStatusCode.ORDER_STATE_SUCCESS -> {
                        val inAppPurchaseData = purchaseResultInfo.inAppPurchaseData
                        val inAppPurchaseDataSignature = purchaseResultInfo.inAppDataSignature

                        Log.v(TAG,"Order Success : ${GsonBuilder().setPrettyPrinting().create().toJson(inAppPurchaseData)}")
                        Log.v(TAG,"Order Success : ${GsonBuilder().setPrettyPrinting().create().toJson(inAppPurchaseDataSignature)}")

                        var purchaseToken = ""

                        try {
                            val iapPurchase = InAppPurchaseData(inAppPurchaseData)
                            purchaseToken = iapPurchase.purchaseToken
                        } catch (e: JSONException) {

                        }

                        val req = ConsumeOwnedPurchaseReq()
                        req.purchaseToken = purchaseToken
                        val tasks = Iap.getIapClient(this).consumeOwnedPurchase(req)
                        tasks.addOnSuccessListener {
                            Log.v(TAG,"Confirm Purchase is success")
                        }.addOnFailureListener {
                            if (it is IapApiException) {
                                val apiException = it as IapApiException
                                val status = apiException.status
                                val code = apiException.statusCode
                                Log.v(TAG, "Confirm Purchase, ReturnCode is $code")
                            }
                        }
                    }
                    else -> {}
                }
            } else {
                Log.e(TAG, "Data is null")
                return
            }
        }
    }

    override fun checkHuaweiIAPIsAvailable() {
        
    }

    private fun checkAndAuthorizeHealthApp() {
        Log.i(TAG, "Begin to checkOrAuthorizeHealthApp")

        val settingController: SettingController = HuaweiHiHealth.getSettingController(
            this,
            HuaweiIdAuthManager.getExtendedAuthResult(
                HiHealthOptions
                    .builder()
                    .build()
            )
        )

        val task = settingController.checkHealthAppAuthorization()

        task.addOnSuccessListener {
            val uri: Uri  = Uri.parse(HEALTH_SCHEME)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            if (intent.resolveActivity(packageManager) != null) {
                this@MainActivity.startActivityForResult(intent, REQUEST_HEALTH_AUTH)
            }
        }.addOnFailureListener {
            Log.e(TAG, "Error => ${it.message}")
        }
    }
}