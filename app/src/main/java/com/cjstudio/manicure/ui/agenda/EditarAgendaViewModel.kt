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
import com.cjstudio.manicure.util.TextoUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Criar ou editar uma agenda mensal: categoria, mês, quem atende, quais dias
// do mês atendem (todos aparecem; a profissional desmarca os que não pode),
// os horários de cada dia e, num dia específico, os horários indisponíveis.
@HiltViewModel
class EditarAgendaViewModel @Inject constructor(
    private val agendas: IAgendaRepository,
    private val profissionais: IProfissionalRepository,
    estado: SavedStateHandle
) : ViewModel() {

    private val id: String? = estado[EditarAgendaActivity.EXTRA_ID]
    val editando = id != null

    // Dados fixos da tela (categorias da profissional, meses, agenda em edição).
    data class Inicio(
        val categorias: List<Pair<String, String>>,
        val meses: List<String>,
        val nomeProfissional: String,
        val agenda: Agenda?
    )

    // O que está sendo montado.
    data class Montagem(
        val mes: String? = null,
        val dias: Set<String> = emptySet(),
        val horarios: List<String> = emptyList(),
        val bloqueados: Set<String> = emptySet()
    ) {
        // Dias com algum horário indisponível (a tela marca com ✎).
        val diasAjustados: Set<String> get() = bloqueados.map { it.substringBefore("-") }.toSet()
    }

    sealed interface Evento {
        data object Salvo : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    private val _inicio = MutableStateFlow<Inicio?>(null)
    val inicio: StateFlow<Inicio?> = _inicio

    private val _montagem = MutableStateFlow(Montagem())
    val montagem: StateFlow<Montagem> = _montagem

    private val _salvando = MutableStateFlow(false)
    val salvando: StateFlow<Boolean> = _salvando

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    init {
        viewModelScope.launch {
            val perfil = profissionais.buscarLogada().getOrNull()
            val todas = agendas.minhasAgendas().getOrNull().orEmpty()
            val agenda = id?.let { agendaId -> todas.firstOrNull { it.id == agendaId } }
            // Sem categoria marcada no painel, oferece todas.
            val minhas = perfil?.categorias.orEmpty()
            val categorias = Categorias.TODAS.filter { minhas.isEmpty() || it.first in minhas || it.first == agenda?.categoria }
            val meses = (AgendaUtil.proximosMeses(4) + listOfNotNull(agenda?.mes)).distinct().sorted()
            _montagem.value = if (agenda != null) {
                Montagem(agenda.mes, AgendaUtil.diasAtivos(agenda).toSet(), agenda.horarios, agenda.bloqueados.toSet())
            } else {
                // Agenda nova já vem pré-marcada: seg a sex, das 09:00 às 18:00 de hora em hora.
                Montagem(meses.first(), AgendaUtil.diasPadrao(meses.first()).toSet(), AgendaUtil.gerarHorarios("09:00", "18:00", 60))
            }
            // Agenda nova: sugere o nome dela, a não ser que ela já atenda em
            // outra agenda deste mês (aí o campo vem vazio pra pôr outra pessoa).
            val nomeDela = perfil?.nomeResponsavel?.ifBlank { null } ?: perfil?.nomeExibicao.orEmpty()
            val jaAtende = todas.any { it.mes == _montagem.value.mes && TextoUtil.normalizar(it.atendente.orEmpty()) == TextoUtil.normalizar(nomeDela) }
            _inicio.value = Inicio(categorias, meses, if (jaAtende) "" else nomeDela, agenda)
        }
    }

    fun escolherMes(mes: String) {
        val atual = _montagem.value
        if (mes == atual.mes) return
        val original = _inicio.value?.agenda
        _montagem.value = if (original != null && original.mes == mes) {
            atual.copy(mes = mes, dias = AgendaUtil.diasAtivos(original).toSet(), bloqueados = original.bloqueados.toSet())
        } else {
            atual.copy(mes = mes, dias = AgendaUtil.diasPadrao(mes).toSet(), bloqueados = emptySet())
        }
    }

    fun alternarDia(data: String) {
        _montagem.update { m -> m.copy(dias = if (data in m.dias) m.dias - data else m.dias + data) }
    }

    // Aplica início/fim/intervalo a todos os dias.
    fun gerarHorarios(inicio: String, fim: String, intervalo: String) {
        val lista = AgendaUtil.gerarHorarios(inicio, fim, intervalo.trim().toIntOrNull() ?: 0)
        if (lista.isEmpty()) {
            _eventos.trySend(Evento.Mensagem(texto(R.string.agenda_erro_gerar)))
            return
        }
        _montagem.update { m -> m.copy(horarios = lista, bloqueados = m.bloqueados.filter { it.horaDaChave() in lista }.toSet()) }
    }

    // Tira um horário de todos os dias (ex.: almoço).
    fun removerHorario(hora: String) {
        _montagem.update { m -> m.copy(horarios = m.horarios - hora, bloqueados = m.bloqueados.filter { it.horaDaChave() != hora }.toSet()) }
    }

    // Horários de um dia com o que está disponível (true) ou não (false).
    fun horariosDoDia(data: String): List<Pair<String, Boolean>> {
        val m = _montagem.value
        return m.horarios.map { it to (AgendaUtil.chaveHorario(data, it) !in m.bloqueados) }
    }

    fun definirDisponiveisDoDia(data: String, disponiveis: Set<String>) {
        _montagem.update { m ->
            val outrosDias = m.bloqueados.filter { !it.startsWith("$data-") }
            val desteDia = m.horarios.filter { it !in disponiveis }.map { AgendaUtil.chaveHorario(data, it) }
            m.copy(bloqueados = (outrosDias + desteDia).toSet())
        }
    }

    fun salvar(categoria: String?, atendente: String) {
        if (_salvando.value) return
        val m = _montagem.value
        val erro = when {
            categoria == null -> R.string.agenda_erro_categoria
            m.mes == null -> R.string.agenda_erro_mes
            atendente.isBlank() -> R.string.agenda_erro_atendente
            m.dias.isEmpty() -> R.string.agenda_erro_dias
            m.horarios.isEmpty() -> R.string.agenda_erro_horarios
            else -> null
        }
        if (erro != null) {
            _eventos.trySend(Evento.Mensagem(texto(erro)))
            return
        }
        val agenda = (_inicio.value?.agenda ?: Agenda()).copy(
            categoria = categoria,
            mes = m.mes,
            atendente = atendente.trim(),
            dias = m.dias.sorted(),
            horarios = m.horarios.sortedBy { AgendaUtil.minutos(it) },
            // Só os bloqueios de dias marcados e horários que ainda existem.
            bloqueados = m.bloqueados.filter { it.substringBefore("-") in m.dias && it.horaDaChave() in m.horarios }.sorted()
        ).also { it.id = id }
        _salvando.value = true
        viewModelScope.launch {
            // Uma pessoa não atende duas clientes ao mesmo tempo: no mesmo mês,
            // cada agenda tem que ser de uma profissional diferente.
            val minhas = agendas.minhasAgendas().getOrElse {
                _salvando.value = false
                _eventos.send(Evento.Mensagem(it.paraUiTexto()))
                return@launch
            }
            val nome = TextoUtil.normalizar(atendente)
            val repetida = minhas.firstOrNull { it.id != id && it.mes == m.mes && TextoUtil.normalizar(it.atendente.orEmpty()) == nome }
            if (repetida != null) {
                _salvando.value = false
                _eventos.send(Evento.Mensagem(texto(
                    R.string.agenda_erro_atendente_repetido,
                    atendente.trim(), Categorias.nome(repetida.categoria), AgendaUtil.nomeMes(m.mes)
                )))
                return@launch
            }
            val r = agendas.salvarAgenda(agenda)
            _salvando.value = false
            r.onSuccess {
                _eventos.send(Evento.Mensagem(texto(R.string.agenda_salva)))
                _eventos.send(Evento.Salvo)
            }.onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }

    // "20261105-0930" -> "09:30"
    private fun String.horaDaChave() = substringAfter("-").let { "${it.take(2)}:${it.drop(2)}" }
}
