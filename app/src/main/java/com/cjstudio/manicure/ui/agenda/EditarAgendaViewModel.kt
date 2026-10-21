package com.cjstudio.manicure.ui.agenda

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Agenda
import com.cjstudio.manicure.data.model.Categorias
import com.cjstudio.manicure.data.repository.IAgendaRepository
import com.cjstudio.manicure.data.repository.IProfissionalRepository
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
import kotlinx.coroutines.launch
import javax.inject.Inject

// Criar ou editar uma agenda mensal: categoria, mês, quem atende, dias da
// semana e horários de cada dia.
@HiltViewModel
class EditarAgendaViewModel @Inject constructor(
    private val agendas: IAgendaRepository,
    private val profissionais: IProfissionalRepository,
    estado: SavedStateHandle
) : ViewModel() {

    private val id: String? = estado[EditarAgendaActivity.EXTRA_ID]
    val editando = id != null

    // Dados pra montar a tela (categorias da profissional, meses, agenda em edição).
    data class Inicio(
        val categorias: List<Pair<String, String>>,
        val meses: List<String>,
        val nomeProfissional: String,
        val agenda: Agenda?
    )

    sealed interface Evento {
        data object Salvo : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    private val _inicio = MutableStateFlow<Inicio?>(null)
    val inicio: StateFlow<Inicio?> = _inicio

    // Horários de cada dia (a tela mostra como chips removíveis).
    private val _horarios = MutableStateFlow<List<String>>(emptyList())
    val horarios: StateFlow<List<String>> = _horarios

    private val _salvando = MutableStateFlow(false)
    val salvando: StateFlow<Boolean> = _salvando

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    init {
        viewModelScope.launch {
            val perfil = profissionais.buscarLogada().getOrNull()
            val agenda = id?.let { agendaId -> agendas.minhasAgendas().getOrNull()?.firstOrNull { it.id == agendaId } }
            // Sem categoria marcada no painel, oferece todas.
            val minhas = perfil?.categorias.orEmpty()
            val categorias = Categorias.TODAS.filter { minhas.isEmpty() || it.first in minhas || it.first == agenda?.categoria }
            val meses = (AgendaUtil.proximosMeses(4) + listOfNotNull(agenda?.mes)).distinct().sorted()
            _horarios.value = agenda?.horarios.orEmpty()
            _inicio.value = Inicio(categorias, meses, perfil?.nomeResponsavel?.ifBlank { null } ?: perfil?.nomeExibicao.orEmpty(), agenda)
        }
    }

    fun gerarHorarios(inicio: String, fim: String, intervalo: String) {
        val lista = AgendaUtil.gerarHorarios(inicio, fim, intervalo.trim().toIntOrNull() ?: 0)
        if (lista.isEmpty()) {
            _eventos.trySend(Evento.Mensagem(texto(R.string.agenda_erro_gerar)))
            return
        }
        _horarios.value = lista
    }

    fun adicionarHorario(hora: String) {
        val m = AgendaUtil.minutos(hora)
        if (m == null) {
            _eventos.trySend(Evento.Mensagem(texto(R.string.agenda_erro_hora)))
            return
        }
        val formatado = "%02d:%02d".format(m / 60, m % 60)
        _horarios.value = (_horarios.value + formatado).distinct().sortedBy { AgendaUtil.minutos(it) }
    }

    fun removerHorario(hora: String) {
        _horarios.value = _horarios.value - hora
    }

    fun salvar(categoria: String?, mes: String?, atendente: String, dias: List<Int>) {
        if (_salvando.value) return
        val erro = when {
            categoria == null -> R.string.agenda_erro_categoria
            mes == null -> R.string.agenda_erro_mes
            atendente.isBlank() -> R.string.agenda_erro_atendente
            dias.isEmpty() -> R.string.agenda_erro_dias
            _horarios.value.isEmpty() -> R.string.agenda_erro_horarios
            else -> null
        }
        if (erro != null) {
            _eventos.trySend(Evento.Mensagem(texto(erro)))
            return
        }
        val agenda = (_inicio.value?.agenda ?: Agenda()).copy(
            categoria = categoria,
            mes = mes,
            atendente = atendente.trim(),
            diasSemana = dias,
            horarios = _horarios.value
        ).also { it.id = id }
        _salvando.value = true
        viewModelScope.launch {
            val r = agendas.salvarAgenda(agenda)
            _salvando.value = false
            r.onSuccess {
                _eventos.send(Evento.Mensagem(texto(R.string.agenda_salva)))
                _eventos.send(Evento.Salvo)
            }.onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }
}
