package com.cjstudio.manicure.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.data.model.Conversa
import com.cjstudio.manicure.data.model.Mensagem
import com.cjstudio.manicure.data.repository.IAuthRepository
import com.cjstudio.manicure.data.repository.IChatRepository
import com.cjstudio.manicure.ui.common.UiTexto
import com.cjstudio.manicure.ui.common.paraUiTexto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Uma conversa aberta. Chega pelo id (notificação, lista de conversas) ou
// pelos dados da dupla (botão Chat no perfil / no horário marcado), e aí
// cria a conversa se for a primeira vez.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chat: IChatRepository,
    private val auth: IAuthRepository,
    private val estado: SavedStateHandle
) : ViewModel() {

    val meuId: String? = auth.uidLogado()

    private val conversaId = MutableStateFlow<String?>(estado[ChatActivity.EXTRA_CONVERSA])

    // Resumo da conversa ao vivo (nome do outro lado, contadores).
    val conversa: StateFlow<Conversa?> = conversaId.filterNotNull()
        .flatMapLatest { chat.escutarConversa(it).catch { emit(null) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val mensagens: StateFlow<List<Mensagem>> = conversaId.filterNotNull()
        .flatMapLatest { id -> chat.escutarMensagens(id).catch { emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Id da conversa já resolvido (a tela usa pra não notificar a conversa aberta).
    val idAtual: StateFlow<String?> = conversaId

    private val _erros = Channel<UiTexto>(Channel.BUFFERED)
    val erros: Flow<UiTexto> = _erros.receiveAsFlow()

    init {
        if (conversaId.value == null) abrirPelaDupla()
        // Chegou mensagem nova com a tela aberta: já marca como lida.
        viewModelScope.launch {
            mensagens.map { lista -> lista.any { it.destinatarioId == meuId && !it.lida } }.collect { temNaoLida ->
                if (temNaoLida) conversa.value?.let { chat.marcarComoLida(it) }
            }
        }
    }

    private fun abrirPelaDupla() {
        val profissionalId: String = estado[ChatActivity.EXTRA_PROFISSIONAL_ID] ?: return
        val clienteId: String = estado[ChatActivity.EXTRA_CLIENTE_ID] ?: return
        viewModelScope.launch {
            chat.abrirConversa(
                profissionalId = profissionalId,
                profissionalNome = estado[ChatActivity.EXTRA_PROFISSIONAL_NOME] ?: "",
                profissionalFotoUrl = estado[ChatActivity.EXTRA_PROFISSIONAL_FOTO],
                clienteId = clienteId,
                clienteNome = estado[ChatActivity.EXTRA_CLIENTE_NOME] ?: ""
            ).onSuccess {
                estado[ChatActivity.EXTRA_CONVERSA] = it.id
                conversaId.value = it.id
            }.onFailure { _erros.send(it.paraUiTexto()) }
        }
    }

    fun enviar(texto: String) {
        val c = conversa.value ?: return
        val limpo = texto.trim()
        if (limpo.isEmpty()) return
        val meuNome = (if (c.souProfissional(meuId)) c.profissionalNome else c.clienteNome).orEmpty()
        viewModelScope.launch {
            chat.enviar(c, limpo, meuNome).onFailure { _erros.send(it.paraUiTexto()) }
        }
    }

    fun marcarComoLida() {
        val c = conversa.value ?: return
        if (c.naoLidasParaMim(meuId) > 0) viewModelScope.launch { chat.marcarComoLida(c) }
    }
}
