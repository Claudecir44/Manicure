package com.cjstudio.manicure.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cjstudio.manicure.R
import com.cjstudio.manicure.databinding.ActivityLoginBinding
import com.cjstudio.manicure.ui.common.Navegacao
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.util.Papel
import com.cjstudio.manicure.util.Suporte
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
        binding.tvSuporte.setOnClickListener { mostrarSuporte() }

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

    // "Suporte" (canto de baixo, como no Caronas): senha esquecida, novo
    // e-mail de confirmação ou falar com a empresa por e-mail.
    private fun mostrarSuporte() {
        val opcoes = arrayOf(
            getString(R.string.suporte_esqueci_senha),
            getString(R.string.suporte_reenviar_confirmacao),
            getString(R.string.suporte_falar)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.suporte_botao)
            .setItems(opcoes) { _, i ->
                when (i) {
                    0 -> formularioSuporte(pedirSenha = false)
                    1 -> formularioSuporte(pedirSenha = true)
                    else -> {
                        val email = binding.etEmail.text.toString().trim()
                        val sou = getString(if (viewModel.papel == Papel.PROFISSIONAL) R.string.suporte_sou_profissional else R.string.suporte_sou_cliente)
                        Suporte.abrirEmail(this, getString(R.string.suporte_assunto_login), getString(R.string.suporte_corpo_login, email, sou))
                    }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // Esqueci a senha: só o e-mail. Reenviar confirmação: e-mail e senha
    // (entrar dispara o reenvio quando a conta ainda não foi confirmada).
    private fun formularioSuporte(pedirSenha: Boolean) {
        val d = resources.displayMetrics.density
        val explicacao = TextView(this).apply {
            setText(if (pedirSenha) R.string.suporte_reenviar_explicacao else R.string.suporte_esqueci_explicacao)
            setTextColor(ContextCompat.getColor(context, R.color.texto_secundario))
            textSize = 14f
            setPadding(0, 0, 0, (12 * d).toInt())
        }
        val email = EditText(this).apply {
            setHint(R.string.email)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(binding.etEmail.text.toString().trim())
        }
        val senha = EditText(this).apply {
            setHint(R.string.senha)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(binding.etSenha.text.toString())
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * d).toInt(), (8 * d).toInt(), (24 * d).toInt(), 0)
            addView(explicacao)
            addView(email)
            if (pedirSenha) addView(senha)
        }
        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(if (pedirSenha) R.string.suporte_reenviar_confirmacao else R.string.suporte_esqueci_senha)
            .setView(form)
            .setPositiveButton(R.string.suporte_enviar, null)
            .setNegativeButton(R.string.cancelar, null)
            .create()
        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val e = email.text.toString().trim()
                if (pedirSenha) {
                    binding.etEmail.setText(e)
                    binding.etSenha.setText(senha.text.toString())
                    viewModel.entrar(e, senha.text.toString())
                } else {
                    viewModel.recuperarSenha(e)
                }
                dialogo.dismiss()
            }
        }
        dialogo.show()
    }

    private fun avisarEmailNaoConfirmado(email: String, reenviado: Boolean) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.verificar_email_titulo)
            .setMessage(getString(if (reenviado) R.string.verificar_email_login else R.string.verificar_email_login_ja_enviado, email))
            .setPositiveButton(R.string.ok, null)
            .show()
    }
}
