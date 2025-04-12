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
import com.tharunbalaji.nammapay.utils.getPrivateKeyFromString
import com.tharunbalaji.nammapay.utils.getSignedData
import com.tharunbalaji.nammapay.utils.getUpiRequestId
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
    private val privateKey =
        "MIIEoQIBAAKCAQEAjJby83Zv4Wqmv22czpkiuwj0Ad9ukJ+zijhwjI4z89Mp8Oku\nC3hv7KsmBIlCMuzzzjZNGN/7C4pKt7juwOpCMG1M9cBzDkEfXqprToHzrbSCRbK5\nB/3ctW82wa6cPps39VURJrUDDckXaoFxyKzZDnItn9nNv5D7kn1ljFN7QwOaqukL\nnCLK7jKFYahDiivHJyhVFD5LtanQiEet2iuCI89CUcKbkqv8MuU2RVJmBFgH5aLv\nwMn5Za8NfuMwhrg68OuYEo7UPhME17J10/7mX6x0zjn7LNFJC3zYYjoeOoCZ48IT\nSwmQFRcp7uhaaFJk1/9Va1XYXmvgKkYII86/JQIDAQABAoIBAB06/UR1aYmanRTL\n+4BRApGUqPcCt4BGVBP27B+tKUwWqW+3a6Vi4xJ3+y2SRDtGXOKRE7KKTy31ENfm\nEW32xtA+yXOHEeTy0UzjbfAiwMFq+HL5V9M7ivoGJ4JZhY7Wwum1SB2eIQZquv2f\n8EJi/bYtMyM7K7YatNOeUtC8QrKJ7FXvIwFDkavR8rgoGY6ow8d3OoxQwuRkQ6nQ\ndoSNm5Rjlc4eanOcxTkiD+pAHTzW4GpRSxo75PNIvt/xsMaSjffXLbhQF43oaVvh\n6fDkaNT4fEChneu+l0A+qg2OSEqctJqYdD8+srDbBUGHenpKIgadvpyqxuiuZ7GH\ne5w+8LkCgYEAxiIWnq9SHuF41KHoYPrB3bWYCETCAj55GM1i8vwLU38nDR9oZCoR\n0bDN4ihAbAhMqOJHa6nBCKwoCT58WLqLh/4BF3XnR0Zy7d5YfCFDrx0aQm1sosSO\ninsq7FAagn24ZP3L/d5xw94TnuPfBINk+ExrewtO+MaBBC1RS5j9uu8CgYEAtaZ6\njpW+F6OY1uxpPe3L7/o0sP6eKDo4uF4K8A4bXr1SdR4h/kGTf+GCD2Hx7zhZur/X\nHJ3fqRWOCknI8DdtR9oh5YLOZ+sJzXirhFXp9vFiQ/E+Zhi971IY1vypZknlV5fl\nzDu6N8N5txIpdlN2V5oMXwuk9dkfkgD0Vt5pNysCgYEArgqDlCCtIjMs0JroZUff\nw8EgKyM6yH3YIdFIeeisikvHId/U8yeBP5DvSRnSfRNNQ4yA8DHNPrD6+iPJVqeG\nqY6VpuYKorFfg1Mspt0Rd2E5D+DO7Kt8Cmjm623x225T62KFLhuYE1WgJpJD9NL2\nfqWiRBNK63xzGBg2sRFS0EECgYBG9uHcQE3CKGx2UmePBQ5uEx5woxggeRZdmIfH\nXot8yJOlI39+OBoqlGveHJKKtUYAuh+Mk3SkNsKF7GtuxQiRUHt7kU2XtW/f8Kt5\nCKNdkNGl32JUOohBLZ58prp7NpU9Uh85WYAXdutfBN5j1pleAdWhcAgi747w2CSc\np0kNfwJ/VmkS9DZQita8tS44wZWyOcs08nbrS9BYwiLUbLOCAgoubni7/d7NvdnX\nhWm1Xv8wiifs9Xh7MBA65LUsjIpjpEscmOk7W+ckeKDZHc/1W3/htPy3U16BCLDg\n66WFq47Rbw1TUAE0trLuSirz3hP7agQmGCg/Lq0wD+/wvBVgLw=="
    private lateinit var binding: ActivityDeeplinkBinding

    override fun onStart() {
        super.onStart()
        hyperServicesHolder = HyperServiceHolder(this)
        hyperServicesHolder!!.setCallback(createHyperPaymentsCallbackAdapter())

        initiatePaymentsSDK()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

            // Ensure signaturePayload is properly formatted
            signaturePayload.put("merchant_id", "hyperupi")
            signaturePayload.put("customer_id", "9677449189")
            signaturePayload.put("timestamp", System.currentTimeMillis().toString())

//            innerPayload.put("signature", getSignedData(signaturePayload.toString(), getPrivateKeyFromString(privateKey)))

            // Convert signaturePayload to string explicitly
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

            val receivedData = data.query?.split("&")

            if (receivedData != null) {
                val payeeVpa = receivedData[0].split("=")[1]
                val payeeName = receivedData[1].split("=")[1]
                val amount = receivedData[2].split("=")[1] + ".00"

                callSendMoney(payeeVpa, payeeName, amount)
            }
        } else {
            Log.d("THARUN (DEEPLINK)", "No data received")
        }
    }

    private fun callSendMoney(payeeVpa: String, payeeName : String, amount: String) {
        val processPayload = JSONObject()
        val innerPayload = JSONObject()
        val signaturePayload = JSONObject()
        try {
            // Generating inner payload
            innerPayload.put("action", "upiSendMoney")
            innerPayload.put("payType", "P2P_PAY")
            innerPayload.put("customerVpa", "abc3269621@ypay")
            innerPayload.put("payeeVpa", payeeVpa)
            innerPayload.put("payeeName", payeeName)
            innerPayload.put("amount", amount)
            innerPayload.put("upiRequestId", getUpiRequestId())
            innerPayload.put("accountReferenceId", "A30eb985179a4491a6f615e9352af4")
            innerPayload.put("remarks", "Dummy Transaction")
            innerPayload.put("timestamp", System.currentTimeMillis().toString())
            innerPayload.put("mcc", "0000")
            innerPayload.put("initiationMode","00")


            // Ensure signaturePayload is properly formatted
            signaturePayload.put("merchant_id", "hyperupi")
            signaturePayload.put("customer_id", "9677449189")
            signaturePayload.put("timestamp", System.currentTimeMillis().toString())

//            innerPayload.put("signature", getSignedData(signaturePayload.toString(), getPrivateKeyFromString(privateKey)))

            innerPayload.put("signaturePayload", signaturePayload.toString())

            processPayload.put("requestId", UUID.randomUUID().toString())
            processPayload.put("service", "in.juspay.ec")
            processPayload.put("payload", innerPayload)

            Log.d("THARUN (Payload Debug)", processPayload.toString())  // Debug Log
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

}