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
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.model.TipoPerfil
import com.cjstudio.manicure.databinding.ActivityPerfilProfissionalBinding
import com.cjstudio.manicure.ui.common.FotosAdapter
import com.cjstudio.manicure.ui.common.ServicosAdapter
import com.cjstudio.manicure.ui.common.VisualizadorFoto
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.ui.profissional.ResumoPerfil
import com.cjstudio.manicure.util.carregarFotoRedonda
import dagger.hilt.android.AndroidEntryPoint

// Perfil público: apresentação, serviços com preço, galeria e contatos
// (WhatsApp, ligar, Instagram, ver no mapa).
@AndroidEntryPoint
class PerfilProfissionalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPerfilProfissionalBinding
    private val viewModel: PerfilProfissionalViewModel by viewModels()
    private val servicosAdapter = ServicosAdapter()
    private val fotosAdapter = FotosAdapter(aoTocar = { VisualizadorFoto.mostrar(this, it.url) })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerfilProfissionalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.rvServicos.layoutManager = LinearLayoutManager(this)
        binding.rvServicos.adapter = servicosAdapter
        binding.rvServicos.isNestedScrollingEnabled = false
        binding.rvFotos.layoutManager = GridLayoutManager(this, 3)
        binding.rvFotos.adapter = fotosAdapter
        binding.rvFotos.isNestedScrollingEnabled = false

        coletar(viewModel.perfil) { p -> if (p != null) mostrar(p) }
        coletar(viewModel.mensagens) { mostrarMensagem(it) }
    }

    private fun mostrar(p: Profissional) {
        binding.topo.tvTitulo.text = p.nomeExibicao
        binding.ivFoto.carregarFotoRedonda(p.fotoPerfilUrl)
        binding.tvNome.text = p.nomeExibicao
        binding.tvTipo.text = if (p.tipoPerfil == TipoPerfil.SALAO) {
            listOfNotNull(getString(R.string.perfil_tipo_salao), p.nomeResponsavel?.takeIf { it.isNotBlank() }).joinToString(" · ")
        } else {
            getString(R.string.perfil_tipo_pessoal)
        }
        binding.tvLocal.text = listOf(ResumoPerfil.local(p), ResumoPerfil.atendimento(this, p)).filter { it.isNotBlank() }.joinToString(" · ")

        mostrarTexto(binding.blocoApresentacao, binding.tvApresentacao, p.apresentacao)
        mostrarTexto(binding.blocoSobre, binding.tvSobreTrabalho, p.sobreTrabalho)

        servicosAdapter.atualizar(p.servicos)
        binding.blocoServicos.visibility = if (p.servicos.isEmpty()) View.GONE else View.VISIBLE

        fotosAdapter.atualizar(p.fotos, podeAdicionar = false)
        binding.blocoGaleria.visibility = if (p.fotos.isEmpty()) View.GONE else View.VISIBLE

        binding.tvContatos.text = ResumoPerfil.contatos(this, p)

        val whatsapp = p.whatsapp?.filter { it.isDigit() }.orEmpty()
        binding.btnWhatsapp.visibility = if (whatsapp.length >= 10) View.VISIBLE else View.GONE
        binding.btnWhatsapp.setOnClickListener { abrir("https://wa.me/55$whatsapp?text=" + Uri.encode(getString(R.string.perfil_whatsapp_msg))) }
        val telefone = p.telefone?.filter { it.isDigit() }.orEmpty()
        binding.btnLigar.visibility = if (telefone.length >= 10) View.VISIBLE else View.GONE
        binding.btnLigar.setOnClickListener { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$telefone"))) }
        binding.btnInstagram.visibility = if (p.instagram.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.btnInstagram.setOnClickListener { abrir("https://instagram.com/${p.instagram}") }
        val enderecoMapa = listOf(p.endereco, p.bairro, p.cidade).filter { !it.isNullOrBlank() }.joinToString(", ")
        binding.btnMapa.visibility = if (p.endereco.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.btnMapa.setOnClickListener { abrir("geo:0,0?q=" + Uri.encode(enderecoMapa)) }
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
    }
}
