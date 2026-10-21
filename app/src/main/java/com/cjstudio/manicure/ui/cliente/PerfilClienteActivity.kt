package com.cjstudio.manicure.ui.cliente

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.manicure.R
import com.cjstudio.manicure.databinding.ActivityPerfilClienteBinding
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.ui.common.texto
import com.cjstudio.manicure.util.FotoUtil
import com.cjstudio.manicure.util.ajustarPaddingParaTeclado
import com.cjstudio.manicure.util.carregarFotoRedonda
import com.cjstudio.manicure.util.mascaraTelefone
import dagger.hilt.android.AndroidEntryPoint

// "Meu perfil" da cliente (link na tela inicial): trocar foto, nome e telefone.
@AndroidEntryPoint
class PerfilClienteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPerfilClienteBinding
    private val viewModel: PerfilClienteViewModel by viewModels()
    private var preenchido = false

    private val seletorFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.trocarFoto(FotoUtil.prepararParaEnvio(this, uri) ?: uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerfilClienteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.scrollRoot.ajustarPaddingParaTeclado()
        binding.etTelefone.mascaraTelefone()
        // Girando a tela, os campos voltam sozinhos; não sobrescrever.
        preenchido = savedInstanceState != null

        binding.topo.tvTitulo.setText(R.string.cliente_perfil_titulo)
        binding.topo.btnVoltar.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        // Sem foto e vindo da tela inicial: só sai depois de pôr a foto.
        val fotoObrigatoria = intent.getBooleanExtra(EXTRA_FOTO_OBRIGATORIA, false)
        val semFotoTravado = object : OnBackPressedCallback(fotoObrigatoria) {
            override fun handleOnBackPressed() = mostrarMensagem(texto(R.string.cliente_foto_obrigatoria))
        }
        onBackPressedDispatcher.addCallback(this, semFotoTravado)
        binding.btnFoto.setOnClickListener {
            seletorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnSalvar.setOnClickListener {
            viewModel.salvar(binding.etNome.text.toString(), binding.etTelefone.text.toString())
        }

        coletar(viewModel.cliente) { c ->
            if (c == null) return@coletar
            binding.ivFoto.carregarFotoRedonda(c.fotoUrl)
            val semFoto = c.fotoUrl.isNullOrBlank()
            semFotoTravado.isEnabled = fotoObrigatoria && semFoto
            binding.tvAvisoFoto.visibility = if (fotoObrigatoria && semFoto) View.VISIBLE else View.GONE
            binding.tvEmail.text = getString(R.string.cliente_perfil_email, c.email.orEmpty())
            if (!preenchido) {
                preenchido = true
                binding.etNome.setText(c.nome)
                binding.etTelefone.setText(c.telefone)
            }
        }
        coletar(viewModel.ocupado) { ocupado ->
            binding.progressBar.visibility = if (ocupado) View.VISIBLE else View.GONE
            binding.btnSalvar.isEnabled = !ocupado
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                // Salvar sem ter posto a foto (modo obrigatório) não fecha a tela.
                PerfilClienteViewModel.Evento.Salvo -> if (!semFotoTravado.isEnabled) finish()
                is PerfilClienteViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    companion object {
        const val EXTRA_FOTO_OBRIGATORIA = "fotoObrigatoria"
    }
}
