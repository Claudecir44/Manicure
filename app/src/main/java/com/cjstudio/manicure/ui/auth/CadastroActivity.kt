package com.cjstudio.manicure.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.manicure.R
import com.cjstudio.manicure.databinding.ActivityCadastroBinding
import com.cjstudio.manicure.ui.common.Navegacao
import com.cjstudio.manicure.ui.common.TermosActivity
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.util.Papel
import com.cjstudio.manicure.util.ajustarPaddingParaTeclado
import com.cjstudio.manicure.util.habilitarToggleSenha
import com.cjstudio.manicure.util.mascaraTelefone
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CadastroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCadastroBinding
    private val viewModel: CadastroViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.scrollRoot.ajustarPaddingParaTeclado()
        binding.etTelefone.mascaraTelefone()
        binding.etSenha.habilitarToggleSenha()
        binding.etConfirmarSenha.habilitarToggleSenha()

        val profissional = viewModel.papel == Papel.PROFISSIONAL
        binding.topo.tvTitulo.setText(if (profissional) R.string.cadastro_titulo_profissional else R.string.cadastro_titulo_cliente)
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.tvExplicacao.setText(if (profissional) R.string.cadastro_explicacao_profissional else R.string.cadastro_explicacao_cliente)
        binding.etNome.setHint(if (profissional) R.string.cadastro_nome_profissional else R.string.nome_completo)
        binding.blocoConta.visibility = if (viewModel.completando) View.GONE else View.VISIBLE
        binding.tvTermos.setOnClickListener { startActivity(Intent(this, TermosActivity::class.java)) }
        binding.btnCadastrar.setOnClickListener {
            viewModel.cadastrar(
                CadastroViewModel.Formulario(
                    nome = binding.etNome.text.toString(),
                    telefone = binding.etTelefone.text.toString(),
                    email = binding.etEmail.text.toString(),
                    senha = binding.etSenha.text.toString(),
                    confirmarSenha = binding.etConfirmarSenha.text.toString(),
                    aceitouTermos = binding.cbTermos.isChecked
                )
            )
        }

        coletar(viewModel.enviando) { enviando ->
            binding.progressBar.visibility = if (enviando) View.VISIBLE else View.GONE
            binding.btnCadastrar.isEnabled = !enviando
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                is CadastroViewModel.Evento.ConfirmarEmail -> MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.verificar_email_titulo)
                    .setMessage(getString(R.string.verificar_email_cadastro, evento.email))
                    .setPositiveButton(R.string.ok) { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
                CadastroViewModel.Evento.IrParaInicio -> Navegacao.irLimpando(this, Navegacao.telaInicial(viewModel.papel))
                is CadastroViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }
}
