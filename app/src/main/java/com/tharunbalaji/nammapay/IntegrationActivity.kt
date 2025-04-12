package com.tharunbalaji.nammapay

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tharunbalaji.nammapay.databinding.ActivityMainBinding
import com.tharunbalaji.nammapay.utils.AuthorizationMethods
import com.tharunbalaji.nammapay.utils.Creds
import com.tharunbalaji.nammapay.utils.JuspayCallback
import com.tharunbalaji.nammapay.utils.createJuspayOrder
import com.tharunbalaji.nammapay.utils.getJWSSignature
import com.tharunbalaji.nammapay.utils.getSignedData
import com.tharunbalaji.nammapay.utils.readPrivateString
import `in`.juspay.hyperinteg.HyperServiceHolder
import `in`.juspay.hypersdk.data.JuspayResponseHandler
import `in`.juspay.hypersdk.ui.HyperPaymentsCallbackAdapter
import org.json.JSONObject
import java.util.*


class IntegrationActivity : AppCompatActivity() {

    private var hyperServicesHolder: HyperServiceHolder? = null
    private var initiatePayload: JSONObject? = null
    private lateinit var binding: ActivityMainBinding
    private var authMethod: AuthorizationMethods = AuthorizationMethods.CAT

    override fun onStart() {
        super.onStart()
        hyperServicesHolder = HyperServiceHolder(this)
        hyperServicesHolder!!.setCallback(createHyperPaymentsCallbackAdapter())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnInitiate.setOnClickListener {
            initiatePaymentsSDK()
            binding.llLoading.visibility = View.VISIBLE
        }

        binding.btnProcess.setOnClickListener {
            callProcess()
        }

        binding.btnTerminate.setOnClickListener {
            callTerminate()
            binding.btnProcess.isEnabled = false
            binding.btnTerminate.isEnabled = false
        }

        binding.radioGroup.setOnCheckedChangeListener { _, i ->
            authMethod = when (i) {
                R.id.radioButton1 -> AuthorizationMethods.CAT
                R.id.radioButton2 -> AuthorizationMethods.RSA
                R.id.radioButton3 -> AuthorizationMethods.JWS
                else -> AuthorizationMethods.RSA
            }
        }

        binding.btnIntent.setOnClickListener {
            callIncomingIntent()
        }
    }

    private fun initiatePaymentsSDK() {
        initiatePayload = createInitiatePayload()
        hyperServicesHolder?.initiate(initiatePayload)
    }

