package com.cjstudio.manicure.util

import com.cjstudio.manicure.data.model.Agenda
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Contas da agenda mensal: gerar horários, montar os dias do mês e saber o
// que ainda está livre. Chave de um horário: "20261105-0900".
object AgendaUtil {

    private val BR = Locale("pt", "BR")

    data class Horario(val chave: String, val hora: String)

    // Um dia da agenda com os horários livres (já sem os passados).
    data class Dia(val data: String, val titulo: String, val livres: List<Horario>)

    // "09:00" até "18:00" a cada 60 min -> 09:00, 10:00 ... 18:00 (o último
    // horário também fica disponível pra marcar).
    fun gerarHorarios(inicio: String, fim: String, intervaloMin: Int): List<String> {
        val ini = minutos(inicio) ?: return emptyList()
        val f = minutos(fim) ?: return emptyList()
        if (intervaloMin < 10 || f <= ini) return emptyList()
        return (ini..f step intervaloMin).map { "%02d:%02d".format(it / 60, it % 60) }
    }

    fun minutos(hora: String): Int? {
        val partes = hora.trim().split(":")
        val h = partes.getOrNull(0)?.toIntOrNull() ?: return null
        val m = partes.getOrNull(1)?.toIntOrNull() ?: 0
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    // Os próximos meses a partir do atual: "2026-10", "2026-11"...
    fun proximosMeses(quantos: Int): List<String> {
        val c = Calendar.getInstance()
        return (0 until quantos).map {
            val mes = "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
            c.add(Calendar.MONTH, 1)
            mes
        }
    }

    // "2026-11" -> "Novembro de 2026".
    fun nomeMes(mes: String?): String {
        val (ano, m) = anoMes(mes) ?: return mes.orEmpty()
        val c = Calendar.getInstance().apply { set(ano, m - 1, 1) }
        return SimpleDateFormat("MMMM 'de' yyyy", BR).format(c.time).replaceFirstChar { it.uppercase() }
    }

    // "20261105-0900" -> "Qui, 05/11 às 09:00".
    fun descrever(chave: String?): String {
        val c = calendario(chave) ?: return chave.orEmpty()
        return SimpleDateFormat("EEE, dd/MM 'às' HH:mm", BR).format(c.time).replaceFirstChar { it.uppercase() }
    }

    // Um dia do calendário do mês (tela de criar agenda).
    data class DiaDoMes(val data: String, val titulo: String, val diaSemana: Int, val passado: Boolean)

    // Todos os dias do mês: "Seg 03", "Ter 04"...
    fun diasDoMes(mes: String?): List<DiaDoMes> {
        val (ano, m) = anoMes(mes) ?: return emptyList()
        val hoje = chaveData(Calendar.getInstance())
        val c = Calendar.getInstance().apply { clear(); set(ano, m - 1, 1) }
        val lista = mutableListOf<DiaDoMes>()
        while (c.get(Calendar.MONTH) == m - 1) {
            val data = chaveData(c)
            lista += DiaDoMes(data, SimpleDateFormat("EEE dd", BR).format(c.time).replaceFirstChar { it.uppercase() }, c.get(Calendar.DAY_OF_WEEK), data < hoje)
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return lista
    }

    // Agenda nova: segunda a sexta já marcados (sem os dias que já passaram).
    fun diasPadrao(mes: String?): List<String> =
        diasDoMes(mes).filter { !it.passado && it.diaSemana in Calendar.MONDAY..Calendar.FRIDAY }.map { it.data }

    // Dias marcados da agenda (agendas antigas guardavam dias da semana).
    fun diasAtivos(agenda: Agenda): List<String> =
        if (agenda.dias.isNotEmpty()) agenda.dias
        else diasDoMes(agenda.mes).filter { it.diaSemana in agenda.diasSemana }.map { it.data }

    fun chaveHorario(data: String, hora: String) = "$data-${hora.replace(":", "")}"

    // "20261105" -> "Qui, 05/11".
    fun descreverDia(data: String): String {
        val c = calendario("$data-0000") ?: return data
        return SimpleDateFormat("EEE, dd/MM", BR).format(c.time).replaceFirstChar { it.uppercase() }
    }

    // Dias do mês com horário livre, a partir de agora.
    fun diasLivres(agenda: Agenda, agora: Long = System.currentTimeMillis()): List<Dia> {
        val indisponiveis = (agenda.ocupados + agenda.bloqueados).toSet()
        val horas = agenda.horarios.sortedBy { minutos(it) ?: 0 }
        return diasAtivos(agenda).sorted().mapNotNull { data ->
            val livres = horas.mapNotNull { hora ->
                val chave = chaveHorario(data, hora)
                val quando = calendario(chave)?.timeInMillis ?: return@mapNotNull null
                if (chave in indisponiveis || quando <= agora) null else Horario(chave, hora)
            }
            if (livres.isEmpty()) null
            else Dia(data, SimpleDateFormat("EEE dd/MM", BR).format(calendario("$data-0000")!!.time).replaceFirstChar { it.uppercase() }, livres)
        }
    }

    // Total de horários do mês (pra mostrar "12 de 80 marcados").
    fun totalHorarios(agenda: Agenda): Int {
        val bloqueados = agenda.bloqueados.toSet()
        return diasAtivos(agenda).sumOf { data -> agenda.horarios.count { chaveHorario(data, it) !in bloqueados } }
    }

    // Cor de um dia no calendário da "Minha agenda".
    enum class SituacaoDia { PASSADO, MARCADO, ABERTO, FECHADO }

    // data == null: quadro vazio antes do dia 1 (a semana começa no domingo).
    data class DiaCalendario(val data: String?, val numero: Int, val situacao: SituacaoDia, val hoje: Boolean)

    // Calendário de um mês juntando todas as agendas: já passou > tem horário
    // marcado > aberto em alguma agenda > sem agenda.
    fun calendarioDoMes(mes: String?, agendas: List<Agenda>, marcados: List<String>): List<DiaCalendario> {
        val dias = diasDoMes(mes)
        if (dias.isEmpty()) return emptyList()
        val hoje = chaveData(Calendar.getInstance())
        val abertos = agendas.filter { it.mes == mes }.flatMap { diasAtivos(it) }.toSet()
        val comMarcado = marcados.map { it.take(8) }.toSet()
        val vazios = List(dias.first().diaSemana - Calendar.SUNDAY) { DiaCalendario(null, 0, SituacaoDia.FECHADO, false) }
        return vazios + dias.map { d ->
            val situacao = when {
                d.passado -> SituacaoDia.PASSADO
                d.data in comMarcado -> SituacaoDia.MARCADO
                d.data in abertos -> SituacaoDia.ABERTO
                else -> SituacaoDia.FECHADO
            }
            DiaCalendario(d.data, d.data.takeLast(2).toInt(), situacao, d.data == hoje)
        }
    }

    // "2026-11" + 1 -> "2026-12".
    fun somarMes(mes: String?, meses: Int): String {
        val (ano, m) = anoMes(mes) ?: return proximosMeses(1).first()
        val c = Calendar.getInstance().apply { clear(); set(ano, m - 1, 1); add(Calendar.MONTH, meses) }
        return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    private fun chaveData(c: Calendar) =
        "%04d%02d%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    // Mês já terminou (some da lista da cliente).
    fun mesPassado(mes: String?): Boolean = mes != null && mes < proximosMeses(1).first()

    private fun anoMes(mes: String?): Pair<Int, Int>? {
        val partes = mes?.split("-") ?: return null
        val ano = partes.getOrNull(0)?.toIntOrNull() ?: return null
        val m = partes.getOrNull(1)?.toIntOrNull() ?: return null
        return ano to m
    }

    private fun calendario(chave: String?): Calendar? {
        if (chave == null || chave.length != 13) return null
        val ano = chave.substring(0, 4).toIntOrNull() ?: return null
        val mes = chave.substring(4, 6).toIntOrNull() ?: return null
        val dia = chave.substring(6, 8).toIntOrNull() ?: return null
        val h = chave.substring(9, 11).toIntOrNull() ?: return null
        val min = chave.substring(11, 13).toIntOrNull() ?: return null
        return Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, h, min) }
    }
}
