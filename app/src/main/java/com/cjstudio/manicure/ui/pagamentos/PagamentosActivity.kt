package com.cjstudio.manicure.ui.pagamentos

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cjstudio.manicure.R
import com.cjstudio.manicure.databinding.ActivityPagamentosBinding
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.ui.common.texto
import com.cjstudio.manicure.util.Aparencia
import com.cjstudio.manicure.util.Assinatura
import com.cjstudio.manicure.util.Assinatura.Situacao
import com.cjstudio.manicure.util.Formato
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

// "Pagamentos" (Configurações e o contador de dias do painel).
@AndroidEntryPoint
class PagamentosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPagamentosBinding
    private val viewModel: PagamentosViewModel by viewModels()
    private lateinit var cobranca: CobrancaGooglePlay

    override fun onCreate(savedInstanceState: Bundle?) {
        Aparencia.aplicar(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPagamentosBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.topo.tvTitulo.setText(R.string.pag_titulo)
        binding.topo.btnVoltar.setOnClickListener { finish() }

        cobranca = CobrancaGooglePlay(
            this,
            Assinatura.Plano.entries.map { it.produto } + Assinatura.PRODUTO_AGENDA_EXTRA,
            aoComprar = { compra ->
                runOnUiThread { compra.products.firstOrNull()?.let { viewModel.confirmar(compra.purchaseToken, it) } }
            },
            aoErro = { msg -> runOnUiThread { mostrarMensagem(texto(R.string.pag_erro_google, msg)) } }
        )
        cobranca.conectar {
            runOnUiThread { mostrarPrecos() }
            cobranca.reconferir()
        }
        mostrarPrecos()

        binding.btnBasico.setOnClickListener { pagar(Assinatura.Plano.BASICO.produto, getString(R.string.pag_basico)) }
        binding.btnPlus.setOnClickListener { pagar(Assinatura.Plano.PLUS.produto, getString(R.string.pag_plus)) }
        binding.btnExtra.setOnClickListener { pagar(Assinatura.PRODUTO_AGENDA_EXTRA, getString(R.string.pag_extra_titulo)) }

        coletar(viewModel.estado) { mostrar(it) }
        coletar(viewModel.mensagens) { mostrarMensagem(it) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.atualizar()
        cobranca.reconferir()
    }

    override fun onDestroy() {
        cobranca.encerrar()
        super.onDestroy()
    }

    // Preço do Google Play quando o produto já existe lá; senão o da tabela.
    private fun mostrarPrecos() {
        binding.tvPrecoBasico.text = getString(R.string.pag_preco_mes, cobranca.preco(Assinatura.Plano.BASICO.produto) ?: Formato.valor(Assinatura.Plano.BASICO.valor))
        binding.tvPrecoPlus.text = getString(R.string.pag_preco_90, cobranca.preco(Assinatura.Plano.PLUS.produto) ?: Formato.valor(Assinatura.Plano.PLUS.valor))
        binding.tvPrecoExtra.text = getString(R.string.pag_preco_extra, cobranca.preco(Assinatura.PRODUTO_AGENDA_EXTRA) ?: Formato.valor(Assinatura.VALOR_AGENDA_EXTRA))
    }

    private fun pagar(produto: String, nome: String) {
        viewModel.motivoParaNaoComprar(produto)?.let {
            mostrarMensagem(it)
            return
        }
        val uid = viewModel.uid ?: return
        cobranca.conectar {
            runOnUiThread {
                if (!cobranca.comprar(this, produto, uid)) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(nome)
                        .setMessage(R.string.pag_indisponivel)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }
            }
        }
    }

    private fun mostrar(e: PagamentosViewModel.Estado) {
        binding.progressBar.visibility = if (e.carregando || e.confirmando) View.VISIBLE else View.GONE
        val a = e.assinatura ?: return

        binding.tvStatusTitulo.text = when (a.situacao) {
            Situacao.NAO_INICIADA -> getString(R.string.pag_status_nao_iniciada)
            Situacao.TESTE -> getString(R.string.pag_status_teste)
            Situacao.PAGA -> getString(if (e.perfil?.plano == "plus") R.string.pag_status_plus else R.string.pag_status_basico)
            Situacao.VENCIDA -> getString(R.string.pag_status_vencida)
        }
        binding.tvStatusDias.text = when (a.situacao) {
            Situacao.NAO_INICIADA -> getString(R.string.pag_dias_nao_iniciada, Assinatura.DIAS_TESTE)
            Situacao.TESTE -> resources.getQuantityString(R.plurals.painel_dias_gratis, a.diasRestantes, a.diasRestantes)
            Situacao.PAGA -> resources.getQuantityString(R.plurals.painel_dias_pagos, a.diasRestantes, a.diasRestantes)
            Situacao.VENCIDA -> getString(R.string.pag_dias_vencida)
        }
        binding.tvStatusFim.text = when (a.situacao) {
            Situacao.NAO_INICIADA -> getString(R.string.pag_fim_nao_iniciada)
            Situacao.VENCIDA -> getString(R.string.pag_fim_vencida, Assinatura.data(a.fim))
            else -> getString(R.string.pag_fim, Assinatura.data(a.fim))
        }
        binding.tvAgendas.text = getString(R.string.pag_agendas, e.agendasEmUso, a.agendasPermitidas)

        binding.tvAviso.visibility = if (a.avisoVencimento || a.situacao == Situacao.VENCIDA) View.VISIBLE else View.GONE
        binding.tvAviso.text = if (a.situacao == Situacao.VENCIDA) getString(R.string.pag_aviso_vencida)
        else resources.getQuantityString(R.plurals.painel_aviso_expirar, a.diasRestantes, a.diasRestantes)

        // Planos: só liberados quando a regra de renovação deixa.
        binding.btnBasico.isEnabled = a.podeRenovar && !e.confirmando
        binding.btnPlus.isEnabled = a.podeRenovar && !e.confirmando
        binding.btnBasico.alpha = if (a.podeRenovar) 1f else 0.45f
        binding.btnPlus.alpha = if (a.podeRenovar) 1f else 0.45f
        binding.tvRenovar.visibility = if (a.podeRenovar) View.GONE else View.VISIBLE
        binding.tvRenovar.text = getString(R.string.pag_renovar_bloqueado, Assinatura.data(a.renovarAPartirDe), Assinatura.DIAS_PARA_RENOVAR)

        binding.btnExtra.isEnabled = a.podeComprarExtra && !e.confirmando
        binding.btnExtra.alpha = if (a.podeComprarExtra) 1f else 0.45f

        binding.tvSemHistorico.visibility = if (e.historico.isEmpty()) View.VISIBLE else View.GONE
        binding.llHistorico.removeAllViews()
        e.historico.forEach { p ->
            val nome = when {
                p.tipo == "extra" -> getString(R.string.pag_extra_titulo)
                p.plano == "plus" -> getString(R.string.pag_plus)
                else -> getString(R.string.pag_basico)
            }
            binding.llHistorico.addView(TextView(this).apply {
                text = getString(R.string.pag_historico_linha, nome, Formato.valor(p.valor), Assinatura.data(p.criadoEm ?: p.inicio), Assinatura.data(p.fim))
                setTextColor(ContextCompat.getColor(context, R.color.texto))
                textSize = 14f
                setBackgroundResource(R.drawable.bg_item_servico)
                val pad = (12 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = pad / 2 }
            })
        }
    }
}
