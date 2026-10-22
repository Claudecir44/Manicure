package com.cjstudio.manicure.util

import com.cjstudio.manicure.data.model.Profissional
import java.util.Date
import java.util.concurrent.TimeUnit

// Assinatura da profissional (a cliente não paga nada). Regras:
// - 30 dias grátis, contando da publicação da primeira agenda;
// - Básico: R$ 200,00 por 30 dias; Plus: R$ 500,00 por 90 dias; os dois com
//   até 3 agendas ao mesmo tempo e R$ 50,00 por agenda a mais (vale até o
//   fim do período pago em que foi comprada — paga só a diferença);
// - um novo plano só pode ser pago faltando 3 dias (ou menos) pro fim do
//   período pago; o período novo começa quando o atual acaba;
// - faltando 5 dias, aviso em vermelho.
// O servidor (confirmarCompraManicure) aplica as mesmas regras.
object Assinatura {

    const val DIAS_TESTE = 30
    const val AGENDAS_INCLUIDAS = 3
    const val DIAS_PARA_RENOVAR = 3
    const val DIAS_AVISO = 5

    enum class Plano(val produto: String, val dias: Int, val valor: Double) {
        BASICO("manicure_basico_30", 30, 200.0),
        PLUS("manicure_plus_90", 90, 500.0)
    }

    const val PRODUTO_AGENDA_EXTRA = "manicure_agenda_extra"
    const val VALOR_AGENDA_EXTRA = 50.0

    enum class Situacao {
        // Ainda não publicou nenhuma agenda: o teste nem começou.
        NAO_INICIADA,
        TESTE,
        PAGA,
        VENCIDA
    }

    data class Estado(
        val situacao: Situacao,
        // Dias que faltam (arredondado pra cima: falta 1 h = 1 dia).
        val diasRestantes: Int,
        val fim: Date?,
        // Quantas agendas ao mesmo tempo ela pode ter agora.
        val agendasPermitidas: Int,
        val extrasAtivas: Int,
        // Pode pagar um plano agora? Se não, a partir de quando.
        val podeRenovar: Boolean,
        val renovarAPartirDe: Date?,
        // Agenda extra só com plano pago em dia.
        val podeComprarExtra: Boolean
    ) {
        val ativa get() = situacao == Situacao.TESTE || situacao == Situacao.PAGA || situacao == Situacao.NAO_INICIADA
        val avisoVencimento get() = (situacao == Situacao.TESTE || situacao == Situacao.PAGA) && diasRestantes <= DIAS_AVISO
    }

    fun fimTeste(p: Profissional): Date? = p.testeInicioEm?.let { Date(it.time + TimeUnit.DAYS.toMillis(DIAS_TESTE.toLong())) }

    fun estado(p: Profissional, agora: Date = Date()): Estado {
        val fimTeste = fimTeste(p)
        val pagoAte = p.acessoPagoAte
        val extras = if (p.extrasAte != null && p.extrasAte!!.after(agora)) p.agendasExtras else 0
        val emPago = pagoAte != null && pagoAte.after(agora)
        val emTeste = fimTeste != null && fimTeste.after(agora)

        // Pagou durante o teste: o período pago começa no fim do teste, então
        // primeiro contam os dias grátis e depois os pagos.
        val situacao = when {
            emTeste -> Situacao.TESTE
            emPago -> Situacao.PAGA
            p.testeInicioEm == null && pagoAte == null -> Situacao.NAO_INICIADA
            else -> Situacao.VENCIDA
        }
        val fim = when (situacao) {
            Situacao.TESTE -> fimTeste
            Situacao.PAGA -> pagoAte
            Situacao.VENCIDA -> listOfNotNull(fimTeste, pagoAte).maxOrNull()
            Situacao.NAO_INICIADA -> null
        }
        val dias = fim?.let { diasAte(agora, it) } ?: 0
        val emPagoAgora = situacao == Situacao.PAGA

        // Renovação: sem período pago em dia, pode pagar já; com período pago,
        // só nos últimos DIAS_PARA_RENOVAR dias.
        val liberaEm = if (emPago) Date(pagoAte!!.time - TimeUnit.DAYS.toMillis(DIAS_PARA_RENOVAR.toLong())) else null
        val podeRenovar = liberaEm == null || !agora.before(liberaEm)

        return Estado(
            situacao = situacao,
            diasRestantes = dias,
            fim = fim,
            agendasPermitidas = if (situacao == Situacao.VENCIDA) 0 else AGENDAS_INCLUIDAS + if (emPagoAgora) extras else 0,
            extrasAtivas = if (emPagoAgora) extras else 0,
            podeRenovar = podeRenovar,
            renovarAPartirDe = liberaEm?.takeIf { !podeRenovar },
            podeComprarExtra = emPagoAgora
        )
    }

    // "25/11/2026"
    fun data(d: Date?): String = d?.let { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR")).format(it) } ?: "—"

    fun diasAte(agora: Date, fim: Date): Int {
        val ms = fim.time - agora.time
        if (ms <= 0) return 0
        return ((ms + TimeUnit.DAYS.toMillis(1) - 1) / TimeUnit.DAYS.toMillis(1)).toInt()
    }
}
