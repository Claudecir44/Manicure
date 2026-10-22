package com.cjstudio.manicure.ui.perfil

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Agendamento
import com.cjstudio.manicure.data.model.Categorias
import com.cjstudio.manicure.ui.chat.ChatActivity
import com.cjstudio.manicure.util.AgendaUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.ui.agenda.AgendarActivity
import com.cjstudio.manicure.ui.common.adicionarChip
import com.cjstudio.manicure.data.model.TipoPerfil
import com.cjstudio.manicure.databinding.ActivityPerfilProfissionalBinding
import com.cjstudio.manicure.databinding.ItemInfoPerfilBinding
import com.cjstudio.manicure.data.model.TipoAtendimento
import com.cjstudio.manicure.util.Formato
import com.cjstudio.manicure.ui.common.FotosAdapter
import com.cjstudio.manicure.ui.common.ServicosAdapter
import com.cjstudio.manicure.ui.common.VisualizadorFoto
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.ui.profissional.ResumoPerfil
import com.cjstudio.manicure.ui.common.DialogoCores
import com.cjstudio.manicure.util.Aparencia
import com.cjstudio.manicure.util.carregarFotoRedonda
import dagger.hilt.android.AndroidEntryPoint

// Perfil público: capa com foto e atalhos (chat, ligar, Instagram, mapa),
// agendar, apresentação, serviços com preço, galeria e, no fim, as
// Informações (endereço, contatos), que é o único lugar com o endereço.
@AndroidEntryPoint
class PerfilProfissionalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPerfilProfissionalBinding
    private val viewModel: PerfilProfissionalViewModel by viewModels()
    private val servicosAdapter = ServicosAdapter()
    private val fotosAdapter = FotosAdapter(aoTocar = { VisualizadorFoto.mostrar(this, it.url) })

    // Cor que esta tela abriu (Profissional.corPerfil; "" = rosa padrão).
    private var corAplicada = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        // O tema sai antes do perfil chegar: usa a cor da última visita e, se
        // a do banco for outra, mostrar() refaz a tela uma vez já na cor certa.
        corAplicada = intent.getStringExtra(EXTRA_COR)
            ?: Aparencia.corPerfilGuardada(this, intent.getStringExtra(EXTRA_ID).orEmpty()).orEmpty()
        setTheme(Aparencia.temaPerfil(corAplicada))
        super.onCreate(savedInstanceState)
        binding = ActivityPerfilProfissionalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVoltar.setOnClickListener { finish() }
        binding.rvServicos.layoutManager = LinearLayoutManager(this)
        binding.rvServicos.adapter = servicosAdapter
        binding.rvServicos.isNestedScrollingEnabled = false
        binding.rvFotos.layoutManager = GridLayoutManager(this, 3)
        binding.rvFotos.adapter = fotosAdapter
        binding.rvFotos.isNestedScrollingEnabled = false

        coletar(viewModel.perfil) { p -> if (p != null) mostrar(p) }
        coletar(viewModel.mensagens) { mostrarMensagem(it) }
        coletar(viewModel.preferido) { binding.tvPreferido.visibility = if (it) View.VISIBLE else View.GONE }
        binding.tvPreferido.setOnClickListener { viewModel.deixarDePreferir() }
        coletar(viewModel.categoriasComAgenda) { mostrarCategorias(it) }
        coletar(viewModel.meusHorarios) { mostrarLembrete(it) }
    }

    private fun mostrar(p: Profissional) {
        val cor = p.corPerfil.orEmpty()
        p.id?.let { Aparencia.guardarCorPerfil(this, it, cor) }
        if (cor != corAplicada) {
            intent.putExtra(EXTRA_COR, cor)
            recreate()
            return
        }

        binding.ivFoto.carregarFotoRedonda(p.fotoPerfilUrl)
        binding.tvNome.text = p.nomeExibicao
        // "Ver como cliente": a dona escolhe aqui a cor que as clientes veem.
        binding.btnAparencia.visibility = if (p.id == viewModel.meuId) View.VISIBLE else View.GONE
        binding.btnAparencia.setOnClickListener {
            DialogoCores.mostrar(this, R.string.aparencia_perfil_titulo, R.string.aparencia_perfil_dica,
                Aparencia.corPerfil(cor) ?: Aparencia.corPerfil("rosa")) { escolhida -> viewModel.salvarCor(escolhida.chave) }
        }
        binding.tvTipo.text = if (p.tipoPerfil == TipoPerfil.SALAO) {
            listOfNotNull(getString(R.string.perfil_tipo_salao), p.nomeResponsavel?.takeIf { it.isNotBlank() }).joinToString(" · ")
        } else {
            getString(R.string.perfil_tipo_pessoal)
        }
        // Canto superior direito: como ela atende.
        binding.tvAtendimento.text = ResumoPerfil.atendimento(this, p)
        val icone = androidx.core.content.ContextCompat.getDrawable(
            this, if (p.atendimento == TipoAtendimento.DOMICILIO) R.drawable.ic_local else R.drawable.ic_casa
        )?.mutate()?.apply { setBounds(0, 0, dp(15), dp(15)) }
        binding.tvAtendimento.setCompoundDrawablesRelative(icone, null, null, null)

        mostrarTexto(binding.blocoApresentacao, binding.tvApresentacao, p.apresentacao)
        mostrarTexto(binding.blocoSobre, binding.tvSobreTrabalho, p.sobreTrabalho)

        servicosAdapter.atualizar(p.servicos)
        binding.blocoServicos.visibility = if (p.servicos.isEmpty()) View.GONE else View.VISIBLE

        fotosAdapter.atualizar(p.fotos, podeAdicionar = false)
        binding.blocoGaleria.visibility = if (p.fotos.isEmpty()) View.GONE else View.VISIBLE
        binding.tvTituloGaleria.text = getString(R.string.perfil_galeria_qtd, p.fotos.size)

        mostrarInformacoes(p)

        // Chat dentro do app (a própria profissional olhando o perfil não conversa consigo).
        binding.btnChat.visibility = if (p.id == viewModel.meuId) View.GONE else View.VISIBLE
        binding.btnChat.setOnClickListener { abrirChat(p) }
        val telefone = p.telefone?.filter { it.isDigit() }.orEmpty()
        binding.btnLigar.visibility = if (telefone.length >= 10) View.VISIBLE else View.GONE
        binding.btnLigar.setOnClickListener { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$telefone"))) }
        binding.btnInstagram.visibility = if (p.instagram.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.btnInstagram.setOnClickListener { abrir("https://instagram.com/${p.instagram}") }
        val enderecoMapa = listOf(p.endereco, p.bairro, p.cidade).filter { !it.isNullOrBlank() }.joinToString(", ")
        binding.btnMapa.visibility = if (p.endereco.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.btnMapa.setOnClickListener { abrir("geo:0,0?q=" + Uri.encode(enderecoMapa)) }
    }

    // Informações: uma linha com ícone por dado preenchido. Endereço abre o
    // mapa, WhatsApp abre a conversa e Instagram abre o perfil.
    private fun mostrarInformacoes(p: Profissional) {
        binding.llInfo.removeAllViews()
        val enderecoMapa = listOf(p.endereco, p.bairro, p.cidade).filter { !it.isNullOrBlank() }.joinToString(", ")
        p.endereco?.takeIf { it.isNotBlank() }?.let { e ->
            linhaInfo(R.drawable.ic_local, R.string.info_endereco, e) { abrir("geo:0,0?q=" + Uri.encode(enderecoMapa)) }
        }
        ResumoPerfil.local(p).takeIf { it.isNotBlank() }?.let { linhaInfo(R.drawable.ic_local, R.string.info_local, it) }
        linhaInfo(R.drawable.ic_casa, R.string.info_atendimento, ResumoPerfil.atendimento(this, p))
        p.horarioAtendimento?.takeIf { it.isNotBlank() }?.let { linhaInfo(R.drawable.ic_relogio, R.string.info_horario, it) }
        p.whatsapp?.takeIf { it.isNotBlank() }?.let { w ->
            val numero = w.filter { it.isDigit() }.let { if (it.length in 10..11) "55$it" else it }
            linhaInfo(R.drawable.ic_chat, R.string.info_whatsapp, w) { abrir("https://wa.me/$numero") }
        }
        p.telefone?.takeIf { it.isNotBlank() && it != p.whatsapp }?.let { t ->
            linhaInfo(R.drawable.ic_telefone, R.string.info_telefone, t) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + t.filter { it.isDigit() })))
            }
        }
        p.instagram?.takeIf { it.isNotBlank() }?.let { i ->
            linhaInfo(R.drawable.ic_instagram, R.string.info_instagram, "@$i") { abrir("https://instagram.com/$i") }
        }
    }

    private fun linhaInfo(icone: Int, rotulo: Int, valor: String, aoTocar: (() -> Unit)? = null) {
        val linha = ItemInfoPerfilBinding.inflate(layoutInflater, binding.llInfo, true)
        linha.ivIcone.setImageResource(icone)
        linha.tvRotulo.setText(rotulo)
        linha.tvValor.text = valor
        linha.tvSeta.visibility = if (aoTocar != null) View.VISIBLE else View.GONE
        if (aoTocar != null) linha.root.setOnClickListener { aoTocar() } else linha.root.isClickable = false
    }

    private fun dp(valor: Int) = (valor * resources.displayMetrics.density).toInt()

    // Um botão por categoria com horário livre -> tela de agendar.
    private fun mostrarCategorias(categorias: List<String>) {
        atualizarBlocoAgendar()
        binding.cgCategorias.removeAllViews()
        categorias.forEach { c ->
            val chip = binding.cgCategorias.adicionarChip(Categorias.nome(c), c)
            chip.isCheckable = false
            chip.setOnClickListener {
                startActivity(
                    Intent(this, AgendarActivity::class.java)
                        .putExtra(AgendarActivity.EXTRA_PROFISSIONAL, intent.getStringExtra(EXTRA_ID))
                        .putExtra(AgendarActivity.EXTRA_NOME, viewModel.perfil.value?.nomeExibicao.orEmpty())
                        .putExtra(AgendarActivity.EXTRA_CATEGORIA, c)
                )
            }
        }
    }

    // Lembrete do(s) horário(s) que a cliente marcou aqui, com "Cancelar".
    private fun mostrarLembrete(lista: List<Agendamento>) {
        binding.blocoLembrete.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
        binding.tvLembrete.text = lista.joinToString("\n") {
            getString(R.string.perfil_lembrete_item, AgendaUtil.descrever(it.horario), Categorias.nome(it.categoria), it.atendente.orEmpty())
        }
        binding.tvCancelarHorario.setOnClickListener { escolherCancelamento(lista) }
        atualizarBlocoAgendar()
    }

    private fun atualizarBlocoAgendar() {
        val tem = viewModel.categoriasComAgenda.value.isNotEmpty() || viewModel.meusHorarios.value.isNotEmpty()
        binding.blocoAgendar.visibility = if (tem) View.VISIBLE else View.GONE
    }

    private fun escolherCancelamento(lista: List<Agendamento>) {
        if (lista.size == 1) {
            confirmarCancelamento(lista.first())
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.agendar_qual_cancelar)
            .setItems(lista.map { AgendaUtil.descrever(it.horario) }.toTypedArray()) { _, i -> confirmarCancelamento(lista[i]) }
            .show()
    }

    private fun confirmarCancelamento(m: Agendamento) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.agendar_cancelar_titulo)
            .setMessage(getString(R.string.agendar_cancelar_msg, AgendaUtil.descrever(m.horario)))
            .setPositiveButton(R.string.agendar_cancelar_sim) { _, _ -> viewModel.cancelarHorario(m) }
            .setNegativeButton(R.string.voltar, null)
            .show()
    }

    private fun abrirChat(p: Profissional) {
        val meuId = viewModel.meuId ?: return
        startActivity(
            Intent(this, ChatActivity::class.java)
                .putExtra(ChatActivity.EXTRA_PROFISSIONAL_ID, p.id)
                .putExtra(ChatActivity.EXTRA_PROFISSIONAL_NOME, p.nomeExibicao.orEmpty())
                .putExtra(ChatActivity.EXTRA_PROFISSIONAL_FOTO, p.fotoPerfilUrl)
                .putExtra(ChatActivity.EXTRA_CLIENTE_ID, meuId)
                .putExtra(ChatActivity.EXTRA_CLIENTE_NOME, viewModel.nomeParaChat())
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.atualizarAgendas()
    }

    private fun mostrarTexto(bloco: View, campo: android.widget.TextView, texto: String?) {
        bloco.visibility = if (texto.isNullOrBlank()) View.GONE else View.VISIBLE
        campo.text = texto
    }

    private fun abrir(endereco: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(endereco))) }
    }

    companion object {
        const val EXTRA_ID = "profissionalId"
        private const val EXTRA_COR = "corPerfil"
    }
}
