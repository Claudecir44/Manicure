package com.cjstudio.manicure.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.manicure.R
import com.cjstudio.manicure.databinding.ActivityPainelAdminBinding
import com.cjstudio.manicure.ui.admin.PainelAdminViewModel.Secao
import com.cjstudio.manicure.ui.common.Navegacao
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.ui.perfil.PerfilProfissionalActivity
import com.cjstudio.manicure.util.FotoUtil
import com.cjstudio.manicure.util.carregarFotoRedonda
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PainelAdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPainelAdminBinding
    private val viewModel: PainelAdminViewModel by viewModels()
    private val adapter = ItemPessoaAdapter { item -> viewModel.tocou(item.id) }

    private val seletorFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.trocarMinhaFoto(FotoUtil.prepararParaEnvio(this, uri) ?: uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPainelAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvItens.layoutManager = LinearLayoutManager(this)
        binding.rvItens.adapter = adapter

        binding.ivFoto.setOnClickListener {
            seletorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnSair.setOnClickListener { viewModel.sair() }
        binding.btnProfissionais.setOnClickListener { viewModel.abrir(Secao.PROFISSIONAIS) }
        binding.btnClientes.setOnClickListener { viewModel.abrir(Secao.CLIENTES) }
        binding.btnAdministradores.setOnClickListener { viewModel.abrir(Secao.ADMINISTRADORES) }
        binding.btnNovoAdmin.setOnClickListener { startActivity(Intent(this, CadastroAdminActivity::class.java)) }

        coletar(viewModel.estado) { mostrar(it) }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                PainelAdminViewModel.Evento.Saiu -> Navegacao.irLimpando(this, Navegacao.telaLogin())
                is PainelAdminViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
                is PainelAdminViewModel.Evento.Opcoes -> mostrarOpcoes(evento.alvo)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.atualizar()
    }

    private fun mostrar(estado: PainelAdminViewModel.Estado) {
        estado.admin?.let { admin ->
            binding.tvNome.text = admin.nome?.ifBlank { null } ?: admin.email.orEmpty()
            binding.tvPapel.setText(
                when {
                    admin.ehMaster -> R.string.admin_selo_master
                    admin.ehColaborador -> R.string.admin_selo_colaborador
                    else -> R.string.admin_selo_admin
                }
            )
            binding.ivFoto.carregarFotoRedonda(admin.fotoUrl)
            // Botão só aparece com a permissão (a trava de verdade é das regras).
            binding.btnProfissionais.visibility = visivel(admin.temPermissao("profissionais"))
            binding.btnClientes.visibility = visivel(admin.temPermissao("clientes"))
            binding.btnAdministradores.visibility = visivel(admin.temPermissao("administradores"))
        }

        val secao = estado.secao
        binding.areaSecao.visibility = visivel(secao != null)
        binding.tvDica.visibility = visivel(secao == null)
        if (secao == null) return

        binding.tvTituloSecao.setText(
            when (secao) {
                Secao.PROFISSIONAIS -> R.string.admin_profissionais
                Secao.CLIENTES -> R.string.admin_clientes
                Secao.ADMINISTRADORES -> R.string.admin_administradores
            }
        )
        binding.tvContador.text = resources.getQuantityString(R.plurals.admin_contador, estado.itens.size, estado.itens.size)
        binding.btnNovoAdmin.visibility = visivel(secao == Secao.ADMINISTRADORES)
        binding.progressBar.visibility = visivel(estado.carregando)
        binding.tvVazio.visibility = visivel(!estado.carregando && estado.itens.isEmpty())
        adapter.submitList(estado.itens)
    }

    // Profissional: ver o perfil público ou bloquear. Cliente: só bloquear.
    private fun mostrarOpcoes(alvo: PainelAdminViewModel.Alvo) {
        val bloquear = getString(if (alvo.bloqueado) R.string.admin_desbloquear else R.string.admin_bloquear)
        val opcoes = if (alvo.profissional) arrayOf(getString(R.string.admin_ver_perfil), bloquear) else arrayOf(bloquear)
        MaterialAlertDialogBuilder(this)
            .setTitle(alvo.nome)
            .setItems(opcoes) { _, i ->
                if (alvo.profissional && i == 0) {
                    startActivity(Intent(this, PerfilProfissionalActivity::class.java).putExtra(PerfilProfissionalActivity.EXTRA_ID, alvo.id))
                } else {
                    confirmarBloqueio(alvo)
                }
            }
            .show()
    }

    private fun confirmarBloqueio(alvo: PainelAdminViewModel.Alvo) {
        MaterialAlertDialogBuilder(this)
            .setTitle(alvo.nome)
            .setMessage(getString(if (alvo.bloqueado) R.string.admin_desbloquear_msg else R.string.admin_bloquear_msg, alvo.nome))
            .setPositiveButton(if (alvo.bloqueado) R.string.admin_desbloquear else R.string.admin_bloquear) { _, _ ->
                viewModel.alternarBloqueio(alvo)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun visivel(mostrar: Boolean) = if (mostrar) View.VISIBLE else View.GONE
}
