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
    var naoLidasCliente: Int = 0,
    // "Apagar conversa só para mim": uid -> quando apagou (ms). Mensagens até
    // esse momento somem só pra essa pessoa; a conversa volta pra lista dela
    // quando chegar mensagem nova.
    var apagadaPara: Map<String, Long> = emptyMap()
) {
    // Eu sou a profissional desta conversa?
    fun souProfissional(uid: String?) = profissionalId == uid

    fun nomeOutro(uid: String?) = if (souProfissional(uid)) clienteNome else profissionalNome
    fun naoLidasParaMim(uid: String?) = if (souProfissional(uid)) naoLidasProfissional else naoLidasCliente

    // Quando eu apaguei esta conversa só pra mim (0 = nunca).
    fun apagadaPorMimEm(uid: String?): Long = uid?.let { apagadaPara[it] } ?: 0L

    // Nada novo desde que apaguei só pra mim: some da minha lista.
    fun ocultaPara(uid: String?): Boolean {
        val apagada = apagadaPorMimEm(uid)
        return apagada > 0 && (ultimoTimestamp?.time ?: 0L) <= apagada
    }

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
    var lida: Boolean = false,
    // "Apagar para todos" (só quem mandou): o texto some e fica o aviso.
    var apagada: Boolean = false,
    // "Apagar para mim": quem está aqui não vê mais a mensagem.
    var ocultaPara: List<String> = emptyList()
)
