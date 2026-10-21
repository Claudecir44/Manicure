package com.cjstudio.manicure.ui.cliente

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Cliente
import com.cjstudio.manicure.data.repository.IClienteRepository
import com.cjstudio.manicure.ui.common.UiTexto
import com.cjstudio.manicure.ui.common.paraUiTexto
import com.cjstudio.manicure.ui.common.texto
import com.cjstudio.manicure.util.Validacao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Meu perfil da cliente: nome e telefone (gravados no Salvar) e a foto
// (gravada assim que ela escolhe).
@HiltViewModel
class PerfilClienteViewModel @Inject constructor(
    private val clientes: IClienteRepository
) : ViewModel() {

    sealed interface Evento {
        data object Salvo : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    private val _cliente = MutableStateFlow<Cliente?>(null)
    val cliente: StateFlow<Cliente?> = _cliente

    private val _ocupado = MutableStateFlow(false)
    val ocupado: StateFlow<Boolean> = _ocupado

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    init {
        viewModelScope.launch {
            clientes.buscarLogada()
                .onSuccess { _cliente.value = it }
                .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }

    fun trocarFoto(uri: Uri) {
        if (_ocupado.value) return
        _ocupado.value = true
        viewModelScope.launch {
            clientes.trocarFoto(uri)
                .onSuccess { url ->
                    _cliente.value = _cliente.value?.copy(fotoUrl = url)
                    _eventos.send(Evento.Mensagem(texto(R.string.cliente_foto_salva)))
                }
                .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
            _ocupado.value = false
        }
    }

    fun salvar(nome: String, telefone: String) {
        if (_ocupado.value) return
        val erro = when {
            nome.trim().length < 3 -> texto(R.string.cadastro_erro_nome)
            !Validacao.telefone(telefone) -> texto(R.string.cadastro_erro_telefone)
            else -> null
        }
        if (erro != null) {
            _eventos.trySend(Evento.Mensagem(erro))
            return
        }
        _ocupado.value = true
        viewModelScope.launch {
            clientes.salvarPerfil(nome, telefone)
                .onSuccess {
                    _eventos.send(Evento.Mensagem(texto(R.string.cliente_perfil_salvo)))
                    _eventos.send(Evento.Salvo)
                }
                .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
            _ocupado.value = false
        }
    }
}
