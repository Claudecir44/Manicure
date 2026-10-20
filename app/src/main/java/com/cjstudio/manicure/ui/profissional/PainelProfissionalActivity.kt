package com.cjstudio.manicure.ui.profissional

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.FotoPortfolio
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.model.Servico
import com.cjstudio.manicure.data.model.TipoPerfil
import com.cjstudio.manicure.databinding.ActivityPainelProfissionalBinding
import com.cjstudio.manicure.ui.common.FotosAdapter
import com.cjstudio.manicure.ui.common.Navegacao
import com.cjstudio.manicure.ui.common.ServicosAdapter
import com.cjstudio.manicure.ui.common.VisualizadorFoto
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.ui.common.texto
import com.cjstudio.manicure.ui.perfil.PerfilProfissionalActivity
import com.cjstudio.manicure.util.FotoUtil
import com.cjstudio.manicure.util.Formato
import com.cjstudio.manicure.util.carregarFotoRedonda
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

// Painel da manicure: tudo do perfil dela numa tela só (apresentação,
// serviços, galeria), com atalho pra ver como as clientes veem.
@AndroidEntryPoint
class PainelProfissionalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPainelProfissionalBinding
    private val viewModel: PainelProfissionalViewModel by viewModels()

    private val servicosAdapter = ServicosAdapter(
        aoTocar = { posicao, servico -> editarServico(posicao, servico) },
        aoSegurar = { posicao -> confirmarRemoverServico(posicao) }
    )
    private val fotosAdapter = FotosAdapter(
        aoTocar = { VisualizadorFoto.mostrar(this, it.url) },
        aoSegurar = { confirmarRemoverFoto(it) },
        aoAdicionar = { escolherFotoGaleria() }
    )

    // Qual foto o seletor vai preencher: a do perfil ou uma da galeria.
    private var escolhendoFotoPerfil = false
    private val seletorFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        val pronta = FotoUtil.prepararParaEnvio(this, uri) ?: uri
        if (escolhendoFotoPerfil) viewModel.trocarFotoPerfil(pronta) else viewModel.adicionarFoto(pronta)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPainelProfissionalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvServicos.layoutManager = LinearLayoutManager(this)
        binding.rvServicos.adapter = servicosAdapter
        binding.rvServicos.isNestedScrollingEnabled = false
        binding.rvFotos.layoutManager = GridLayoutManager(this, 3)
        binding.rvFotos.adapter = fotosAdapter
        binding.rvFotos.isNestedScrollingEnabled = false

        binding.btnSair.setOnClickListener { viewModel.sair() }
        binding.ivFoto.setOnClickListener {
            escolhendoFotoPerfil = true
            seletorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnEditarPerfil.setOnClickListener { startActivity(Intent(this, EditarPerfilActivity::class.java)) }
        binding.btnAdicionarServico.setOnClickListener { editarServico(null, null) }
        binding.btnVerComoCliente.setOnClickListener {
            viewModel.perfil.value?.id?.let { id ->
                startActivity(Intent(this, PerfilProfissionalActivity::class.java).putExtra(PerfilProfissionalActivity.EXTRA_ID, id))
            }
        }
        binding.btnCompartilhar.setOnClickListener { compartilhar() }

        coletar(viewModel.perfil) { p -> if (p != null) mostrar(p) }
        coletar(viewModel.enviando) { binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                PainelProfissionalViewModel.Evento.Saiu -> Navegacao.irLimpando(this, Navegacao.telaLogin())
                is PainelProfissionalViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    private fun mostrar(p: Profissional) {
        binding.ivFoto.carregarFotoRedonda(p.fotoPerfilUrl)
        binding.tvNome.text = p.nomeExibicao
        binding.tvTipo.setText(if (p.tipoPerfil == TipoPerfil.SALAO) R.string.perfil_tipo_salao else R.string.perfil_tipo_pessoal)
        binding.tvCodigo.text = getString(R.string.painel_codigo, p.codigo.orEmpty())

        // Situação do perfil pras clientes.
        val (situacao, cor) = when {
            p.bloqueado -> R.string.painel_situacao_bloqueado to R.color.vermelho
            p.publicado -> R.string.painel_situacao_publicado to R.color.verde
            else -> R.string.painel_situacao_incompleto to R.color.rosa_claro
        }
        binding.tvSituacao.setText(situacao)
        binding.tvSituacao.setTextColor(ContextCompat.getColor(this, cor))

        binding.tvApresentacao.text = p.apresentacao?.takeIf { it.isNotBlank() } ?: getString(R.string.painel_sem_apresentacao)
        binding.tvSobreTrabalho.text = p.sobreTrabalho?.takeIf { it.isNotBlank() } ?: getString(R.string.painel_sem_sobre)
        binding.tvContatos.text = ResumoPerfil.contatos(this, p)

        servicosAdapter.atualizar(p.servicos)
        binding.tvSemServicos.visibility = if (p.servicos.isEmpty()) View.VISIBLE else View.GONE

        binding.tvTituloGaleria.text = getString(R.string.painel_galeria, p.fotos.size, Profissional.MAX_FOTOS)
        fotosAdapter.atualizar(p.fotos, podeAdicionar = p.fotos.size < Profissional.MAX_FOTOS)
    }

    private fun escolherFotoGaleria() {
        escolhendoFotoPerfil = false
        seletorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun compartilhar() {
        val p = viewModel.perfil.value ?: return
        val texto = getString(R.string.painel_compartilhar_texto, p.nomeExibicao.orEmpty(), p.codigo.orEmpty())
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto), null))
    }

    // Serviço novo (posicao == null) ou edição de um existente.
    private fun editarServico(posicao: Int?, atual: Servico?) {
        val margem = (20 * resources.displayMetrics.density).toInt()
        fun campo(dica: Int, tipo: Int, valor: String?) = EditText(this).apply {
            setHint(dica)
            inputType = tipo
            setText(valor)
        }
        val nome = campo(R.string.servico_nome, android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, atual?.nome)
        val preco = campo(R.string.servico_preco, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL,
            atual?.preco?.let { String.format(java.util.Locale("pt", "BR"), "%.2f", it) })
        val duracao = campo(R.string.servico_duracao, android.text.InputType.TYPE_CLASS_NUMBER, atual?.duracaoMin?.takeIf { it > 0 }?.toString())
        val descricao = campo(R.string.servico_descricao, android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, atual?.descricao)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(margem, margem / 2, margem, 0)
            listOf(nome, preco, duracao, descricao).forEach { addView(it) }
        }
        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(if (atual == null) R.string.servico_novo else R.string.servico_editar)
            .setView(form)
            .setPositiveButton(R.string.salvar, null)
            .setNegativeButton(R.string.cancelar, null)
            .create()
        dialogo.setOnShowListener {
            dialogo.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val valor = Formato.lerValor(preco.text.toString())
                val minutos = duracao.text.toString().trim().toIntOrNull() ?: 0
                if (nome.text.isBlank() || valor == null || valor < 0) {
                    mostrarMensagem(texto(R.string.servico_erro))
                    return@setOnClickListener
                }
                viewModel.salvarServico(
                    posicao,
                    Servico(nome = nome.text.toString().trim(), preco = valor, duracaoMin = minutos, descricao = descricao.text.toString().trim().ifBlank { null })
                )
                dialogo.dismiss()
            }
        }
        dialogo.show()
    }

    private fun confirmarRemoverServico(posicao: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.servico_remover)
            .setMessage(R.string.servico_remover_msg)
            .setPositiveButton(R.string.remover) { _, _ -> viewModel.removerServico(posicao) }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun confirmarRemoverFoto(foto: FotoPortfolio) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.foto_remover)
            .setMessage(R.string.foto_remover_msg)
            .setPositiveButton(R.string.remover) { _, _ -> viewModel.removerFoto(foto) }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }
}
