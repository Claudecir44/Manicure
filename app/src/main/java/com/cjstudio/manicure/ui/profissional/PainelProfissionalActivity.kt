package com.cjstudio.manicure.ui.profissional

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.model.TipoPerfil
import com.cjstudio.manicure.databinding.ActivityPainelProfissionalBinding
import com.cjstudio.manicure.ui.agenda.AgendaProfissionalActivity
import com.cjstudio.manicure.ui.chat.ConversasActivity
import com.cjstudio.manicure.ui.common.DialogoCores
import com.cjstudio.manicure.ui.common.Navegacao
import com.cjstudio.manicure.ui.common.PermissaoNotificacao
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.ui.common.texto
import com.cjstudio.manicure.ui.perfil.PerfilProfissionalActivity
import com.cjstudio.manicure.util.Aparencia
import com.cjstudio.manicure.util.BadgeIcone
import com.cjstudio.manicure.util.carregarFotoRedonda
import com.google.android.material.color.MaterialColors
import com.cjstudio.manicure.util.Assinatura
import com.cjstudio.manicure.util.Assinatura.Situacao
import dagger.hilt.android.AndroidEntryPoint

// Painel da manicure: resumo do perfil e atalhos (agenda, meu perfil, chat,
// ver como cliente, divulgar). Os dados do perfil ficam no Meu perfil.
@AndroidEntryPoint
class PainelProfissionalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPainelProfissionalBinding
    private val viewModel: PainelProfissionalViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        Aparencia.aplicar(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPainelProfissionalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val abrirMeuPerfil = View.OnClickListener { startActivity(Intent(this, EditarPerfilActivity::class.java)) }
        binding.btnMeuPerfil.setOnClickListener(abrirMeuPerfil)
        binding.ivFoto.setOnClickListener(abrirMeuPerfil)
        binding.cartaoCompleto.setOnClickListener(abrirMeuPerfil)
        binding.btnSair.setOnClickListener { viewModel.sair() }
        binding.btnAgenda.setOnClickListener { startActivity(Intent(this, AgendaProfissionalActivity::class.java)) }
        binding.btnVerComoCliente.setOnClickListener {
            viewModel.perfil.value?.id?.let { id ->
                startActivity(Intent(this, PerfilProfissionalActivity::class.java).putExtra(PerfilProfissionalActivity.EXTRA_ID, id))
            }
        }
        binding.btnCompartilhar.setOnClickListener { compartilhar() }
        binding.tvCodigo.setOnClickListener { copiarCodigo() }
        binding.btnAparencia.setOnClickListener { escolherAparencia() }
        binding.btnConfiguracoes.setOnClickListener { startActivity(Intent(this, ConfiguracoesActivity::class.java)) }

        val abrirConversas = View.OnClickListener {
            startActivity(Intent(this, ConversasActivity::class.java).putExtra(ConversasActivity.EXTRA_COMO_PROFISSIONAL, true))
        }
        binding.btnAbrirChat.setOnClickListener(abrirConversas)
        binding.chat.btnChat.setOnClickListener(abrirConversas)
        coletar(viewModel.naoLidas) { n ->
            binding.chat.tvBadgeChat.visibility = if (n.comoProfissional > 0) View.VISIBLE else View.GONE
            binding.chat.tvBadgeChat.text = if (n.comoProfissional > 99) "99+" else n.comoProfissional.toString()
            BadgeIcone.atualizar(this, n.total)
        }
        PermissaoNotificacao.pedir(this)

        coletar(viewModel.perfil) { p -> if (p != null) mostrar(p) }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                PainelProfissionalViewModel.Evento.Saiu -> Navegacao.irLimpando(this, Navegacao.telaLogin())
            }
        }
    }

    // "Restam X dias gratuitos/pagos" e, faltando 5 dias, o lembrete vermelho.
    private fun mostrarAssinatura(p: Profissional) {
        val a = Assinatura.estado(p)
        binding.tvDiasAssinatura.text = when (a.situacao) {
            Situacao.NAO_INICIADA -> getString(R.string.painel_teste_nao_iniciado)
            Situacao.TESTE -> resources.getQuantityString(R.plurals.painel_dias_gratis, a.diasRestantes, a.diasRestantes)
            Situacao.PAGA -> resources.getQuantityString(R.plurals.painel_dias_pagos, a.diasRestantes, a.diasRestantes)
            Situacao.VENCIDA -> getString(R.string.painel_assinatura_vencida)
        }
        binding.tvDiasAssinatura.setTextColor(
            if (a.situacao == Situacao.VENCIDA) ContextCompat.getColor(this, R.color.vermelho)
            else MaterialColors.getColor(binding.tvDiasAssinatura, R.attr.corDestaqueClaro)
        )
        binding.tvAvisoAssinatura.visibility = if (a.avisoVencimento) View.VISIBLE else View.GONE
        binding.tvAvisoAssinatura.text = resources.getQuantityString(R.plurals.painel_aviso_expirar, a.diasRestantes, a.diasRestantes)
        binding.tvDiasAssinatura.setOnClickListener {
            startActivity(android.content.Intent(this, com.cjstudio.manicure.ui.pagamentos.PagamentosActivity::class.java))
        }
    }

    private fun mostrar(p: Profissional) {
        binding.ivFoto.carregarFotoRedonda(p.fotoPerfilUrl)
        val primeiroNome = p.nomeExibicao?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
        binding.tvSaudacao.text = if (primeiroNome != null) getString(R.string.painel_ola, primeiroNome) else getString(R.string.painel_ola_sem_nome)
        binding.tvNome.text = p.nomeExibicao
        binding.tvNome.visibility = if (p.nomeExibicao.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.tvTipo.setText(if (p.tipoPerfil == TipoPerfil.SALAO) R.string.perfil_tipo_salao else R.string.perfil_tipo_pessoal)
        binding.tvCodigo.text = getString(R.string.painel_codigo, p.codigo.orEmpty())
        mostrarAssinatura(p)

        // Situação do perfil pras clientes.
        val (situacao, cor) = when {
            p.bloqueado -> R.string.painel_situacao_bloqueado to R.color.vermelho
            p.publicado -> R.string.painel_situacao_publicado to R.color.verde
            else -> R.string.painel_situacao_incompleto to null
        }
        binding.tvSituacao.setText(situacao)
        binding.tvSituacao.setTextColor(
            if (cor != null) ContextCompat.getColor(this, cor)
            else MaterialColors.getColor(binding.tvSituacao, R.attr.corDestaqueClaro)
        )

        binding.tvQtdServicos.text = p.servicos.size.toString()
        binding.tvQtdFotos.text = p.fotos.size.toString()
        binding.tvQtdCategorias.text = p.categorias.size.toString()

        val pct = porcentagemCompleta(p)
        binding.tvCompleto.text = getString(R.string.painel_completo, pct)
        binding.progressoPerfil.setProgressCompat(pct, true)
        binding.tvCompletoDica.setText(if (pct == 100) R.string.painel_completo_ok else R.string.painel_completo_dica)
    }

    // O que deixa o perfil atrativo pras clientes; cada item vale o mesmo.
    private fun porcentagemCompleta(p: Profissional): Int {
        val itens = listOf(
            !p.nomeExibicao.isNullOrBlank(),
            !p.fotoPerfilUrl.isNullOrBlank(),
            !p.apresentacao.isNullOrBlank(),
            !p.sobreTrabalho.isNullOrBlank(),
            !p.whatsapp.isNullOrBlank(),
            !p.cidade.isNullOrBlank(),
            p.categorias.isNotEmpty(),
            p.servicos.isNotEmpty(),
            p.fotos.isNotEmpty()
        )
        return itens.count { it } * 100 / itens.size
    }

    // Cor das telas dela: salva e redesenha a tela já com a nova cor.
    private fun escolherAparencia() {
        DialogoCores.mostrar(this, R.string.aparencia_titulo, R.string.aparencia_dica, Aparencia.atual(this)) { cor ->
            Aparencia.salvar(this, cor)
            recreate()
        }
    }

    private fun copiarCodigo() {
        val codigo = viewModel.perfil.value?.codigo ?: return
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(codigo, codigo))
        mostrarMensagem(texto(R.string.painel_codigo_copiado))
    }

    private fun compartilhar() {
        val p = viewModel.perfil.value ?: return
        val texto = getString(R.string.painel_compartilhar_texto, p.nomeExibicao.orEmpty(), p.codigo.orEmpty())
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto), null))
    }
}
