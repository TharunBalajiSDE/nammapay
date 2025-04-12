package com.tharunbalaji.nammapay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
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
            // Generating inner payload
            innerPayload.put("action", "initiate")
            innerPayload.put("merchantKeyId", "35554")
            innerPayload.put("clientId", "testhyperupi")
            innerPayload.put("environment", "sandbox")
            innerPayload.put("issuingPsp", "YES_BIZ")


            signaturePayload.put("merchant_id", Creds.MERCHANT_ID)
            signaturePayload.put("customer_id", Creds.CUSTOMER_ID)
            signaturePayload.put("order_id", "hyper${System.currentTimeMillis()}")
            signaturePayload.put("timestamp", System.currentTimeMillis().toString())

            innerPayload.put("signature", getSignedData(signaturePayload.toString(), readPrivateString(this, AuthorizationMethods.RSA)))
            innerPayload.put("signaturePayload", signaturePayload.toString())

            sdkPayload.put("requestId", UUID.randomUUID().toString())
            sdkPayload.put("service", "in.juspay.ec")
            sdkPayload.put("payload", innerPayload)

            Log.d("THARUN (Payload Debug)", sdkPayload.toString())  // Debug Log
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
            innerPayload.put("merchantKeyId", Creds.MERCHANT_KEY_ID)
            innerPayload.put("clientId", Creds.CLIENT_ID)
            innerPayload.put("environment", Creds.ENV)
            innerPayload.put("issuingPsp", Creds.ISSUING_PSP)
            innerPayload.put("intentData", dataUri)

            signaturePayload.put("merchant_id", Creds.MERCHANT_ID)
            signaturePayload.put("customer_id", Creds.CUSTOMER_ID)
            signaturePayload.put("order_id", "hyper${System.currentTimeMillis()}")
            signaturePayload.put("timestamp", System.currentTimeMillis().toString())

            innerPayload.put("signature", getSignedData(signaturePayload.toString(), readPrivateString(this, AuthorizationMethods.RSA)))
            innerPayload.put("signaturePayload", signaturePayload.toString())

            processPayload.put("requestId", UUID.randomUUID().toString())
            processPayload.put("service", "in.juspay.hyperupi")
            processPayload.put("payload", innerPayload)

            Log.d("THARUN (Payload Debug)", processPayload.toString())

        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (hyperServicesHolder?.isInitialised == true) {
            hyperServicesHolder?.process(processPayload)
            Log.d("THARUN", "Calling process")
        } else {
            Log.e("THARUN", "HyperServicesHolder is not initialized! Cannot process payment.")
        }
    }

    private fun createHyperPaymentsCallbackAdapter(): HyperPaymentsCallbackAdapter {
        return object : HyperPaymentsCallbackAdapter() {
            override fun onEvent(jsonObject: JSONObject, responseHandler: JuspayResponseHandler?) {
                try {
                    // block:start:handle-sdk-response
                    val event = jsonObject.getString("event")

                    // block:start:hide-loader
                    if (event == "hide_loader") {
                        // Hide Loader
                    }
                    // block:end:hide-loader

                    // block:start:show-loader
                    else if (event == "show_loader") {
                        // Show some loader
                    }
                    // block:end:show-loader

                    // block:start:initiate-result
                    else if (event == "initiate_result") {
                        val innerPayload = jsonObject.optJSONObject("payload")
                        if (innerPayload != null) {
                            if (innerPayload.getJSONObject("payload").get("status") == "success") {
                                binding.llInitializing.visibility = View.GONE
                                binding.llProcessing.visibility = View.VISIBLE
                                handleDeeplinkIntent(intent)
                            }
                            Log.d("THARUN (initiate_result): ", innerPayload.toString())
                        }
                    }
                    // block:end:initiate-result

                    // block:start:process-result
                    else if (event == "process_result") {
                        val innerPayload = jsonObject.optJSONObject("payload")
                        if (innerPayload != null) {
                            Log.d("THARUN (process_result): ", innerPayload.toString())
                            binding.llProcessing.visibility = View.GONE
                            binding.tvResponse.visibility = View.VISIBLE
                            binding.tvResponse.text = innerPayload.toString()
                        }
                    }
                    // block:end:process-result

                    // block:start:log-stream
                    else if (event == "log_stream") {
                        val innerPayload = jsonObject.optJSONObject("payload")
                        if (innerPayload != null) {
                            Log.d("THARUN (log_stream): ", innerPayload.toString())
                        }
                    }
                    // block:end:log-stream

                    // block:start:session-expiry
                    else if (event == "session_expiry") {
                        // Handle Session Expiry
                        Log.d("THARUN (session_expiry): ", "SESSION EXPIRED")
                    }
                    // block:end:session-expiry

                } catch (e: Exception) {
                    Log.d("THARUN (exception): ",  e.message.toString())
                }
                // block:end:handle-sdk-response
            }
        }
    }

    private fun handleDeeplinkIntent(intent: Intent) {
        val data: Uri? = intent.data
        if (data != null) {
            Log.d("THARUN (DEEPLINK)", "Host: ${data.host}")
            Log.d("THARUN (DEEPLINK)", "Path: ${data.path}")
            Log.d("THARUN (DEEPLINK)", "Query: ${data.query}")
            Log.d("THARUN (DEEPLINK)", "Full URI: $data")

            callIncomingIntent(data.toString())
        } else {
            Log.d("THARUN (DEEPLINK)", "No data received")
        }
    }

}