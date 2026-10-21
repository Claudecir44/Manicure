package com.cjstudio.manicure.data.repository

import com.cjstudio.manicure.data.model.Conversa
import com.cjstudio.manicure.data.model.Mensagem
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

// Chat profissional <-> cliente. Uma conversa por dupla (id fixo
// profissionalId_clienteId), mensagens numa subcoleção — mesmo desenho do
// Caronas (consulta pelo caminho, que as regras conseguem validar).
interface IChatRepository {
    // Abre (ou cria) a conversa da dupla. Nomes/foto atualizam o resumo.
    suspend fun abrirConversa(
        profissionalId: String,
        profissionalNome: String,
        profissionalFotoUrl: String?,
        clienteId: String,
        clienteNome: String
    ): Result<Conversa>

    suspend fun buscarConversa(conversaId: String): Result<Conversa?>
    fun escutarConversa(conversaId: String): Flow<Conversa?>
    fun escutarMensagens(conversaId: String): Flow<List<Mensagem>>
    suspend fun enviar(conversa: Conversa, texto: String, meuNome: String): Result<Unit>
    suspend fun marcarComoLida(conversa: Conversa): Result<Unit>

    // Todas as conversas em que estou (como profissional e/ou cliente).
    fun escutarMinhasConversas(): Flow<List<Conversa>>

    // Mensagens não lidas: como profissional e como cliente (ícone de chat e do app).
    fun escutarNaoLidas(): Flow<NaoLidas>
}

data class NaoLidas(val comoProfissional: Int = 0, val comoCliente: Int = 0) {
    val total get() = comoProfissional + comoCliente
}

@Singleton
class ChatRepository @Inject constructor(
    private val db: Provider<FirebaseFirestore>,
    private val auth: IAuthRepository
) : IChatRepository {

    private fun conversas() = db.get().collection("conversas")
    private fun mensagens(conversaId: String) = conversas().document(conversaId).collection("mensagens")
    private fun uid() = auth.uidLogado() ?: throw IllegalStateException("Não há sessão ativa.")

    override suspend fun abrirConversa(
        profissionalId: String,
        profissionalNome: String,
        profissionalFotoUrl: String?,
        clienteId: String,
        clienteNome: String
    ): Result<Conversa> = runCatching {
        val id = Conversa.idDe(profissionalId, clienteId)
        val ref = conversas().document(id)
        val existente = etapa("abrir conversa") { ref.get().await() }
        if (existente.exists()) {
            return@runCatching existente.toObject(Conversa::class.java)!!.also { it.id = id }
        }
        val nova = Conversa(
            profissionalId = profissionalId,
            profissionalNome = profissionalNome,
            profissionalFotoUrl = profissionalFotoUrl,
            clienteId = clienteId,
            clienteNome = clienteNome,
            participantes = listOf(profissionalId, clienteId)
        )
        etapa("criar conversa") { ref.set(nova).await() }
        nova.also { it.id = id }
    }

    override suspend fun buscarConversa(conversaId: String): Result<Conversa?> = runCatching {
        val doc = etapa("ler conversa") { conversas().document(conversaId).get().await() }
        doc.toObject(Conversa::class.java)?.also { it.id = doc.id }
    }

    override fun escutarConversa(conversaId: String): Flow<Conversa?> = callbackFlow {
        val registro = conversas().document(conversaId).addSnapshotListener { doc, erro ->
            if (erro != null) {
                close(erro)
                return@addSnapshotListener
            }
            trySend(doc?.toObject(Conversa::class.java)?.also { it.id = doc.id })
        }
        awaitClose { registro.remove() }
    }

    override fun escutarMensagens(conversaId: String): Flow<List<Mensagem>> = callbackFlow {
        val registro = mensagens(conversaId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, erro ->
                if (erro != null) {
                    close(erro)
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { d -> d.toObject(Mensagem::class.java)?.also { it.id = d.id } }.orEmpty())
            }
        awaitClose { registro.remove() }
    }

    // Mensagem + resumo da conversa num lote (soma 1 nas não lidas de quem recebe).
    override suspend fun enviar(conversa: Conversa, texto: String, meuNome: String): Result<Unit> = runCatching {
        val eu = uid()
        val conversaId = conversa.id ?: throw IllegalStateException("Conversa sem id.")
        val souProfissional = conversa.souProfissional(eu)
        val destinatario = if (souProfissional) conversa.clienteId else conversa.profissionalId
        val lote = db.get().batch()
        lote.set(
            mensagens(conversaId).document(),
            Mensagem(remetenteId = eu, destinatarioId = destinatario, remetenteNome = meuNome, conteudo = texto)
        )
        lote.set(
            conversas().document(conversaId),
            mapOf(
                "ultimaMensagem" to texto,
                "ultimoTimestamp" to FieldValue.serverTimestamp(),
                "ultimoRemetenteId" to eu,
                (if (souProfissional) "naoLidasCliente" else "naoLidasProfissional") to FieldValue.increment(1)
            ),
            SetOptions.merge()
        )
        etapa("enviar mensagem") { lote.commit().await() }
        Unit
    }

    // Zera o meu contador e marca as mensagens recebidas como lidas
    // (alimenta o "Lida" embaixo das mensagens de quem mandou).
    override suspend fun marcarComoLida(conversa: Conversa): Result<Unit> = runCatching {
        val eu = uid()
        val conversaId = conversa.id ?: return@runCatching
        val naoLidas = mensagens(conversaId)
            .whereEqualTo("destinatarioId", eu)
            .whereEqualTo("lida", false)
            .get().await()
        val lote = db.get().batch()
        lote.update(conversas().document(conversaId), if (conversa.souProfissional(eu)) "naoLidasProfissional" else "naoLidasCliente", 0)
        naoLidas.documents.forEach { lote.update(it.reference, "lida", true) }
        lote.commit().await()
        Unit
    }

    override fun escutarMinhasConversas(): Flow<List<Conversa>> = callbackFlow {
        val eu = auth.uidLogado()
        if (eu == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registro = conversas().whereArrayContains("participantes", eu).addSnapshotListener { snap, erro ->
            if (erro != null) {
                close(erro)
                return@addSnapshotListener
            }
            trySend(
                snap?.documents?.mapNotNull { d -> d.toObject(Conversa::class.java)?.also { it.id = d.id } }.orEmpty()
                    // Conversa aberta sem nenhuma mensagem não aparece na lista.
                    .filter { it.ultimaMensagem != null }
                    .sortedByDescending { it.ultimoTimestamp?.time ?: 0L }
            )
        }
        awaitClose { registro.remove() }
    }

    override fun escutarNaoLidas(): Flow<NaoLidas> = escutarMinhasConversas().map { lista ->
        val eu = auth.uidLogado()
        NaoLidas(
            comoProfissional = lista.filter { it.souProfissional(eu) }.sumOf { it.naoLidasProfissional },
            comoCliente = lista.filter { !it.souProfissional(eu) }.sumOf { it.naoLidasCliente }
        )
    }.catch { emit(NaoLidas()) }
}