    private fun createInitiatePayload(): JSONObject {
        val sdkPayload = JSONObject()
        val innerPayload = JSONObject()
        val signaturePayload = JSONObject()

        try {
            innerPayload.put("action", "initiate")
            innerPayload.put("merchantKeyId", Creds.MERCHANT_KEY_ID)
            innerPayload.put("clientId", Creds.CLIENT_ID)
            innerPayload.put("customerId", Creds.CUSTOMER_ID)
            innerPayload.put("merchantId", Creds.MERCHANT_ID)
            innerPayload.put("environment", Creds.ENV)
            innerPayload.put("issuingPsp", Creds.ISSUING_PSP)

            when (authMethod) {
                AuthorizationMethods.CAT -> {
                    signaturePayload.put("timestamp", System.currentTimeMillis().toString())
                    val orderId = "hyper" + System.currentTimeMillis().toString()
                    innerPayload.put("orderId", orderId)


                    createJuspayOrder(orderId, object : JuspayCallback {
                        override fun onSuccess(token: String) {
                            innerPayload.put("clientAuthToken", token)
                            Log.d("THARUN (clientAuthToken): ", token)
                        }
                        override fun onError(e: Exception) {
                            println("Error: ${e.message}")
                        }
                    })
                }
                AuthorizationMethods.RSA -> {
                    signaturePayload.put("merchant_id", Creds.MERCHANT_ID)
                    signaturePayload.put("customer_id", Creds.CUSTOMER_ID)
                    signaturePayload.put("order_id", "hyper${System.currentTimeMillis()}")
                    signaturePayload.put("timestamp", System.currentTimeMillis().toString())
                    innerPayload.put("signature", getSignedData(signaturePayload.toString(), readPrivateString(this, authMethod)))
                    innerPayload.put("signaturePayload", signaturePayload.toString())
                }
                AuthorizationMethods.JWS -> {
                    innerPayload.put("merchantId", Creds.MERCHANT_ID)
                    innerPayload.put("enableJwsAuth", true)
                    signaturePayload.put("merchantId", Creds.PSP_MERCHANT_ID)
                    signaturePayload.put("merchantChannelId", Creds.PSP_MERCHANT_CHANNEL_ID)
                    signaturePayload.put("merchantCustomerId", Creds.CUSTOMER_ID)
                    signaturePayload.put("customerMobileNumber", "919677449189")
                    signaturePayload.put("timestamp", System.currentTimeMillis().toString())

                    val jwspayload = getJWSSignature(signaturePayload.toString(), readPrivateString(this, authMethod), Creds.JWS_KID).split('.')
                    innerPayload.put("protected", jwspayload[0])
                    innerPayload.put("signaturePayload", jwspayload[1])
                    innerPayload.put("signature", jwspayload[2])
                }
            }

            sdkPayload.put("requestId", UUID.randomUUID().toString())
            sdkPayload.put("service", "in.juspay.ec")
            sdkPayload.put("payload", innerPayload)

            Log.d("THARUN (Payload Debug)", sdkPayload.toString())

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return sdkPayload
    }

    private fun createHyperPaymentsCallbackAdapter(): HyperPaymentsCallbackAdapter {
        return object : HyperPaymentsCallbackAdapter() {
            override fun onEvent(jsonObject: JSONObject, responseHandler: JuspayResponseHandler?) {
                binding.llLoading.visibility = View.INVISIBLE
                try {
                    val event = jsonObject.getString("event")
                    Log.d("THARUN (jsonObject): ", jsonObject.toString())

                    if (event == "hide_loader") {
                        // Hide Loader
                    }

                    else if (event == "show_loader") {
                        // Show some loader
                    }

                    else if (event == "initiate_result") {
                        val innerPayload = jsonObject.optJSONObject("payload")

                        Toast.makeText(this@IntegrationActivity, "Successfully initiated", Toast.LENGTH_SHORT).show()

                        if (innerPayload != null) {
                            if (innerPayload.getJSONObject("payload").get("status") == "success") {
                                binding.btnInitiate.isEnabled = false
                                binding.btnProcess.isEnabled = true
                                binding.btnTerminate.isEnabled = true
                                binding.btnIntent.isEnabled = true
                            }
                        }
                    }
                    else if (event == "process_result") {
                        val innerPayload = jsonObject.optJSONObject("payload")
                        if (innerPayload != null) {
                            val responseCode = innerPayload.optString("gatewayResponseCode")
                                .ifEmpty { innerPayload.optJSONObject("payload")?.optString("gatewayResponseCode").orEmpty() }

                            when (responseCode) {
                                "00" -> Toast.makeText(this@IntegrationActivity, "Payment Successful", Toast.LENGTH_SHORT).show()
                                "01" -> Toast.makeText(this@IntegrationActivity, "Payment is Pending", Toast.LENGTH_SHORT).show()
                                else -> Toast.makeText(this@IntegrationActivity, "Payment Failed", Toast.LENGTH_SHORT).show()
                            }

                            Log.d("THARUN (process_result): ", innerPayload.toString())
                        }
                    }
                    else if (event == "log_stream") {
                        val innerPayload = jsonObject.optJSONObject("payload")
                        if (innerPayload != null) {

                            Log.d("THARUN (log_stream): ", innerPayload.toString())
                        }
                    }
                    else if (event == "session_expiry") {
                        Toast.makeText(this@IntegrationActivity, "Session Expired", Toast.LENGTH_SHORT).show()

                        callUpdateAuth()

                        Log.d("THARUN (session_expiry): ", "SESSION EXPIRED")
                    }

                } catch (e: Exception) {
                    Log.d("THARUN (exception): ",  e.message.toString())
                }
            }
        }
    }

    private fun callProcess() {
        val processPayload = JSONObject()
        val innerPayload = JSONObject()
        val signaturePayload = JSONObject()

        try {
            innerPayload.put("action", "onboardingAndPay")
            innerPayload.put("merchantKeyId", Creds.MERCHANT_KEY_ID)
            innerPayload.put("customerId", Creds.CUSTOMER_ID)
            innerPayload.put("clientId", Creds.CLIENT_ID)
            innerPayload.put("merchantId", Creds.MERCHANT_ID)
            innerPayload.put("environment", Creds.ENV)
            innerPayload.put("issuingPsp", Creds.ISSUING_PSP)

            when (authMethod) {
                AuthorizationMethods.CAT -> {
                    innerPayload.put("amount", "10.00")

                    val orderId = "hyper" + System.currentTimeMillis().toString()
                    innerPayload.put("orderId", orderId)

                    createJuspayOrder(orderId, object : JuspayCallback {
                        override fun onSuccess(token: String) {
                            innerPayload.put("clientAuthToken", token)
                            Log.d("THARUN (clientAuthToken): ", token)
                        }
                        override fun onError(e: Exception) {
                            println("Error: ${e.message}")
                        }
                    })
                }

                AuthorizationMethods.RSA -> {
                    signaturePayload.put("merchant_id", Creds.MERCHANT_ID)
                    signaturePayload.put("customer_id", Creds.CUSTOMER_ID)
                    signaturePayload.put("amount", "10.00")
                    signaturePayload.put("order_id", "hyper${System.currentTimeMillis()}")
                    signaturePayload.put("timestamp", System.currentTimeMillis().toString())

                    innerPayload.put("signature", getSignedData(signaturePayload.toString(), readPrivateString(this, authMethod)))
                    innerPayload.put("signaturePayload", signaturePayload.toString())
                }

                AuthorizationMethods.JWS -> {
                    innerPayload.put("merchantId", Creds.MERCHANT_ID)

                    signaturePayload.put("merchantId", Creds.PSP_MERCHANT_ID)
                    signaturePayload.put("merchantChannelId", Creds.PSP_MERCHANT_CHANNEL_ID)
                    signaturePayload.put("customerMobileNumber", "919677449189")
                    signaturePayload.put("merchantCustomerId", Creds.CUSTOMER_ID)
                    signaturePayload.put("merchantVpa", "hyperupitest@ypay")
                    signaturePayload.put("amount", "10.00")
                    signaturePayload.put("merchantRequestId", "hyper${System.currentTimeMillis()}")
                    signaturePayload.put("timestamp", System.currentTimeMillis().toString())

                    val jwspayload = getJWSSignature(
                        signaturePayload.toString(),
                        readPrivateString(this, authMethod),
                        Creds.JWS_KID
                    ).split('.')

                    innerPayload.put("protected", jwspayload[0])
                    innerPayload.put("signaturePayload", jwspayload[1])
                    innerPayload.put("signature", jwspayload[2])
                    innerPayload.put("enableJwsAuth", true)
                }
            }

            processPayload.put("requestId", UUID.randomUUID().toString())
            processPayload.put("service", "in.juspay.ec")
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

    private fun callIncomingIntent() {
        val processPayload = JSONObject()
        val innerPayload = JSONObject()
        val signaturePayload = JSONObject()

        try {
            innerPayload.put("action", "incomingIntent")
            innerPayload.put("merchantKeyId", Creds.MERCHANT_KEY_ID)
            innerPayload.put("clientId", Creds.CLIENT_ID)
            innerPayload.put("environment", Creds.ENV)
            innerPayload.put("issuingPsp", Creds.ISSUING_PSP)
            innerPayload.put("intentData", "upi://pay?pa=swiggytest@ypay&pn=test&tn=SignedIntentTesting&mam=null&cu=INR&mode=01&msid=&mtid=&orgid=189532&sign=MEYCIQC91EyrsD9370BijZXqAV+VhsLz0hjKEPf5YWzzUF29yQIhAKQqKUQF4ieF4VlFwAbu3O5v6pkxliPdAl+KrFB6rMw0")


            when (authMethod) {
                AuthorizationMethods.CAT -> {
                    val orderId = "hyper" + System.currentTimeMillis().toString()
                    innerPayload.put("orderId", orderId)

                    createJuspayOrder(orderId, object : JuspayCallback {
                        override fun onSuccess(token: String) {
                            innerPayload.put("clientAuthToken", token)
                            Log.d("THARUN (clientAuthToken): ", token)
                        }
                        override fun onError(e: Exception) {
                            println("Error: ${e.message}")
                        }
                    })
                }

                AuthorizationMethods.RSA -> {
                    signaturePayload.put("merchant_id", Creds.MERCHANT_ID)
                    signaturePayload.put("customer_id", Creds.CUSTOMER_ID)
                    signaturePayload.put("order_id", "hyper${System.currentTimeMillis()}")
                    signaturePayload.put("timestamp", System.currentTimeMillis().toString())

                    innerPayload.put("signature", getSignedData(signaturePayload.toString(), readPrivateString(this, authMethod)))
                    innerPayload.put("signaturePayload", signaturePayload.toString())
                }

                AuthorizationMethods.JWS -> {
                    innerPayload.put("merchantId", Creds.MERCHANT_ID)

                    signaturePayload.put("merchantId", Creds.PSP_MERCHANT_ID)
                    signaturePayload.put("merchantChannelId", Creds.PSP_MERCHANT_CHANNEL_ID)
                    signaturePayload.put("customerMobileNumber", "919677449189")
                    signaturePayload.put("merchantCustomerId", Creds.CUSTOMER_ID)
                    signaturePayload.put("merchantRequestId", "hyper${System.currentTimeMillis()}")
                    signaturePayload.put("timestamp", System.currentTimeMillis().toString())

                    val jwspayload = getJWSSignature(
                        signaturePayload.toString(),
                        readPrivateString(this, authMethod),
                        Creds.JWS_KID
                    ).split('.')

                    innerPayload.put("protected", jwspayload[0])
                    innerPayload.put("signaturePayload", jwspayload[1])
                    innerPayload.put("signature", jwspayload[2])
                    innerPayload.put("enableJwsAuth", true)
                }
            }

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

    private fun callUpdateAuth() {
        val processPayload = JSONObject()
        val innerPayload = JSONObject()
        val signaturePayload = JSONObject()

        try {
            innerPayload.put("action", "updateAuth")
            innerPayload.put("merchantKeyId", Creds.MERCHANT_KEY_ID)
            innerPayload.put("clientId", Creds.CLIENT_ID)
            innerPayload.put("environment", Creds.ENV)
            innerPayload.put("issuingPsp", Creds.ISSUING_PSP)

            when (authMethod) {
                AuthorizationMethods.CAT -> {
                    innerPayload.put("clientAuthToken", "tkn_493da38e22d3499cb2521bfc99e29284")
                    innerPayload.put("orderId", "hyper562004")
                }

                AuthorizationMethods.RSA -> {
                    signaturePayload.put("merchant_id", Creds.MERCHANT_ID)
                    signaturePayload.put("customer_id", Creds.CUSTOMER_ID)
                    signaturePayload.put("order_id", "hyper${System.currentTimeMillis()}")
                    signaturePayload.put("timestamp", System.currentTimeMillis().toString())

                    innerPayload.put("signature", getSignedData(signaturePayload.toString(), readPrivateString(this, authMethod)))
                    innerPayload.put("signaturePayload", signaturePayload.toString())
                }

                AuthorizationMethods.JWS -> {
                    innerPayload.put("merchantId", Creds.MERCHANT_ID)

                    signaturePayload.put("merchantId", Creds.PSP_MERCHANT_ID)
                    signaturePayload.put("merchantChannelId", Creds.PSP_MERCHANT_CHANNEL_ID)
                    signaturePayload.put("customerMobileNumber", "919677449189")
                    signaturePayload.put("merchantCustomerId", Creds.CUSTOMER_ID)
                    signaturePayload.put("merchantRequestId", "hyper${System.currentTimeMillis()}")
                    signaturePayload.put("timestamp", System.currentTimeMillis().toString())

                    val jwspayload = getJWSSignature(
                        signaturePayload.toString(),
                        readPrivateString(this, authMethod),
                        Creds.JWS_KID
                    ).split('.')

                    innerPayload.put("protected", jwspayload[0])
                    innerPayload.put("signaturePayload", jwspayload[1])
                    innerPayload.put("signature", jwspayload[2])
                    innerPayload.put("enableJwsAuth", true)
                }
            }

            processPayload.put("requestId", UUID.randomUUID().toString())
            processPayload.put("service", "in.juspay.ec")
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

    private fun callTerminate() {
        hyperServicesHolder?.terminate()
        Toast.makeText(this@IntegrationActivity, "Terminated Successfully", Toast.LENGTH_LONG).show()

        binding.btnInitiate.isEnabled = true
        binding.btnTerminate.isEnabled = false
        binding.btnProcess.isEnabled = false
        binding.btnIntent.isEnabled = false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (data != null) {
            Log.d("THARUN (result code)", resultCode.toString())
            Log.d("THARUN (requestCode)", requestCode.toString())
            Log.d("THARUN (intent result)", data.data.toString())
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onDestroy() {
        callTerminate()
        super.onDestroy()
    }


}