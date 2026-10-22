package com.cjstudio.manicure.ui.profissional

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.manicure.BuildConfig
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.repository.TipoCadastro
import com.cjstudio.manicure.databinding.ActivityConfiguracoesBinding
import com.cjstudio.manicure.ui.common.ExcluirCadastroViewModel
import com.cjstudio.manicure.ui.common.Navegacao
import com.cjstudio.manicure.ui.common.TermosActivity
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.ui.common.pedirSenha
import com.cjstudio.manicure.ui.common.texto
import com.cjstudio.manicure.util.Aparencia
import com.cjstudio.manicure.util.Suporte
import dagger.hilt.android.AndroidEntryPoint

// Configurações da profissional (mesmo jeito do Caronas): instruções do app,
// termos, contato com o suporte e excluir o cadastro.
@AndroidEntryPoint
class ConfiguracoesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfiguracoesBinding
    private val excluir: ExcluirCadastroViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        Aparencia.aplicar(this)
        super.onCreate(savedInstanceState)
        binding = ActivityConfiguracoesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topo.tvTitulo.setText(R.string.config_titulo)
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.btnInstrucoes.setOnClickListener { startActivity(Intent(this, InstrucoesActivity::class.java)) }
        binding.btnTermos.setOnClickListener { startActivity(Intent(this, TermosActivity::class.java)) }
        binding.btnAjuda.setOnClickListener { Suporte.abrirEmail(this, getString(R.string.config_ajuda_assunto)) }
        binding.tvVersao.text = getString(R.string.config_versao, BuildConfig.VERSION_NAME)
        binding.btnExcluirCadastro.setOnClickListener {
            pedirSenha(
                titulo = getString(R.string.excluir_meu_cadastro),
                mensagem = getString(R.string.excluir_profissional_msg),
                dica = R.string.excluir_sua_senha,
                botao = R.string.excluir
            ) { senha -> excluir.excluir(TipoCadastro.PROFISSIONAL, senha) }
        }
        coletar(excluir.excluindo) { ocupado ->
            binding.progressBar.visibility = if (ocupado) View.VISIBLE else View.GONE
            binding.btnExcluirCadastro.isEnabled = !ocupado
        }
        coletar(excluir.eventos) { evento ->
            when (evento) {
                ExcluirCadastroViewModel.Evento.Excluido -> {
                    mostrarMensagem(texto(R.string.excluir_feito))
                    Navegacao.irLimpando(this, Navegacao.telaLogin())
                }
                is ExcluirCadastroViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }
}
