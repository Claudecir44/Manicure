package com.cjstudio.manicure.ui.profissional

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.model.TipoAtendimento
import com.cjstudio.manicure.data.model.TipoPerfil
import com.cjstudio.manicure.databinding.ActivityEditarPerfilBinding
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.util.ajustarPaddingParaTeclado
import com.cjstudio.manicure.util.mascaraTelefone
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EditarPerfilActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditarPerfilBinding
    private val viewModel: EditarPerfilViewModel by viewModels()
    private var preenchido = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditarPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.scrollRoot.ajustarPaddingParaTeclado()
        binding.etWhatsapp.mascaraTelefone()
        binding.etTelefone.mascaraTelefone()
        preenchido = savedInstanceState != null

        binding.topo.tvTitulo.setText(R.string.editar_titulo)
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.rgTipo.setOnCheckedChangeListener { _, _ -> atualizarDicaNome() }
        binding.btnSalvar.setOnClickListener { salvar() }

        coletar(viewModel.perfil) { p -> if (p != null && !preenchido) preencher(p) }
        coletar(viewModel.salvando) { salvando ->
            binding.progressBar.visibility = if (salvando) View.VISIBLE else View.GONE
            binding.btnSalvar.isEnabled = !salvando
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                EditarPerfilViewModel.Evento.Salvo -> finish()
                is EditarPerfilViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    // "Seu nome" ou "Nome do salão", conforme o tipo de perfil.
    private fun atualizarDicaNome() {
        val salao = binding.rgTipo.checkedRadioButtonId == R.id.rbSalao
        binding.etNomeExibicao.setHint(if (salao) R.string.editar_nome_salao else R.string.editar_nome_pessoal)
        binding.etNomeResponsavel.visibility = if (salao) View.VISIBLE else View.GONE
    }

    private fun preencher(p: Profissional) {
        preenchido = true
        binding.rgTipo.check(if (p.tipoPerfil == TipoPerfil.SALAO) R.id.rbSalao else R.id.rbPessoal)
        binding.etNomeExibicao.setText(p.nomeExibicao)
        binding.etNomeResponsavel.setText(p.nomeResponsavel)
        binding.etApresentacao.setText(p.apresentacao)
        binding.etSobreTrabalho.setText(p.sobreTrabalho)
        binding.etWhatsapp.setText(p.whatsapp)
        binding.etTelefone.setText(p.telefone)
        binding.etInstagram.setText(p.instagram)
        binding.etCidade.setText(p.cidade)
        binding.etBairro.setText(p.bairro)
        binding.etEndereco.setText(p.endereco)
        binding.etHorario.setText(p.horarioAtendimento)
        binding.rgAtendimento.check(
            when (p.atendimento) {
                TipoAtendimento.DOMICILIO -> R.id.rbDomicilio
                TipoAtendimento.AMBOS -> R.id.rbAmbos
                else -> R.id.rbNoSalao
            }
        )
        atualizarDicaNome()
    }

    private fun salvar() {
        viewModel.salvar(
            EditarPerfilViewModel.Formulario(
                nomeExibicao = binding.etNomeExibicao.text.toString(),
                tipoPerfil = if (binding.rgTipo.checkedRadioButtonId == R.id.rbSalao) TipoPerfil.SALAO else TipoPerfil.PESSOAL,
                nomeResponsavel = binding.etNomeResponsavel.text.toString(),
                apresentacao = binding.etApresentacao.text.toString(),
                sobreTrabalho = binding.etSobreTrabalho.text.toString(),
                whatsapp = binding.etWhatsapp.text.toString(),
                telefone = binding.etTelefone.text.toString(),
                instagram = binding.etInstagram.text.toString(),
                cidade = binding.etCidade.text.toString(),
                bairro = binding.etBairro.text.toString(),
                endereco = binding.etEndereco.text.toString(),
                horarioAtendimento = binding.etHorario.text.toString(),
                atendimento = when (binding.rgAtendimento.checkedRadioButtonId) {
                    R.id.rbDomicilio -> TipoAtendimento.DOMICILIO
                    R.id.rbAmbos -> TipoAtendimento.AMBOS
                    else -> TipoAtendimento.SALAO
                }
            )
        )
    }
}
