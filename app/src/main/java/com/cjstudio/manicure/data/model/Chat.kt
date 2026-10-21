package com.cjstudio.manicure.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Conversa entre uma profissional e uma cliente — conversas/{profissionalId_clienteId}.
// Guarda o resumo (última mensagem, não lidas de cada lado) pra montar a
// lista e o contador do ícone de chat sem ler as mensagens. Mesmo modelo do
// chat do Caronas.
data class Conversa(
    @get:Exclude @set:Exclude
    var id: String? = null,

    var profissionalId: String? = null,
    var profissionalNome: String? = null,
    var profissionalFotoUrl: String? = null,
    var clienteId: String? = null,
    var clienteNome: String? = null,
    // [profissionalId, clienteId] — usado na consulta e nas regras.
    var participantes: List<String> = emptyList(),

    var ultimaMensagem: String? = null,
    @ServerTimestamp
    var ultimoTimestamp: Date? = null,
    var ultimoRemetenteId: String? = null,
    var naoLidasProfissional: Int = 0,
    var naoLidasCliente: Int = 0
) {
    // Eu sou a profissional desta conversa?
    fun souProfissional(uid: String?) = profissionalId == uid

    fun nomeOutro(uid: String?) = if (souProfissional(uid)) clienteNome else profissionalNome
    fun naoLidasParaMim(uid: String?) = if (souProfissional(uid)) naoLidasProfissional else naoLidasCliente

    companion object {
        fun idDe(profissionalId: String, clienteId: String) = "${profissionalId}_$clienteId"
    }
}

// Mensagem — conversas/{id}/mensagens/{mensagemId}.
data class Mensagem(
    @get:Exclude @set:Exclude
    var id: String? = null,

    var remetenteId: String? = null,
    var destinatarioId: String? = null,
    var remetenteNome: String? = null,
    var conteudo: String? = null,
    @ServerTimestamp
    var timestamp: Date? = null,
    var lida: Boolean = false
)
