package com.cjstudio.manicure

import com.cjstudio.manicure.data.model.Agenda
import com.cjstudio.manicure.util.AgendaUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AgendaUtilTest {

    // Bem antes do mês testado, pra nenhum horário contar como "passado".
    private val antes = Calendar.getInstance().apply { clear(); set(2030, 0, 1) }.timeInMillis

    @Test
    fun gerarHorarios_deHoraEmHora() {
        assertEquals(listOf("09:00", "10:00", "11:00"), AgendaUtil.gerarHorarios("09:00", "12:00", 60))
        assertTrue(AgendaUtil.gerarHorarios("12:00", "09:00", 60).isEmpty())
    }

    @Test
    fun diasDoMes_todosOsDias() {
        assertEquals(30, AgendaUtil.diasDoMes("2030-11").size)
        assertEquals(31, AgendaUtil.diasDoMes("2030-12").size)
    }

    @Test
    fun diasLivres_respeitaDiasMarcadosBloqueadosEOcupados() {
        val agenda = Agenda(
            mes = "2030-11",
            dias = listOf("20301104", "20301105"),
            horarios = listOf("09:00", "10:00", "11:00"),
            bloqueados = listOf("20301104-1000"),
            ocupados = listOf("20301105-0900")
        )
        val dias = AgendaUtil.diasLivres(agenda, agora = antes)
        assertEquals(listOf("20301104", "20301105"), dias.map { it.data })
        assertEquals(listOf("09:00", "11:00"), dias[0].livres.map { it.hora })
        assertEquals(listOf("10:00", "11:00"), dias[1].livres.map { it.hora })
        assertEquals(5, AgendaUtil.totalHorarios(agenda))
    }

    @Test
    fun agendaAntiga_porDiaDaSemana_continuaFuncionando() {
        // 04/11/2030 é segunda-feira.
        val agenda = Agenda(mes = "2030-11", diasSemana = listOf(Calendar.MONDAY), horarios = listOf("09:00"))
        val dias = AgendaUtil.diasAtivos(agenda)
        assertEquals(listOf("20301104", "20301111", "20301118", "20301125"), dias)
        assertFalse(AgendaUtil.diasLivres(agenda, agora = antes).isEmpty())
    }
}
