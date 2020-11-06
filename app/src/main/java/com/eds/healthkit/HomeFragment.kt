package com.eds.healthkit

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.eds.healthkit.databinding.FragmentHomeBinding
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.huawei.hmf.tasks.Task
import com.huawei.hms.hihealth.HiHealthOptions
import com.huawei.hms.hihealth.HiHealthStatusCodes
import com.huawei.hms.hihealth.HuaweiHiHealth
import com.huawei.hms.hihealth.data.DataCollector
import com.huawei.hms.hihealth.data.DataType
import com.huawei.hms.hihealth.options.ReadOptions
import com.huawei.hms.iap.Iap
import com.huawei.hms.iap.IapApiException
import com.huawei.hms.iap.entity.*
import com.huawei.hms.iap.util.IapClientHelper
import com.huawei.hms.support.hwid.HuaweiIdAuthManager
import com.huawei.hms.support.hwid.result.AuthHuaweiId
import kotlinx.android.synthetic.main.fragment_home.*
import org.json.JSONException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    companion object {
        private var TAG = HomeFragment::class.java.simpleName
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.btnPurchase.setOnClickListener {
            initializingPurchase(requireActivity())
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val healthOptions: HiHealthOptions = HiHealthOptions.builder()
            .addDataType(DataType.DT_CONTINUOUS_STEPS_DELTA, HiHealthOptions.ACCESS_READ)
            .build()

        val authHuaweiId: AuthHuaweiId = HuaweiIdAuthManager.getExtendedAuthResult(healthOptions)

        val dataController =
            HuaweiHiHealth.getDataController(requireActivity().applicationContext, authHuaweiId)

        val dataCollector = DataCollector.Builder()
            .setPackageName(requireActivity().applicationContext)
            .setDataType(DataType.DT_CONTINUOUS_STEPS_DELTA)
            .setDataStreamName("STEPS_DELTA")
            .setDataGenerateType(DataCollector.DATA_TYPE_RAW)
            .build()

        val todaySummationTask = dataController.readTodaySummation(DataType.DT_CONTINUOUS_STEPS_DELTA)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.ROOT)

        val readOptions = ReadOptions.Builder().read(dataCollector)
            .setTimeRange(
                dateFormat.parse("2020-11-6 02:00:00").time,
                dateFormat.parse("2020-11-6 02:30:00").time,
                TimeUnit.MILLISECONDS
            )
            .build()

        val readReplyTask = dataController.read(readOptions)
        val syncTask = dataController.syncAll()

        syncTask.addOnSuccessListener {
            Log.i(TAG, "Sync Success")
            readReplyTask.addOnSuccessListener { readReply ->
                for (sample in readReply.sampleSets) {
                    Log.v(TAG, "Sample => ${GsonBuilder().setPrettyPrinting().create().toJson(sample)}")
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error Code => ${e.message}")
                Log.e(
                    TAG,
                    "Error Message => ${GsonBuilder().setPrettyPrinting().create().toJson(e)}"
                )
            }

            todaySummationTask.addOnSuccessListener {
                    sampleSets ->
                Log.v(TAG, "Sample Success")
                var i = 1
                for (samplePoint in sampleSets.samplePoints) {
                    val steps = samplePoint.getFieldValue(samplePoint.dataType.fields[0])
                    Log.i(TAG, "Total Steps => $steps")
                    tvValueYourSteps.text = steps.toString()
                }
            }.addOnFailureListener {
                Log.e(TAG, "Error Code => ${it.message}")
            }
        }.addOnFailureListener {
            Log.e(TAG, "Sync Failed ${it.message}")
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val _activity = activity
        super.onActivityCreated(savedInstanceState)
        val tasks: Task<IsEnvReadyResult> = Iap.getIapClient(_activity).isEnvReady
        tasks.addOnSuccessListener {
            Log.v(TAG, "IAP Env is ready ${it.returnCode}")
        }.addOnFailureListener {
            if (it is IapApiException) {
                val iapApiException = it as IapApiException
                val status = iapApiException.status
                if (status.statusCode == OrderStatusCode.ORDER_HWID_NOT_LOGIN) {
                    if (status.hasResolution()) {
                        try {
                            status.startResolutionForResult(_activity, 6666)
                        } catch (e : IntentSender.SendIntentException) {
                            Log.v(TAG, "Error message is ${e.message}")
                        }
                    }
                } else if (status.statusCode == OrderStatusCode.ORDER_ACCOUNT_AREA_NOT_SUPPORTED) {
                    Log.v(TAG, "Order Account Area is not supported")
                }
            }
        }.addOnCanceledListener {

        }

        displayProductInformation(_activity)
    }

    private fun displayProductInformation(activity : FragmentActivity?) {
        val productIdList = mutableListOf<String>()
        productIdList.add("comsumables_health")
        val req = ProductInfoReq()
        req.priceType = 0
        req.productIds = productIdList
        val task : Task<ProductInfoResult> = Iap.getIapClient(activity).obtainProductInfo(req)
        task.addOnSuccessListener {
            val productInfo = it.productInfoList
            Log.v(TAG, "Products ${GsonBuilder().setPrettyPrinting().create().toJson(productInfo)}")
            Log.v(TAG, "IAP is success.")
        }.addOnFailureListener {
            if (it is IapApiException) {
                val iapException : IapApiException = it 
                val returnCode = iapException.statusCode
                Log.v(TAG, "IAP Error is $returnCode")
            } else {
                Log.v(TAG, "IAP Other Error is ${it.localizedMessage}")
            }
        }
    }

    private fun initializingPurchase(activity: FragmentActivity?) {
        val request = PurchaseIntentReq()
        request.productId = "comsumables_health"
        request.priceType = 0
        request.developerPayload = "test"
        val isSandboxActivatedReq = IsSandboxActivatedReq()
        Log.v(TAG, "Is sand box is activated : ${Iap.getIapClient(activity).isSandboxActivated(isSandboxActivatedReq)}")
        val tasks = Iap.getIapClient(activity).createPurchaseIntent(request)
        tasks.addOnSuccessListener {
            if (it.status.hasResolution()) {
                try {
                    it.status.startResolutionForResult(activity, 6666)
                } catch (e: IntentSender.SendIntentException) {
                    Log.e(TAG, "Purchase Exception: ${GsonBuilder().setPrettyPrinting().create().toJson(e)}")
                }
            }
        }.addOnFailureListener {
            if (it is IapApiException) {
                val apiException: IapApiException = it
                val status = apiException.status
                val returnCode = apiException.statusCode
                Log.e(TAG, "Status Error, $returnCode")
            }
        }
    }
}