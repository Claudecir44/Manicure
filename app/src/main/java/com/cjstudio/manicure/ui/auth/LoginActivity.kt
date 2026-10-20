package com.cjstudio.manicure.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.manicure.R
import com.cjstudio.manicure.databinding.ActivityLoginBinding
import com.cjstudio.manicure.ui.common.Navegacao
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.util.Papel
import com.cjstudio.manicure.util.ajustarPaddingParaTeclado
import com.cjstudio.manicure.util.habilitarToggleSenha
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.scrollRoot.ajustarPaddingParaTeclado()
        binding.etSenha.habilitarToggleSenha()

        binding.tvSubtitulo.setText(
            if (viewModel.papel == Papel.PROFISSIONAL) R.string.login_subtitulo_profissional else R.string.login_subtitulo_cliente
        )
        binding.tvTrocarPapel.setOnClickListener { finish() }
        binding.btnEntrar.setOnClickListener {
            viewModel.entrar(binding.etEmail.text.toString().trim(), binding.etSenha.text.toString())
        }
        binding.tvCriarConta.setOnClickListener { abrirCadastro(completar = false) }
        binding.tvEsqueciSenha.setOnClickListener { viewModel.recuperarSenha(binding.etEmail.text.toString().trim()) }

        coletar(viewModel.carregando) { carregando ->
            binding.progressBar.visibility = if (carregando) View.VISIBLE else View.GONE
            binding.btnEntrar.isEnabled = !carregando
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                LoginViewModel.Evento.IrParaInicio -> Navegacao.irLimpando(this, Navegacao.telaInicial(viewModel.papel))
                LoginViewModel.Evento.CompletarCadastro -> perguntarCompletarCadastro()
                is LoginViewModel.Evento.EmailNaoConfirmado -> avisarEmailNaoConfirmado(evento.email, evento.reenviado)
                is LoginViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    private fun abrirCadastro(completar: Boolean) {
        startActivity(
            Intent(this, CadastroActivity::class.java)
                .putExtra(LoginViewModel.EXTRA_PAPEL, viewModel.papel.name)
                .putExtra(LoginViewModel.EXTRA_COMPLETAR_CADASTRO, completar)
        )
    }

    // A conta existe (ex.: já é cliente) mas não tem perfil neste papel.
    private fun perguntarCompletarCadastro() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.login_completar_titulo)
            .setMessage(
                if (viewModel.papel == Papel.PROFISSIONAL) R.string.login_completar_profissional else R.string.login_completar_cliente
            )
            .setPositiveButton(R.string.login_completar_sim) { _, _ -> abrirCadastro(completar = true) }
            .setNegativeButton(R.string.cancelar) { _, _ -> viewModel.sairDaConta() }
            .setCancelable(false)
            .show()
    }

    private fun avisarEmailNaoConfirmado(email: String, reenviado: Boolean) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.verificar_email_titulo)
            .setMessage(getString(if (reenviado) R.string.verificar_email_login else R.string.verificar_email_login_ja_enviado, email))
            .setPositiveButton(R.string.ok, null)
            .show()
    }
}
