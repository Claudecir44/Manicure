package com.cjstudio.manicure.ui.cliente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Cliente
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.repository.IAuthRepository
import com.cjstudio.manicure.data.repository.IClienteRepository
import com.cjstudio.manicure.data.repository.IFalhasRepository
import com.cjstudio.manicure.data.repository.IProfissionalRepository
import com.cjstudio.manicure.ui.common.UiTexto
import com.cjstudio.manicure.ui.common.paraUiTexto
import com.cjstudio.manicure.ui.common.texto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Tela da cliente: busca de manicures por nome/cidade/bairro ou pelo código
// que a profissional divulgou.
@HiltViewModel
class HomeClienteViewModel @Inject constructor(
    private val auth: IAuthRepository,
    private val clientes: IClienteRepository,
    private val profissionais: IProfissionalRepository,
    private val falhas: IFalhasRepository
) : ViewModel() {

    sealed interface Evento {
        data object Saiu : Evento
        data class AbrirPerfil(val id: String) : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    private val _cliente = MutableStateFlow<Cliente?>(null)
    val cliente: StateFlow<Cliente?> = _cliente

    private val _lista = MutableStateFlow<List<Profissional>>(emptyList())
    val lista: StateFlow<List<Profissional>> = _lista

    private val _carregando = MutableStateFlow(true)
    val carregando: StateFlow<Boolean> = _carregando

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    private val textoBusca = MutableStateFlow("")

    init {
        falhas.definirUsuario(auth.uidLogado())
        viewModelScope.launch { clientes.buscarLogada().onSuccess { _cliente.value = it } }
        buscarQuandoParar()
    }

    @OptIn(FlowPreview::class)
    private fun buscarQuandoParar() {
        viewModelScope.launch {
            textoBusca.debounce(300).collect { buscar(it) }
        }
    }

    fun digitar(texto: String) {
        textoBusca.value = texto
    }

    private suspend fun buscar(texto: String) {
        _carregando.value = true
        profissionais.listarPublicadas(texto)
            .onSuccess { _lista.value = it }
            .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        _carregando.value = false
    }

    fun buscarCodigo(codigo: String) {
        if (codigo.isBlank()) return
        viewModelScope.launch {
            profissionais.buscarPorCodigo(codigo)
                .onSuccess { p ->
                    val id = p?.id
                    _eventos.send(if (id != null) Evento.AbrirPerfil(id) else Evento.Mensagem(texto(R.string.cliente_codigo_nao_achado)))
                }
                .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }

    fun sair() {
        viewModelScope.launch {
            auth.sair()
            _eventos.send(Evento.Saiu)
        }
    }
}
