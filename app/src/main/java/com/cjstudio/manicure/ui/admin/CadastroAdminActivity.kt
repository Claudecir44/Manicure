package com.cjstudio.manicure.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Admin
import com.cjstudio.manicure.databinding.ActivityCadastroAdminBinding
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.ui.common.texto
import com.cjstudio.manicure.util.ajustarPaddingParaTeclado
import com.cjstudio.manicure.util.habilitarToggleSenha
import com.cjstudio.manicure.util.mascaraCpf
import com.cjstudio.manicure.util.mascaraTelefone
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CadastroAdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCadastroAdminBinding
    private val viewModel: CadastroAdminViewModel by viewModels()
    private val checkboxesPermissao = mutableMapOf<String, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.scrollRoot.ajustarPaddingParaTeclado()
        binding.etCpf.mascaraCpf()
        binding.etTelefone.mascaraTelefone()
        binding.etSenha.habilitarToggleSenha()
        binding.etConfirmarSenha.habilitarToggleSenha()
        binding.etSenhaMaster.habilitarToggleSenha()

        // Uma caixa por permissão — só aparecem para "Colaborador".
        Admin.CHAVES_PERMISSOES.forEach { (chave, rotulo) ->
            val cb = CheckBox(this).apply {
                text = rotulo
                setTextColor(ContextCompat.getColor(context, R.color.texto))
                buttonTintList = ContextCompat.getColorStateList(context, R.color.rosa)
            }
            checkboxesPermissao[chave] = cb
            binding.containerPermissoes.addView(cb)
        }
        binding.rgPapel.setOnCheckedChangeListener { _, id ->
            binding.containerPermissoes.visibility = if (id == R.id.rbColaborador) View.VISIBLE else View.GONE
        }

        binding.topo.tvTitulo.setText(if (viewModel.editando) R.string.admin_editar_titulo else R.string.admin_novo_titulo)
        if (viewModel.editando) {
            // E-mail e senha de login não mudam pela edição.
            binding.etEmail.isEnabled = false
            binding.etSenha.visibility = View.GONE
            binding.etConfirmarSenha.visibility = View.GONE
            binding.btnCadastrar.setText(R.string.admin_salvar)
            binding.tvSenhaMasterDica.setText(R.string.admin_senha_master_dica_editar)
        }
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.btnCadastrar.setOnClickListener { enviar() }

        coletar(viewModel.enviando) { enviando ->
            binding.progressBar.visibility = if (enviando) View.VISIBLE else View.GONE
            binding.btnCadastrar.isEnabled = !enviando
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                CadastroAdminViewModel.Evento.Cadastrado -> {
                    mostrarMensagem(texto(R.string.admin_cadastrado))
                    finish()
                }
                CadastroAdminViewModel.Evento.Salvo -> {
                    mostrarMensagem(texto(R.string.admin_salvo))
                    finish()
                }
                is CadastroAdminViewModel.Evento.Preencher -> preencher(evento.admin)
                is CadastroAdminViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    private fun preencher(admin: Admin) {
        // Girando a tela os campos voltam sozinhos; não sobrescrever.
        if (binding.etNome.text.isNotEmpty()) return
        binding.etNome.setText(admin.nome)
        binding.etSobrenome.setText(admin.sobrenome)
        binding.etCpf.setText(admin.cpf)
        binding.etTelefone.setText(admin.telefone)
        binding.etEmail.setText(admin.email)
        binding.rgPapel.check(if (admin.ehColaborador) R.id.rbColaborador else R.id.rbAdmin)
        checkboxesPermissao.forEach { (chave, cb) -> cb.isChecked = admin.permissoes?.get(chave) ?: true }
        // Master sempre tem acesso total: o tipo de acesso dele não muda.
        if (admin.ehMaster) {
            binding.tvPapel.visibility = View.GONE
            binding.rgPapel.visibility = View.GONE
            binding.containerPermissoes.visibility = View.GONE
        }
    }

    private fun enviar() {
        viewModel.cadastrar(
            CadastroAdminViewModel.Formulario(
                nome = binding.etNome.text.toString(),
                sobrenome = binding.etSobrenome.text.toString(),
                email = binding.etEmail.text.toString(),
                telefone = binding.etTelefone.text.toString(),
                cpf = binding.etCpf.text.toString(),
                senha = binding.etSenha.text.toString(),
                confirmarSenha = binding.etConfirmarSenha.text.toString(),
                colaborador = binding.rgPapel.checkedRadioButtonId == R.id.rbColaborador,
                permissoes = checkboxesPermissao.mapValues { it.value.isChecked },
                senhaAutorizacao = binding.etSenhaMaster.text.toString()
            )
        )
    }

    companion object {
        // Presente = editar este admin (sem = novo).
        const val EXTRA_ID = "adminId"
    }
}
