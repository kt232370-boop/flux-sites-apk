package com.example.data.billing

import android.content.Context
import android.content.Intent
import com.google.android.gms.tasks.Task
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

sealed interface GooglePayAvailability {
    data object Checking : GooglePayAvailability
    data object Ready : GooglePayAvailability
    data class Unavailable(val reason: String) : GooglePayAvailability
}

data class GooglePayReceipt(
    val orderId: String,
    val paymentNetwork: String,
    val tokenSummary: String,
)

class GooglePayManager(context: Context) {

    private val paymentsClient: PaymentsClient = Wallet.getPaymentsClient(
        context.applicationContext,
        Wallet.WalletOptions.Builder()
            .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
            .build()
    )

    private val baseRequestJson: JSONObject
        get() = JSONObject()
            .put("apiVersion", 2)
            .put("apiVersionMinor", 0)

    private val allowedCardAuthMethods = JSONArray(listOf("PAN_ONLY", "CRYPTOGRAM_3DS"))
    private val allowedCardNetworks = JSONArray(listOf("AMEX", "DISCOVER", "MASTERCARD", "VISA"))

    private fun baseCardPaymentMethod(): JSONObject = JSONObject()
        .put("type", "CARD")
        .put(
            "parameters",
            JSONObject()
                .put("allowedAuthMethods", allowedCardAuthMethods)
                .put("allowedCardNetworks", allowedCardNetworks)
        )

    private fun cardPaymentMethod(): JSONObject = baseCardPaymentMethod().put(
        "tokenizationSpecification",
        JSONObject()
            .put("type", "PAYMENT_GATEWAY")
            .put(
                "parameters",
                JSONObject()
                    .put("gateway", "example")
                    .put("gatewayMerchantId", "exampleGatewayMerchantId")
            )
    )

    suspend fun checkReadiness(): GooglePayAvailability {
        return try {
            val requestJson = baseRequestJson.put(
                "allowedPaymentMethods",
                JSONArray().put(baseCardPaymentMethod())
            )
            val request = IsReadyToPayRequest.fromJson(requestJson.toString())
            val ready = paymentsClient.isReadyToPay(request).await()
            if (ready) {
                GooglePayAvailability.Ready
            } else {
                GooglePayAvailability.Unavailable("Google Pay is not configured on this device.")
            }
        } catch (e: Exception) {
            GooglePayAvailability.Unavailable(
                e.localizedMessage ?: "Google Pay service unavailable in this environment."
            )
        }
    }

    fun createLoadPaymentDataTask(
        priceUsdFormatted: String,
        planLabel: String,
    ): Task<PaymentData> {
        val paymentDataRequestJson = baseRequestJson
            .put("allowedPaymentMethods", JSONArray().put(cardPaymentMethod()))
            .put(
                "transactionInfo",
                JSONObject()
                    .put("totalPrice", priceUsdFormatted)
                    .put("totalPriceStatus", "FINAL")
                    .put("countryCode", "US")
                    .put("currencyCode", "USD")
                    .put("checkoutOption", "COMPLETE_IMMEDIATE_PURCHASE")
            )
            .put(
                "merchantInfo",
                JSONObject()
                    .put("merchantName", "Flux Sites Hosting ($planLabel)")
            )

        val request = PaymentDataRequest.fromJson(paymentDataRequestJson.toString())
        return paymentsClient.loadPaymentData(request)
    }

    fun parsePaymentResult(data: Intent?): Result<GooglePayReceipt> {
        if (data == null) {
            return Result.failure(IllegalStateException("Google Pay returned empty result intent."))
        }
        val paymentData = PaymentData.getFromIntent(data)
            ?: return Result.failure(IllegalStateException("PaymentData missing from Google Pay result."))
        return parsePaymentData(paymentData)
    }

    fun parsePaymentData(paymentData: PaymentData): Result<GooglePayReceipt> {
        return try {
            val json = JSONObject(paymentData.toJson())
            val paymentMethodData = json.getJSONObject("paymentMethodData")
            val info = paymentMethodData.optJSONObject("info")
            val cardNetwork = info?.optString("cardNetwork", "CARD") ?: "CARD"
            val cardDetails = info?.optString("cardDetails", "••••") ?: "••••"
            val tokenRaw = paymentMethodData
                .optJSONObject("tokenizationData")
                ?.optString("token", "")
                .orEmpty()
            val tokenPreview = if (tokenRaw.isNotBlank()) {
                "gpay_tok_${tokenRaw.hashCode().toUInt().toString(16)}"
            } else {
                "gpay_tok_${UUID.randomUUID().toString().take(8)}"
            }
            val orderId = "gpay_ord_${UUID.randomUUID().toString().take(10)}"
            Result.success(
                GooglePayReceipt(
                    orderId = orderId,
                    paymentNetwork = "Google Pay • $cardNetwork $cardDetails",
                    tokenSummary = tokenPreview,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseErrorMessage(data: Intent?): String {
        val status = AutoResolveHelper.getStatusFromIntent(data)
        return status?.statusMessage ?: "Google Pay checkout failed (code ${status?.statusCode ?: -1})."
    }
}
