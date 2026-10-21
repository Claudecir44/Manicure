package com.cjstudio.manicure.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Agenda
import com.cjstudio.manicure.data.model.Agendamento
import com.cjstudio.manicure.data.repository.IAgendaRepository
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

// "Minha agenda": as agendas mensais de cada categoria e todos os horários
// marcados pelas clientes (só a profissional vê quem marcou).
@HiltViewModel
class AgendaProfissionalViewModel @Inject constructor(
    private val agendas: IAgendaRepository
) : ViewModel() {

    data class Estado(
        val agendas: List<Agenda> = emptyList(),
        val agendamentos: List<Agendamento> = emptyList(),
        val carregando: Boolean = true
    )

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado

    private val _mensagens = Channel<UiTexto>(Channel.BUFFERED)
    val mensagens: Flow<UiTexto> = _mensagens.receiveAsFlow()

    // Chamado no onResume (volta da criação/edição de uma agenda).
    fun atualizar() {
        viewModelScope.launch {
            _estado.value = _estado.value.copy(carregando = true)
            val a = agendas.minhasAgendas()
            val m = agendas.meusAgendamentos()
            a.exceptionOrNull()?.let { _mensagens.send(it.paraUiTexto()) }
            m.exceptionOrNull()?.let { _mensagens.send(it.paraUiTexto()) }
            _estado.value = Estado(a.getOrDefault(emptyList()), m.getOrDefault(emptyList()), carregando = false)
        }
    }

    fun excluir(agenda: Agenda) {
        viewModelScope.launch {
            agendas.excluirAgenda(agenda)
                .onSuccess { _mensagens.send(texto(R.string.agenda_excluida)) }
                .onFailure { _mensagens.send(it.paraUiTexto()) }
            atualizar()
        }
    }

    fun cancelar(agendamento: Agendamento) {
        viewModelScope.launch {
            agendas.cancelarAgendamento(agendamento)
                .onSuccess { _mensagens.send(texto(R.string.agenda_agendamento_cancelado)) }
                .onFailure { _mensagens.send(it.paraUiTexto()) }
            atualizar()
        }
    }
}
