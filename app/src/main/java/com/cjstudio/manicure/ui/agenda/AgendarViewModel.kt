package com.cjstudio.manicure.ui.agenda

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Agenda
import com.cjstudio.manicure.data.model.Agendamento
import com.cjstudio.manicure.data.repository.IAgendaRepository
import com.cjstudio.manicure.data.repository.IAuthRepository
import com.cjstudio.manicure.data.repository.IClienteRepository
import com.cjstudio.manicure.ui.common.UiTexto
import com.cjstudio.manicure.ui.common.paraUiTexto
import com.cjstudio.manicure.ui.common.texto
import com.cjstudio.manicure.util.AgendaUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Cliente marcando horário numa categoria de uma profissional. Só aparecem
// os horários livres — nunca quem marcou os outros.
@HiltViewModel
class AgendarViewModel @Inject constructor(
    private val agendas: IAgendaRepository,
    private val clientes: IClienteRepository,
    private val auth: IAuthRepository,
    estado: SavedStateHandle
) : ViewModel() {

    val profissionalId: String = estado[AgendarActivity.EXTRA_PROFISSIONAL] ?: ""
    val profissionalNome: String = estado[AgendarActivity.EXTRA_NOME] ?: ""
    val categoria: String = estado[AgendarActivity.EXTRA_CATEGORIA] ?: ""

    data class Estado(
        val agendas: List<Agenda> = emptyList(),
        val agendaId: String? = null,
        val dias: List<AgendaUtil.Dia> = emptyList(),
        val dia: String? = null,
        val horario: AgendaUtil.Horario? = null,
        val carregando: Boolean = true,
        val salvando: Boolean = false
    ) {
        val agenda: Agenda? get() = agendas.firstOrNull { it.id == agendaId }
        val horariosDoDia: List<AgendaUtil.Horario> get() = dias.firstOrNull { it.data == dia }?.livres.orEmpty()
    }

    sealed interface Evento {
        data object Marcado : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    init {
        carregar()
    }

    private fun carregar(manterAgenda: String? = null) {
        viewModelScope.launch {
            _estado.update { it.copy(carregando = true) }
            agendas.agendasDaProfissional(profissionalId)
                .onSuccess { todas ->
                    // Só desta categoria, de meses que ainda não acabaram e com horário livre.
                    val lista = todas.filter {
                        it.categoria == categoria && !AgendaUtil.mesPassado(it.mes) && AgendaUtil.diasLivres(it).isNotEmpty()
                    }
                    _estado.update { it.copy(agendas = lista, carregando = false) }
                    escolherAgenda((lista.firstOrNull { a -> a.id == manterAgenda } ?: lista.firstOrNull())?.id)
                }
                .onFailure { e ->
                    _estado.update { it.copy(carregando = false) }
                    _eventos.send(Evento.Mensagem(e.paraUiTexto()))
                }
        }
    }

    fun escolherAgenda(id: String?) {
        val agenda = _estado.value.agendas.firstOrNull { it.id == id }
        val dias = agenda?.let { AgendaUtil.diasLivres(it) }.orEmpty()
        _estado.update { it.copy(agendaId = id, dias = dias, dia = dias.firstOrNull()?.data, horario = null) }
    }

    fun escolherDia(data: String) {
        _estado.update { it.copy(dia = data, horario = null) }
    }

    fun escolherHorario(chave: String) {
        _estado.update { e -> e.copy(horario = e.horariosDoDia.firstOrNull { it.chave == chave }) }
    }

    fun confirmar() {
        val e = _estado.value
        val agenda = e.agenda ?: return
        val horario = e.horario
        if (horario == null) {
            _eventos.trySend(Evento.Mensagem(texto(R.string.agendar_erro_horario)))
            return
        }
        if (e.salvando) return
        _estado.update { it.copy(salvando = true) }
        viewModelScope.launch {
            val cliente = clientes.buscarLogada().getOrNull()
            val r = agendas.reservar(
                Agendamento(
                    agendaId = agenda.id,
                    profissionalId = profissionalId,
                    profissionalNome = profissionalNome,
                    categoria = categoria,
                    atendente = agenda.atendente,
                    horario = horario.chave,
                    clienteNome = cliente?.nome ?: auth.emailLogado(),
                    clienteTelefone = cliente?.telefone
                )
            )
            _estado.update { it.copy(salvando = false) }
            r.onSuccess {
                _eventos.send(Evento.Marcado)
            }.onFailure {
                _eventos.send(Evento.Mensagem(it.paraUiTexto()))
                // Outra cliente pode ter pegado o horário: recarrega o que está livre.
                carregar(manterAgenda = agenda.id)
            }
        }
    }
}
