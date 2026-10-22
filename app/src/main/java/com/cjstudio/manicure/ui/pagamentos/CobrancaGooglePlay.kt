package com.cjstudio.manicure.ui.pagamentos

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

// Google Play Billing só no que a assinatura precisa — mesmo desenho do
// GooglePlayBillingManager do Caronas: produtos avulsos (consumíveis), um
// por plano e um pra agenda extra. A compra paga chega em aoComprar e a
// tela manda pro servidor (confirmarCompraManicure) liberar.
class CobrancaGooglePlay(
    context: Context,
    private val produtos: List<String>,
    private val aoComprar: (Purchase) -> Unit,
    private val aoErro: (String) -> Unit
) {
    private var conectado = false
    private val detalhes = mutableMapOf<String, ProductDetails>()

    private val ouvinte = PurchasesUpdatedListener { resultado, compras ->
        when (resultado.responseCode) {
            BillingClient.BillingResponseCode.OK -> compras?.forEach(aoComprar)
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> aoErro(resultado.debugMessage)
        }
    }

    private val cliente: BillingClient = BillingClient.newBuilder(context)
        .setListener(ouvinte)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun conectar(aoConectar: () -> Unit) {
        if (conectado) {
            aoConectar()
            return
        }
        cliente.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(resultado: BillingResult) {
                if (resultado.responseCode == BillingClient.BillingResponseCode.OK) {
                    carregarProdutos { conectado = true; aoConectar() }
                } else {
                    aoErro(resultado.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                conectado = false
            }
        })
    }

    private fun carregarProdutos(aoTerminar: () -> Unit) {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            produtos.map {
                QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build()
            }
        ).build()
        cliente.queryProductDetailsAsync(params) { resultado, lista ->
            if (resultado.responseCode == BillingClient.BillingResponseCode.OK) {
                detalhes.clear()
                lista.productDetailsList.forEach { detalhes[it.productId] = it }
                // Sem produtos: o app ainda não está no Play Console ou os
                // produtos não foram criados lá com estes ids.
                if (lista.productDetailsList.isEmpty()) Log.w(TAG, "Nenhum produto no Play Console: $produtos")
            }
            aoTerminar()
        }
    }

    // Preço do Google (já no formato da loja), se o produto existir lá.
    fun preco(produto: String): String? = detalhes[produto]?.oneTimePurchaseOfferDetails?.formattedPrice

    fun disponivel(produto: String) = detalhes.containsKey(produto)

    // uid vai como obfuscatedAccountId: o servidor confere que a compra é desta conta.
    fun comprar(activity: Activity, produto: String, uid: String): Boolean {
        val d = detalhes[produto] ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .setObfuscatedAccountId(uid)
            .build()
        val r = cliente.launchBillingFlow(activity, params)
        if (r.responseCode != BillingClient.BillingResponseCode.OK) aoErro(r.debugMessage)
        return true
    }

    // Compras pagas que ainda não foram liberadas (Pix compensado depois,
    // app fechado no meio). O servidor ignora as já processadas.
    fun reconferir() {
        if (!conectado) return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        cliente.queryPurchasesAsync(params) { resultado, compras ->
            if (resultado.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            compras.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach(aoComprar)
        }
    }

    fun encerrar() = cliente.endConnection()

    private companion object {
        const val TAG = "CobrancaGooglePlay"
    }
}
