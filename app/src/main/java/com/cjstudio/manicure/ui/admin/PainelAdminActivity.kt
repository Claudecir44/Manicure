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
import com.cjstudio.manicure.ui.common.pedirSenha
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
                is PainelAdminViewModel.Evento.OpcoesAdmin -> mostrarOpcoesAdmin(evento.alvo)
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

    // Profissional: ver o perfil público, bloquear ou excluir. Cliente:
    // bloquear ou excluir.
    private fun mostrarOpcoes(alvo: PainelAdminViewModel.Alvo) {
        val verPerfil: () -> Unit = {
            startActivity(Intent(this, PerfilProfissionalActivity::class.java).putExtra(PerfilProfissionalActivity.EXTRA_ID, alvo.id))
        }
        val opcoes = listOfNotNull(
            if (alvo.profissional) getString(R.string.admin_ver_perfil) to verPerfil else null,
            getString(if (alvo.bloqueado) R.string.admin_desbloquear else R.string.admin_bloquear) to { confirmarBloqueio(alvo) },
            getString(R.string.admin_excluir_cadastro) to { confirmarExclusao(alvo) }
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(alvo.nome)
            .setItems(opcoes.map { it.first }.toTypedArray()) { _, i -> opcoes[i].second() }
            .show()
    }

    private fun confirmarExclusao(alvo: PainelAdminViewModel.Alvo) {
        pedirSenha(
            titulo = getString(R.string.admin_excluir_cadastro),
            mensagem = getString(
                if (alvo.profissional) R.string.admin_excluir_profissional_msg else R.string.admin_excluir_cliente_msg,
                alvo.nome
            ),
            dica = R.string.admin_senha_master_ou_sua,
            botao = R.string.excluir
        ) { senha -> viewModel.excluirCadastro(alvo, senha) }
    }

    // Admin: editar ou excluir. O master (e o próprio admin logado) nunca é
    // excluído: a opção aparece, mas só explica por quê (o servidor também recusa).
    private fun mostrarOpcoesAdmin(alvo: PainelAdminViewModel.AlvoAdmin) {
        val editar: () -> Unit = {
            startActivity(Intent(this, CadastroAdminActivity::class.java).putExtra(CadastroAdminActivity.EXTRA_ID, alvo.id))
        }
        val excluir: () -> Unit = {
            val motivo = when {
                alvo.master -> R.string.admin_master_protegido
                alvo.souEu -> R.string.admin_nao_exclui_a_si
                else -> null
            }
            if (motivo != null) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.admin_excluir_admin)
                    .setMessage(motivo)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } else {
                confirmarExclusaoAdmin(alvo)
            }
        }
        val opcoes = listOf(
            getString(R.string.admin_editar) to editar,
            getString(if (alvo.master) R.string.admin_excluir_admin_bloqueado else R.string.admin_excluir_admin) to excluir
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(alvo.nome)
            .setItems(opcoes.map { it.first }.toTypedArray()) { _, i -> opcoes[i].second() }
            .show()
    }

    private fun confirmarExclusaoAdmin(alvo: PainelAdminViewModel.AlvoAdmin) {
        pedirSenha(
            titulo = getString(R.string.admin_excluir_admin),
            mensagem = getString(R.string.admin_excluir_admin_msg, alvo.nome),
            dica = R.string.admin_senha_master,
            botao = R.string.excluir
        ) { senha -> viewModel.excluirAdmin(alvo, senha) }
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
