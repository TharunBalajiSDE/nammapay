package com.tharunbalaji.nammapay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tharunbalaji.nammapay.databinding.ActivityDeeplinkBinding
import com.tharunbalaji.nammapay.databinding.ActivityMainBinding
import com.tharunbalaji.nammapay.utils.AuthorizationMethods
import com.tharunbalaji.nammapay.utils.Creds
import com.tharunbalaji.nammapay.utils.JuspayCallback
import com.tharunbalaji.nammapay.utils.getJWSSignature
import com.tharunbalaji.nammapay.utils.getPrivateKeyFromString
import com.tharunbalaji.nammapay.utils.getSignedData
import com.tharunbalaji.nammapay.utils.getUpiRequestId
import com.tharunbalaji.nammapay.utils.readPrivateString
import `in`.juspay.hyperinteg.HyperServiceHolder
import `in`.juspay.hypersdk.data.JuspayResponseHandler
import `in`.juspay.hypersdk.ui.HyperPaymentsCallbackAdapter
import org.json.JSONObject
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.UUID
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private var hyperServicesHolder: HyperServiceHolder? = null
    private var initiatePayload: JSONObject? = null
    private lateinit var binding: ActivityDeeplinkBinding

    override fun onStart() {
        super.onStart()
        hyperServicesHolder = HyperServiceHolder(this)
        hyperServicesHolder!!.setCallback(createHyperPaymentsCallbackAdapter())

        initiatePaymentsSDK()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeeplinkBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    private fun initiatePaymentsSDK() {
        initiatePayload = createInitiatePayload()
        hyperServicesHolder!!.initiate(createInitiatePayload())
    }

    private fun createInitiatePayload(): JSONObject {
        val sdkPayload = JSONObject()
        val innerPayload = JSONObject()
        val signaturePayload = JSONObject()
        try {
            innerPayload.put("action", "initiate")
            innerPayload.put("merchantKeyId", Creds.MERCHANT_KEY_ID)
            innerPayload.put("clientId", Creds.CLIENT_ID)
            innerPayload.put("environment", Creds.ENV)
            innerPayload.put("issuingPsp", Creds.ISSUING_PSP)

            signaturePayload.put("merchant_id", Creds.MERCHANT_ID)
            signaturePayload.put("customer_id", Creds.CUSTOMER_ID)
            signaturePayload.put("order_id", "hyper${System.currentTimeMillis()}")
            signaturePayload.put("timestamp", System.currentTimeMillis().toString())

            innerPayload.put("signature", getSignedData(signaturePayload.toString(), readPrivateString(this, AuthorizationMethods.RSA)))
            innerPayload.put("signaturePayload", signaturePayload.toString())

            sdkPayload.put("requestId", UUID.randomUUID().toString())
            sdkPayload.put("service", "in.juspay.ec")
            sdkPayload.put("payload", innerPayload)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sdkPayload
    }

    private fun callIncomingIntent(dataUri: String) {
        val processPayload = JSONObject()
        val innerPayload = JSONObject()
        val signaturePayload = JSONObject()

        try {
            innerPayload.put("action", "incomingIntent")
            innerPayload.put("intentData", dataUri)

            signaturePayload.put("merchant_id", Creds.MERCHANT_ID)
            signaturePayload.put("customer_id", Creds.CUSTOMER_ID)
            signaturePayload.put("order_id", "hyper${System.currentTimeMillis()}")
            signaturePayload.put("timestamp", System.currentTimeMillis().toString())

            innerPayload.put("signature", getSignedData(signaturePayload.toString(), readPrivateString(this, AuthorizationMethods.RSA)))
            innerPayload.put("signaturePayload", signaturePayload.toString())
            innerPayload.put("showStatusScreen", false)

            processPayload.put("requestId", UUID.randomUUID().toString())
            processPayload.put("service", "in.juspay.hyperupi")
            processPayload.put("payload", innerPayload)

        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (hyperServicesHolder?.isInitialised == true) {
            hyperServicesHolder?.process(processPayload)
        } else {
            Log.e("THARUN", "HyperServicesHolder is not initialized! Cannot process payment.")
        }
    }

    private fun createHyperPaymentsCallbackAdapter(): HyperPaymentsCallbackAdapter {
        return object : HyperPaymentsCallbackAdapter() {
            override fun onEvent(jsonObject: JSONObject, responseHandler: JuspayResponseHandler?) {
                try {
                    val event = jsonObject.getString("event")

                    if (event == "hide_loader") {
                        // Hide Loader
                    }

                    else if (event == "show_loader") {
                        // Show some loader
                    }

                    else if (event == "initiate_result") {
                        val innerPayload = jsonObject.optJSONObject("payload")
                        if (innerPayload != null) {
                            if (innerPayload.getJSONObject("payload").get("action") == "initiate" && innerPayload.getJSONObject("payload").get("status") == "success") {
                                binding.llInitializing.visibility = View.GONE
                                binding.llProcessing.visibility = View.VISIBLE
                                handleDeeplinkIntent(intent)
                            }
                            Log.d("THARUN (initiate_result): ", innerPayload.toString())
                        }
                    }

                    else if (event == "process_result") {
                        val innerPayload = jsonObject.optJSONObject("payload")
                        if (innerPayload != null) {
                            Log.d("THARUN (process_result): ", innerPayload.toString())

                            if (innerPayload.get("action") == "incomingIntent") {
                                binding.llProcessing.visibility = View.GONE
                                binding.llFetching.visibility = View.VISIBLE

                                val returnIntent = Intent().apply {
                                    putExtra("result_json", innerPayload.toString())
                                }

                                callTerminate()
                                setResult(Activity.RESULT_OK, returnIntent)
                                finish()
                            }

                        }
                    }
                    else if (event == "log_stream") {
                        val innerPayload = jsonObject.optJSONObject("payload")
                        if (innerPayload != null) {
                            Log.d("THARUN (log_stream): ", innerPayload.toString())
                        }
                    }
                    else if (event == "session_expiry") {
                        // Handle Session Expiry
                        Log.d("THARUN (session_expiry): ", "SESSION EXPIRED")
                    }

                } catch (e: Exception) {
                    Log.d("THARUN (exception): ",  e.message.toString())
                }
            }
        }
    }

    private fun handleDeeplinkIntent(intent: Intent) {
        val data: Uri? = intent.data
        if (data != null) {
            callIncomingIntent(data.toString())
        } else {
            Log.d("THARUN (DEEPLINK)", "No data received")
        }
    }

    private fun callTerminate() {
        hyperServicesHolder?.terminate()
    }

    override fun onDestroy() {
        super.onDestroy()
        callTerminate()
    }

}