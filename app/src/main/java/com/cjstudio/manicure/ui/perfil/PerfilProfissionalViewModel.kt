package com.cjstudio.manicure.ui.perfil

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.model.Agendamento
import com.cjstudio.manicure.data.repository.IAgendaRepository
import com.cjstudio.manicure.data.repository.IAuthRepository
import com.cjstudio.manicure.data.repository.IClienteRepository
import com.cjstudio.manicure.data.repository.IPreferenciasRepository
import com.cjstudio.manicure.util.AgendaUtil
import com.cjstudio.manicure.data.repository.IProfissionalRepository
import com.cjstudio.manicure.ui.common.UiTexto
import com.cjstudio.manicure.ui.common.paraUiTexto
import com.cjstudio.manicure.ui.common.texto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Perfil público de uma manicure (o que a cliente vê; a própria
// profissional também abre pra conferir).
@HiltViewModel
class PerfilProfissionalViewModel @Inject constructor(
    private val profissionais: IProfissionalRepository,
    private val preferencias: IPreferenciasRepository,
    private val agendas: IAgendaRepository,
    private val clientes: IClienteRepository,
    auth: IAuthRepository,
    estado: SavedStateHandle
) : ViewModel() {

    private val id: String = estado[PerfilProfissionalActivity.EXTRA_ID] ?: ""

    private val _perfil = MutableStateFlow<Profissional?>(null)
    val perfil: StateFlow<Profissional?> = _perfil

    // Este perfil é o que abre direto quando a cliente entra no app.
    private val _preferido = MutableStateFlow(false)
    val preferido: StateFlow<Boolean> = _preferido

    // Categorias com agenda e horário livre (a cliente toca pra agendar).
    private val _categoriasComAgenda = MutableStateFlow<List<String>>(emptyList())
    val categoriasComAgenda: StateFlow<List<String>> = _categoriasComAgenda

    val meuId: String? = auth.uidLogado()

    // Lembrete: os próximos horários que EU marquei com esta profissional.
    private val _meusHorarios = MutableStateFlow<List<Agendamento>>(emptyList())
    val meusHorarios: StateFlow<List<Agendamento>> = _meusHorarios

    // Meu nome de cliente (vai na conversa do chat).
    private var meuNome: String = ""

    private val _mensagens = Channel<UiTexto>(Channel.BUFFERED)
    val mensagens: Flow<UiTexto> = _mensagens.receiveAsFlow()

    init {
        viewModelScope.launch { _preferido.value = preferencias.perfilFavorito() == id }
        atualizarAgendas()
        viewModelScope.launch { meuNome = clientes.buscarLogada().getOrNull()?.nome.orEmpty() }
        viewModelScope.launch {
            profissionais.buscar(id)
                .onSuccess { p ->
                    if (p == null) _mensagens.send(texto(R.string.perfil_nao_encontrado)) else _perfil.value = p
                }
                .onFailure { _mensagens.send(it.paraUiTexto()) }
        }
    }

    // Também no onResume: depois de marcar, um horário a menos.
    fun atualizarAgendas() {
        viewModelScope.launch {
            val agora = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())
            agendas.horariosDaCliente().onSuccess { lista ->
                _meusHorarios.value = lista.filter { it.profissionalId == id && (it.horario ?: "") >= agora }
            }
        }
        viewModelScope.launch {
            agendas.agendasDaProfissional(id).onSuccess { lista ->
                _categoriasComAgenda.value = lista
                    .filter { !AgendaUtil.mesPassado(it.mes) && AgendaUtil.diasLivres(it).isNotEmpty() }
                    .mapNotNull { it.categoria }
                    .distinct()
            }
        }
    }

    fun cancelarHorario(agendamento: Agendamento) {
        viewModelScope.launch {
            agendas.cancelarMeuHorario(agendamento)
                .onSuccess { _mensagens.send(texto(R.string.agendar_cancelado)) }
                .onFailure { _mensagens.send(it.paraUiTexto()) }
            atualizarAgendas()
        }
    }

    fun nomeParaChat(): String = meuNome

    // Só a dona do perfil: a cor que as clientes veem.
    fun salvarCor(chave: String) {
        val atual = _perfil.value ?: return
        if (atual.id != meuId) return
        viewModelScope.launch {
            profissionais.salvarCorPerfil(chave)
                .onSuccess {
                    _perfil.value = atual.copy(corPerfil = chave)
                    _mensagens.send(texto(R.string.aparencia_perfil_salva))
                }
                .onFailure { _mensagens.send(it.paraUiTexto()) }
        }
    }

    fun deixarDePreferir() {
        viewModelScope.launch {
            preferencias.definirPerfilFavorito(null)
            _preferido.value = false
            _mensagens.send(texto(R.string.perfil_preferido_removido))
        }
    }
}
