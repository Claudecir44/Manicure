package com.cjstudio.manicure.ui.cliente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Cliente
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.repository.Coordenadas
import com.cjstudio.manicure.data.repository.IAuthRepository
import com.cjstudio.manicure.data.repository.IClienteRepository
import com.cjstudio.manicure.data.repository.IFalhasRepository
import com.cjstudio.manicure.data.repository.ILocalizacaoRepository
import com.cjstudio.manicure.data.repository.IPreferenciasRepository
import com.cjstudio.manicure.data.repository.IProfissionalRepository
import com.cjstudio.manicure.ui.common.UiTexto
import com.cjstudio.manicure.ui.common.paraUiTexto
import com.cjstudio.manicure.ui.common.texto
import com.cjstudio.manicure.util.Distancia
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
// que a profissional divulgou, com as mais perto no topo. A cliente pode
// escolher um perfil pra abrir direto sempre que entrar no app.
@HiltViewModel
class HomeClienteViewModel @Inject constructor(
    private val auth: IAuthRepository,
    private val clientes: IClienteRepository,
    private val profissionais: IProfissionalRepository,
    private val localizacao: ILocalizacaoRepository,
    private val preferencias: IPreferenciasRepository,
    private val falhas: IFalhasRepository
) : ViewModel() {

    // Profissional na lista, com a distância até a cliente (null = sem
    // localização de um dos lados).
    data class ItemLista(val profissional: Profissional, val distanciaKm: Double?)

    sealed interface Evento {
        data object Saiu : Evento
        data class AbrirPerfil(val id: String) : Evento
        // Toque num perfil que ainda não é o preferido: perguntar se salva.
        data class PerguntarPreferido(val id: String, val nome: String) : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    private val _cliente = MutableStateFlow<Cliente?>(null)
    val cliente: StateFlow<Cliente?> = _cliente

    private val _lista = MutableStateFlow<List<ItemLista>>(emptyList())
    val lista: StateFlow<List<ItemLista>> = _lista

    private val _carregando = MutableStateFlow(true)
    val carregando: StateFlow<Boolean> = _carregando

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    private val textoBusca = MutableStateFlow("")
    private var posicao: Coordenadas? = null
    private var preferido: String? = null

    init {
        falhas.definirUsuario(auth.uidLogado())
        viewModelScope.launch { clientes.buscarLogada().onSuccess { _cliente.value = it } }
        viewModelScope.launch {
            // Uma vez por abertura do app: vai direto pro perfil preferido.
            preferido = preferencias.perfilFavorito()
            preferido?.let { _eventos.send(Evento.AbrirPerfil(it)) }
        }
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

    // Chamado quando a permissão de localização chega (ou já existia).
    fun localizacaoLiberada() {
        viewModelScope.launch {
            posicao = localizacao.posicaoAtual() ?: return@launch
            buscar(textoBusca.value)
        }
    }

    private suspend fun buscar(texto: String) {
        _carregando.value = true
        profissionais.listarPublicadas(texto)
            .onSuccess { lista -> _lista.value = ordenar(lista) }
            .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        _carregando.value = false
    }

    // Mais perto primeiro; quem não tem localização vai pro fim, por nome.
    private fun ordenar(lista: List<Profissional>): List<ItemLista> {
        val aqui = posicao
        return lista.map { p ->
            val lat = p.latitude
            val lon = p.longitude
            ItemLista(p, if (aqui != null && lat != null && lon != null) Distancia.km(aqui, Coordenadas(lat, lon)) else null)
        }.sortedWith(compareBy<ItemLista> { it.distanciaKm ?: Double.MAX_VALUE }.thenBy { it.profissional.nomeExibicao.orEmpty().lowercase() })
    }

    fun tocou(p: Profissional) {
        val id = p.id ?: return
        _eventos.trySend(
            if (id == preferido) Evento.AbrirPerfil(id) else Evento.PerguntarPreferido(id, p.nomeExibicao.orEmpty())
        )
    }

    fun abrir(id: String, salvarPreferido: Boolean) {
        viewModelScope.launch {
            if (salvarPreferido) {
                preferencias.definirPerfilFavorito(id)
                preferido = id
            }
            _eventos.send(Evento.AbrirPerfil(id))
        }
    }

    fun buscarCodigo(codigo: String) {
        if (codigo.isBlank()) return
        viewModelScope.launch {
            profissionais.buscarPorCodigo(codigo)
                .onSuccess { p ->
                    if (p?.id != null) tocou(p) else _eventos.send(Evento.Mensagem(texto(R.string.cliente_codigo_nao_achado)))
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
