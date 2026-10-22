package com.cjstudio.manicure.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

// Um pagamento da assinatura — pagamentos/{id}, gravado só pela Cloud
// Function confirmarCompraManicure.
data class Pagamento(
    var id: String? = null,
    var uid: String? = null,
    var produto: String? = null,
    // "plano" | "extra"
    var tipo: String? = null,
    var plano: String? = null,
    var valor: Double = 0.0,
    var inicio: Date? = null,
    var fim: Date? = null,
    @ServerTimestamp
    var criadoEm: Date? = null
)

sealed interface ResultadoCompra {
    data object Liberada : ResultadoCompra
    // Pix/boleto ainda não compensou: o app confere de novo depois.
    data object Pendente : ResultadoCompra
}

// Pagamentos da profissional: confirma no servidor a compra feita no Google
// Play e lista o histórico.
interface IPagamentoRepository {
    suspend fun confirmarCompra(purchaseToken: String, productId: String): Result<ResultadoCompra>
    suspend fun meusPagamentos(): Result<List<Pagamento>>
}

@Singleton
class PagamentoRepository @Inject constructor(
    private val db: Provider<FirebaseFirestore>,
    private val functions: Provider<FirebaseFunctions>,
    private val auth: IAuthRepository
) : IPagamentoRepository {

    override suspend fun confirmarCompra(purchaseToken: String, productId: String): Result<ResultadoCompra> = runCatching {
        val resposta = try {
            etapa("confirmar compra", limiteMs = 60_000) {
                functions.get().getHttpsCallable("confirmarCompraManicure")
                    .call(mapOf("purchaseToken" to purchaseToken, "productId" to productId)).await()
            }
        } catch (e: FirebaseFunctionsException) {
            throw IllegalStateException(e.message ?: "Não foi possível confirmar o pagamento.")
        }
        val dados = resposta.getData() as? Map<*, *>
        if (dados?.get("pendente") == true) ResultadoCompra.Pendente else ResultadoCompra.Liberada
    }

    override suspend fun meusPagamentos(): Result<List<Pagamento>> = runCatching {
        val uid = auth.uidLogado() ?: throw IllegalStateException("Não há sessão ativa.")
        etapa("ler pagamentos") { db.get().collection("pagamentos").whereEqualTo("uid", uid).get().await() }
            .documents.mapNotNull { d -> d.toObject(Pagamento::class.java)?.also { it.id = d.id } }
            .sortedByDescending { it.criadoEm ?: it.inicio }
    }
}
