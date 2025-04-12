package com.tharunbalaji.nammapay.utils

import android.content.Context
import android.util.Base64
import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.RSASSASigner
import com.tharunbalaji.nammapay.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.UUID
import kotlin.math.min

fun getUpiRequestId(): String {
    try {
        val uuid = UUID.randomUUID().toString().replace("-", "")
        val generatedUpiRequestId = uuid.substring(0, min(uuid.length.toDouble(), 35.0).toInt())
        return generatedUpiRequestId
    } catch (e: java.lang.Exception) {
        return ""
    }
}

fun getCustomerInfo(): String? {
    val client = OkHttpClient()

    val url = "https://sandbox.juspay.in/customers/9677449189?options.get_client_auth_token=true"

    val request = Request.Builder()
        .url(url)
        .addHeader("x-merchantid", "hyperupi")
        .addHeader("x-routing-id", "9677449189")
        .addHeader("Authorization", "Basic MTZBNERGRTIzREE0OTdCOTk5RkM5NUE0NTI4MDE3Og==")
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            println("Request failed: ${response.code}")
            return null
        }
        return response.body?.string()
    }
}

@Throws(Exception::class)
fun getSignedData(plainText: String, privateKeyStr: String): String {
    val privateKey = getPrivateKeyFromString(privateKeyStr) as RSAPrivateKey
    val signature = Signature.getInstance("SHA256withRSA").apply {
        initSign(privateKey)
        update(plainText.toByteArray(Charsets.UTF_8))
    }.sign()
    return Base64.encodeToString(signature, Base64.DEFAULT)
}

@Throws(Exception::class)
fun getJWSSignature(plainText: String, privateKeyStr: String, kid: String): String {
    val privateKey = getPrivateKeyFromString(privateKeyStr) as RSAPrivateKey
    val jwsObject = JWSObject(
        JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(),
        Payload(plainText)
    ).apply {
        sign(RSASSASigner(privateKey))
    }
    return jwsObject.serialize()
}

@Throws(Exception::class)
fun getPrivateKeyFromString(keyString: String): PrivateKey {
    val cleanedKey = keyString
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("\\s".toRegex(), "")
    val keyBytes = Base64.decode(cleanedKey, Base64.DEFAULT)
    return KeyFactory.getInstance("RSA")
        .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
}

enum class AuthorizationMethods {
    CAT, RSA, JWS
}

fun readPrivateString(context: Context, authMethod: AuthorizationMethods): String {
    val resourceId = when (authMethod) {
        AuthorizationMethods.CAT, AuthorizationMethods.RSA -> R.raw.private_key
        AuthorizationMethods.JWS -> R.raw.private_key_jws
    }
    return context.resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
}

interface JuspayCallback {
    fun onSuccess(token: String)
    fun onError(e: Exception)
}
