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

    // "09:00" até "18:00" a cada 60 min -> 09:00, 10:00 ... 17:00.
    fun gerarHorarios(inicio: String, fim: String, intervaloMin: Int): List<String> {
        val ini = minutos(inicio) ?: return emptyList()
        val f = minutos(fim) ?: return emptyList()
        if (intervaloMin < 10 || f <= ini) return emptyList()
        return (ini until f step intervaloMin).map { "%02d:%02d".format(it / 60, it % 60) }
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

    // Dias do mês com horário livre, a partir de agora.
    fun diasLivres(agenda: Agenda, agora: Long = System.currentTimeMillis()): List<Dia> {
        val (ano, m) = anoMes(agenda.mes) ?: return emptyList()
        val ocupados = agenda.ocupados.toSet()
        val c = Calendar.getInstance().apply { clear(); set(ano, m - 1, 1) }
        val dias = mutableListOf<Dia>()
        while (c.get(Calendar.MONTH) == m - 1) {
            if (c.get(Calendar.DAY_OF_WEEK) in agenda.diasSemana) {
                val data = "%04d%02d%02d".format(ano, m, c.get(Calendar.DAY_OF_MONTH))
                val livres = agenda.horarios.sortedBy { minutos(it) ?: 0 }.mapNotNull { hora ->
                    val chave = "$data-${hora.replace(":", "")}"
                    val quando = calendario(chave)?.timeInMillis ?: return@mapNotNull null
                    if (chave in ocupados || quando <= agora) null else Horario(chave, hora)
                }
                if (livres.isNotEmpty()) {
                    dias += Dia(data, SimpleDateFormat("EEE dd/MM", BR).format(c.time).replaceFirstChar { it.uppercase() }, livres)
                }
            }
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return dias
    }

    // Total de horários do mês (pra mostrar "12 de 80 ocupados").
    fun totalHorarios(agenda: Agenda): Int {
        val (ano, m) = anoMes(agenda.mes) ?: return 0
        val c = Calendar.getInstance().apply { clear(); set(ano, m - 1, 1) }
        var dias = 0
        while (c.get(Calendar.MONTH) == m - 1) {
            if (c.get(Calendar.DAY_OF_WEEK) in agenda.diasSemana) dias++
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return dias * agenda.horarios.size
    }

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
