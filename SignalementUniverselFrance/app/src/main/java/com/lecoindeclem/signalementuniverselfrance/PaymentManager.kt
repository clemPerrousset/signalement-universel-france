package com.lecoindeclem.signalementuniverselfrance

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.Calendar
import java.util.Locale

class PaymentManager(
    private val context: Context,
    private val productId: String = PRODUCT_ID_LIFETIME
) : PurchasesUpdatedListener {

    interface Listener {
        fun onPremiumStateChanged(isPremium: Boolean)
        fun onPurchaseFlowError(message: String)
        fun onPriceLoaded(price: String?)
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var listener: Listener? = null
    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun start() {
        if (billingClient != null) return
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    queryProductDetails()
                    restorePurchases()
                } else {
                    listener?.onPurchaseFlowError("Billing indisponible (${result.responseCode})")
                }
            }

            override fun onBillingServiceDisconnected() {
            }
        })
    }

    fun stop() {
        billingClient?.endConnection()
        billingClient = null
        productDetails = null
    }

    fun isPremium(): Boolean = prefs.getBoolean(KEY_IS_PREMIUM, false)

    fun getLastSentDayKey(): Int = prefs.getInt(KEY_LAST_SENT_DAY, 0)

    fun canSendToday(): Boolean {
        if (isPremium()) return true
        val today = todayKey()
        val lastDay = getLastSentDayKey()
        if (lastDay != today) return true
        val count = prefs.getInt(KEY_DAILY_COUNT, 0)
        return count < FREE_LIMIT
    }

    fun markSentToday() {
        val today = todayKey()
        val lastDay = getLastSentDayKey()
        var currentCount = prefs.getInt(KEY_DAILY_COUNT, 0)
        if (lastDay != today) {
            prefs.edit()
                .putInt(KEY_LAST_SENT_DAY, today)
                .putInt(KEY_DAILY_COUNT, 1) // C'est le 1er envoi du jour
                .apply()
        } else {
            prefs.edit()
                .putInt(KEY_DAILY_COUNT, currentCount + 1)
                .apply()
        }
    }

    fun millisUntilNextFreeSend(): Long {
        if (isPremium()) return 0L
        if (canSendToday()) return 0L

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val now = System.currentTimeMillis()
        val target = cal.timeInMillis
        return (target - now).coerceAtLeast(0L)
    }

    fun formatCountdown(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun restorePurchases() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.INAPP)
            .build()

        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingResponseCode.OK) return@queryPurchasesAsync
            handlePurchases(purchases)
        }
    }

    fun launchLifetimePurchase(activity: Activity) {
        val client = billingClient
        if (client == null || !client.isReady) {
            listener?.onPurchaseFlowError("Billing non prêt")
            return
        }

        val details = productDetails
        if (details == null) {
            listener?.onPurchaseFlowError("Produit introuvable (vérifie l’ID Play Console)")
            return
        }

        val offerToken = details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply {
                if (!offerToken.isNullOrBlank()) setOfferToken(offerToken)
            }
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val res = client.launchBillingFlow(activity, flowParams)
        if (res.responseCode != BillingResponseCode.OK) {
            listener?.onPurchaseFlowError("Achat annulé/échoué (${res.responseCode})")
        }
    }

    override fun onPurchasesUpdated(
        result: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (result.responseCode == BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
            return
        }

        if (result.responseCode == BillingResponseCode.USER_CANCELED) return
        listener?.onPurchaseFlowError("Erreur achat (${result.responseCode})")
    }

    private fun queryProductDetails() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        client.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode != BillingResponseCode.OK) {
                listener?.onPriceLoaded(null)
                return@queryProductDetailsAsync
            }
            productDetails = list.productDetailsList.firstOrNull()
            listener?.onPriceLoaded(getFormattedPriceOrNull())
        }
    }

    fun getFormattedPriceOrNull(): String? {
        val details = productDetails ?: return null
        val oneTime = details.oneTimePurchaseOfferDetails ?: return null
        return oneTime.formattedPrice
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        var premiumNow = isPremium()

        purchases.forEach { purchase ->
            if (!purchase.products.contains(productId)) return@forEach
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return@forEach

            premiumNow = true
            prefs.edit().putBoolean(KEY_IS_PREMIUM, true).apply()
            listener?.onPremiumStateChanged(true)

            if (!purchase.isAcknowledged) acknowledge(purchase)
        }

        if (!premiumNow) {
            val stored = isPremium()
            if (stored) return
            listener?.onPremiumStateChanged(false)
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val client = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        client.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingResponseCode.OK) return@acknowledgePurchase
        }
    }

    private fun todayKey(): Int {
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return (y * 10000) + (m * 100) + d
    }

    companion object {
        const val PRODUCT_ID_LIFETIME = "suf_unlimited_lifetime"
        const val FREE_LIMIT = 10
        private const val PREFS_NAME = "suf_prefs"
        private const val KEY_IS_PREMIUM = "suf_is_premium"
        private const val KEY_LAST_SENT_DAY = "suf_last_sent_day"
        private const val KEY_DAILY_COUNT = "suf_daily_count"
    }
}
