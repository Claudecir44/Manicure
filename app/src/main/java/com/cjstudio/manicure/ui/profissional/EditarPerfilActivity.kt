package com.cjstudio.manicure.ui.profissional

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Categorias
import com.cjstudio.manicure.data.model.FotoPortfolio
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.model.Servico
import com.cjstudio.manicure.data.model.TipoAtendimento
import com.cjstudio.manicure.data.model.TipoPerfil
import com.cjstudio.manicure.databinding.ActivityEditarPerfilBinding
import com.cjstudio.manicure.ui.common.FotosAdapter
import com.cjstudio.manicure.ui.common.ServicosAdapter
import com.cjstudio.manicure.ui.common.VisualizadorFoto
import com.cjstudio.manicure.ui.common.adicionarChip
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.ui.common.texto
import com.cjstudio.manicure.util.Aparencia
import com.cjstudio.manicure.util.FotoUtil
import com.cjstudio.manicure.util.Formato
import com.cjstudio.manicure.util.ajustarPaddingParaTeclado
import com.cjstudio.manicure.util.carregarFotoRedonda
import com.cjstudio.manicure.util.mascaraTelefone
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

// Meu perfil: tudo do perfil da manicure numa tela só. Abre para ver;
// "Editar" libera os campos e as categorias, "Salvar" grava e volta a ver.
@AndroidEntryPoint
class EditarPerfilActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditarPerfilBinding
    private val viewModel: EditarPerfilViewModel by viewModels()

    private var editando = false
    private var categorias = mutableSetOf<String>()

    private val servicosAdapter = ServicosAdapter(
        aoTocar = { posicao, servico -> if (editando) editarServico(posicao, servico) },
        aoSegurar = { posicao -> if (editando) confirmarRemoverServico(posicao) }
    )
    private val fotosAdapter = FotosAdapter(
        aoTocar = { VisualizadorFoto.mostrar(this, it.url) },
        aoSegurar = { if (editando) confirmarRemoverFoto(it) },
        aoAdicionar = { escolherFoto(perfil = false) }
    )

    // Qual foto o seletor vai preencher: a do perfil ou uma da galeria.
    private var escolhendoFotoPerfil = false
    private val seletorFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        val pronta = FotoUtil.prepararParaEnvio(this, uri) ?: uri
        if (escolhendoFotoPerfil) viewModel.trocarFotoPerfil(pronta) else viewModel.adicionarFoto(pronta)
    }

    // Voltar no modo edição pergunta antes de descartar.
    private val voltarEditando = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = confirmarDescartar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Aparencia.aplicar(this)
        super.onCreate(savedInstanceState)
        binding = ActivityEditarPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.scrollRoot.ajustarPaddingParaTeclado()
        binding.etWhatsapp.mascaraTelefone()
        binding.etTelefone.mascaraTelefone()
        onBackPressedDispatcher.addCallback(this, voltarEditando)

        binding.rvServicos.layoutManager = LinearLayoutManager(this)
        binding.rvServicos.adapter = servicosAdapter
        binding.rvServicos.isNestedScrollingEnabled = false
        binding.rvFotos.layoutManager = GridLayoutManager(this, 3)
        binding.rvFotos.adapter = fotosAdapter
        binding.rvFotos.isNestedScrollingEnabled = false

        binding.topo.tvTitulo.setText(R.string.editar_titulo)
        binding.topo.btnVoltar.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.rgTipo.setOnCheckedChangeListener { _, _ -> atualizarDicaNome() }
        binding.btnEditar.setOnClickListener { definirEdicao(true) }
        binding.btnCancelar.setOnClickListener { cancelarEdicao() }
        binding.btnSalvar.setOnClickListener { salvar() }
        binding.btnAdicionarServico.setOnClickListener { editarServico(null, null) }
        binding.ivFoto.setOnClickListener { if (editando) escolherFoto(perfil = true) }

        // Depois de girar a tela, os campos já voltam sozinhos; só o modo e as categorias precisam.
        if (savedInstanceState != null) {
            editando = savedInstanceState.getBoolean(ESTADO_EDITANDO)
            categorias = savedInstanceState.getStringArrayList(ESTADO_CATEGORIAS).orEmpty().toMutableSet()
        }
        definirEdicao(editando)

        coletar(viewModel.perfil) { p -> if (p != null) mostrar(p) }
        coletar(viewModel.salvando) { salvando ->
            binding.progressBar.visibility = if (salvando) View.VISIBLE else View.GONE
            binding.btnSalvar.isEnabled = !salvando
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                EditarPerfilViewModel.Evento.Salvo -> {
                    definirEdicao(false)
                    viewModel.perfil.value?.let { preencher(it) }
                }
                is EditarPerfilViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ESTADO_EDITANDO, editando)
        outState.putStringArrayList(ESTADO_CATEGORIAS, ArrayList(categorias))
    }

    // Foto, serviços e galeria sempre acompanham o banco; o formulário só
    // é preenchido fora do modo edição (pra não apagar o que ela está digitando).
    private fun mostrar(p: Profissional) {
        binding.ivFoto.carregarFotoRedonda(p.fotoPerfilUrl)
        servicosAdapter.atualizar(p.servicos)
        binding.tvSemServicos.visibility = if (p.servicos.isEmpty()) View.VISIBLE else View.GONE
        binding.tvTituloGaleria.text = getString(R.string.painel_galeria, p.fotos.size, Profissional.MAX_FOTOS)
        atualizarGaleria(p)
        if (!editando) preencher(p)
    }

    private fun atualizarGaleria(p: Profissional? = viewModel.perfil.value) {
        val fotos = p?.fotos.orEmpty()
        fotosAdapter.atualizar(fotos, podeAdicionar = editando && fotos.size < Profissional.MAX_FOTOS)
    }

    private fun preencher(p: Profissional) {
        binding.rgTipo.check(if (p.tipoPerfil == TipoPerfil.SALAO) R.id.rbSalao else R.id.rbPessoal)
        binding.etNomeExibicao.setText(p.nomeExibicao)
        binding.etNomeResponsavel.setText(p.nomeResponsavel)
        binding.etApresentacao.setText(p.apresentacao)
        binding.etSobreTrabalho.setText(p.sobreTrabalho)
        binding.etWhatsapp.setText(p.whatsapp)
        binding.etTelefone.setText(p.telefone)
        binding.etInstagram.setText(p.instagram)
        binding.etCidade.setText(p.cidade)
        binding.etBairro.setText(p.bairro)
        binding.etEndereco.setText(p.endereco)
        binding.etHorario.setText(p.horarioAtendimento)
        binding.rgAtendimento.check(
            when (p.atendimento) {
                TipoAtendimento.DOMICILIO -> R.id.rbDomicilio
                TipoAtendimento.AMBOS -> R.id.rbAmbos
                else -> R.id.rbNoSalao
            }
        )
        categorias = p.categorias.toMutableSet()
        mostrarCategorias()
        atualizarDicaNome()
    }

    // Vendo: só as marcadas. Editando: todas, pra marcar e desmarcar.
    private fun mostrarCategorias() {
        val grupo = binding.cgCategorias
        grupo.removeAllViews()
        Categorias.TODAS
            .filter { editando || it.first in categorias }
            .forEach { (chave, nome) ->
                val chip = grupo.adicionarChip(nome, chave, chave in categorias)
                chip.isClickable = editando
                chip.setOnCheckedChangeListener { _, marcada -> if (marcada) categorias += chave else categorias -= chave }
            }
        binding.tvSemCategorias.visibility = if (!editando && categorias.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun definirEdicao(ligar: Boolean) {
        editando = ligar
        voltarEditando.isEnabled = ligar
        listOf(
            binding.etNomeExibicao, binding.etNomeResponsavel, binding.etApresentacao, binding.etSobreTrabalho,
            binding.etWhatsapp, binding.etTelefone, binding.etInstagram, binding.etCidade, binding.etBairro,
            binding.etEndereco, binding.etHorario
        ).forEach { it.isEnabled = ligar }
        habilitarFilhos(binding.rgTipo, ligar)
        habilitarFilhos(binding.rgAtendimento, ligar)

        binding.btnEditar.visibility = if (ligar) View.GONE else View.VISIBLE
        binding.barraEdicao.visibility = if (ligar) View.VISIBLE else View.GONE
        binding.ivSeloCamera.visibility = if (ligar) View.VISIBLE else View.GONE
        binding.btnAdicionarServico.visibility = if (ligar) View.VISIBLE else View.GONE
        binding.tvDicaServicos.visibility = if (ligar) View.VISIBLE else View.GONE
        binding.tvDicaGaleria.visibility = if (ligar) View.VISIBLE else View.GONE
        binding.tvModo.setText(if (ligar) R.string.editar_modo_editando else R.string.editar_modo_visualizar)
        mostrarCategorias()
        atualizarGaleria()
    }

    private fun habilitarFilhos(grupo: ViewGroup, ligar: Boolean) {
        for (i in 0 until grupo.childCount) grupo.getChildAt(i).isEnabled = ligar
    }

    private fun cancelarEdicao() {
        definirEdicao(false)
        viewModel.perfil.value?.let { preencher(it) }
    }

    private fun confirmarDescartar() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.editar_descartar)
            .setMessage(R.string.editar_descartar_msg)
            .setPositiveButton(R.string.editar_descartar_sim) { _, _ -> cancelarEdicao() }
            .setNegativeButton(R.string.editar_continuar, null)
            .show()
    }

    // "Seu nome" ou "Nome do salão", conforme o tipo de perfil.
    private fun atualizarDicaNome() {
        val salao = binding.rgTipo.checkedRadioButtonId == R.id.rbSalao
        binding.etNomeExibicao.setHint(if (salao) R.string.editar_nome_salao else R.string.editar_nome_pessoal)
        binding.etNomeResponsavel.visibility = if (salao) View.VISIBLE else View.GONE
    }

    private fun salvar() {
        viewModel.salvar(
            EditarPerfilViewModel.Formulario(
                nomeExibicao = binding.etNomeExibicao.text.toString(),
                tipoPerfil = if (binding.rgTipo.checkedRadioButtonId == R.id.rbSalao) TipoPerfil.SALAO else TipoPerfil.PESSOAL,
                nomeResponsavel = binding.etNomeResponsavel.text.toString(),
                apresentacao = binding.etApresentacao.text.toString(),
                sobreTrabalho = binding.etSobreTrabalho.text.toString(),
                whatsapp = binding.etWhatsapp.text.toString(),
                telefone = binding.etTelefone.text.toString(),
                instagram = binding.etInstagram.text.toString(),
                cidade = binding.etCidade.text.toString(),
                bairro = binding.etBairro.text.toString(),
                endereco = binding.etEndereco.text.toString(),
                horarioAtendimento = binding.etHorario.text.toString(),
                atendimento = when (binding.rgAtendimento.checkedRadioButtonId) {
                    R.id.rbDomicilio -> TipoAtendimento.DOMICILIO
                    R.id.rbAmbos -> TipoAtendimento.AMBOS
                    else -> TipoAtendimento.SALAO
                },
                // Na ordem da lista de categorias, como antes.
                categorias = Categorias.TODAS.map { it.first }.filter { it in categorias }
            )
        )
    }

    private fun escolherFoto(perfil: Boolean) {
        escolhendoFotoPerfil = perfil
        seletorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // Serviço novo (posicao == null) ou edição de um existente.
    private fun editarServico(posicao: Int?, atual: Servico?) {
        val margem = (20 * resources.displayMetrics.density).toInt()
        fun campo(dica: Int, tipo: Int, valor: String?) = EditText(this).apply {
            setHint(dica)
            inputType = tipo
            setText(valor)
        }
        val nome = campo(R.string.servico_nome, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, atual?.nome)
        val preco = campo(R.string.servico_preco, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            atual?.preco?.let { String.format(Locale("pt", "BR"), "%.2f", it) })
        val duracao = campo(R.string.servico_duracao, InputType.TYPE_CLASS_NUMBER, atual?.duracaoMin?.takeIf { it > 0 }?.toString())
        val descricao = campo(R.string.servico_descricao, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, atual?.descricao)
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
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
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

    private companion object {
        const val ESTADO_EDITANDO = "editando"
        const val ESTADO_CATEGORIAS = "categorias"
    }
}
