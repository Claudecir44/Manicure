package com.cjstudio.manicure

import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.util.Assinatura
import com.cjstudio.manicure.util.Assinatura.Situacao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

class AssinaturaTest {

    private val agora = Date(1_800_000_000_000L)
    private fun dias(n: Int) = Date(agora.time + TimeUnit.DAYS.toMillis(n.toLong()))

    @Test
    fun semAgendaOTesteNaoComecou() {
        val e = Assinatura.estado(Profissional(), agora)
        assertEquals(Situacao.NAO_INICIADA, e.situacao)
        assertTrue(e.ativa)
        assertEquals(3, e.agendasPermitidas)
        assertFalse(e.podeComprarExtra)
    }

    @Test
    fun testeContaTrintaDiasDaPrimeiraAgenda() {
        val e = Assinatura.estado(Profissional(testeInicioEm = dias(-10)), agora)
        assertEquals(Situacao.TESTE, e.situacao)
        assertEquals(20, e.diasRestantes)
        assertEquals(3, e.agendasPermitidas)
        assertTrue(e.podeRenovar)
        assertFalse(e.avisoVencimento)
    }

    @Test
    fun avisoFaltandoCincoDias() {
        val e = Assinatura.estado(Profissional(testeInicioEm = dias(-26)), agora)
        assertEquals(4, e.diasRestantes)
        assertTrue(e.avisoVencimento)
    }

    @Test
    fun testeAcabadoSemPagarVence() {
        val e = Assinatura.estado(Profissional(testeInicioEm = dias(-31)), agora)
        assertEquals(Situacao.VENCIDA, e.situacao)
        assertEquals(0, e.agendasPermitidas)
        assertTrue(e.podeRenovar)
    }

    @Test
    fun pagoSoRenovaNosUltimosTresDias() {
        val p = Profissional(testeInicioEm = dias(-60), acessoPagoAte = dias(10))
        val e = Assinatura.estado(p, agora)
        assertEquals(Situacao.PAGA, e.situacao)
        assertEquals(10, e.diasRestantes)
        assertFalse(e.podeRenovar)
        assertEquals(dias(7), e.renovarAPartirDe)

        val perto = Assinatura.estado(p.copy(acessoPagoAte = dias(3)), agora)
        assertTrue(perto.podeRenovar)
        assertNull(perto.renovarAPartirDe)
    }

    @Test
    fun extrasSomamAteOFimDoPeriodo() {
        val p = Profissional(testeInicioEm = dias(-60), acessoPagoAte = dias(20), agendasExtras = 2, extrasAte = dias(20))
        val e = Assinatura.estado(p, agora)
        assertEquals(5, e.agendasPermitidas)
        assertTrue(e.podeComprarExtra)

        val vencidas = Assinatura.estado(p.copy(extrasAte = dias(-1)), agora)
        assertEquals(3, vencidas.agendasPermitidas)
    }

    @Test
    fun pagouDuranteOTesteContaPrimeiroOsDiasGratis() {
        val p = Profissional(testeInicioEm = dias(-25), acessoPagoAte = dias(35))
        val e = Assinatura.estado(p, agora)
        assertEquals(Situacao.TESTE, e.situacao)
        assertEquals(5, e.diasRestantes)
        assertFalse(e.podeRenovar)
    }
}
