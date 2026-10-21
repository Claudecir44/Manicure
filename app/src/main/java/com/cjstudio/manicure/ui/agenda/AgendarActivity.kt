package com.cjstudio.manicure.ui.agenda

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Categorias
import com.cjstudio.manicure.databinding.ActivityAgendarBinding
import com.cjstudio.manicure.ui.common.adicionarChip
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.util.AgendaUtil
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AgendarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgendarBinding
    private val viewModel: AgendarViewModel by viewModels()

    // Evita que redesenhar os chips dispare a escolha de novo.
    private var desenhando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topo.tvTitulo.text = Categorias.nome(viewModel.categoria)
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.tvProfissional.text = viewModel.profissionalNome
        binding.btnConfirmar.setOnClickListener { confirmar() }

        aoEscolher(binding.cgAgendas) { viewModel.escolherAgenda(it) }
        aoEscolher(binding.cgDias) { viewModel.escolherDia(it) }
        aoEscolher(binding.cgHorarios) { viewModel.escolherHorario(it) }

        coletar(viewModel.estado) { mostrar(it) }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                AgendarViewModel.Evento.Marcado -> MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.agendar_marcado_titulo)
                    .setMessage(R.string.agendar_marcado_msg)
                    .setPositiveButton(R.string.ok) { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
                is AgendarViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    private fun aoEscolher(grupo: ChipGroup, acao: (String) -> Unit) {
        grupo.isSingleSelection = true
        grupo.isSelectionRequired = true
        grupo.setOnCheckedStateChangeListener { g, ids ->
            if (desenhando) return@setOnCheckedStateChangeListener
            val chip = ids.firstOrNull()?.let { g.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            acao(chip.tag as String)
        }
    }

    private fun mostrar(e: AgendarViewModel.Estado) {
        desenhando = true
        binding.progressBar.visibility = if (e.carregando || e.salvando) View.VISIBLE else View.GONE
        binding.btnConfirmar.isEnabled = !e.salvando && e.horario != null

        // Quem atende (uma agenda por atendente/mês).
        binding.cgAgendas.removeAllViews()
        e.agendas.forEach { a ->
            binding.cgAgendas.adicionarChip("${a.atendente.orEmpty()} · ${AgendaUtil.nomeMes(a.mes)}", a.id.orEmpty(), a.id == e.agendaId)
        }
        binding.cgDias.removeAllViews()
        e.dias.forEach { d -> binding.cgDias.adicionarChip(d.titulo, d.data, d.data == e.dia) }
        binding.cgHorarios.removeAllViews()
        e.horariosDoDia.forEach { h -> binding.cgHorarios.adicionarChip(h.hora, h.chave, h.chave == e.horario?.chave) }

        val vazio = !e.carregando && e.agendas.isEmpty()
        binding.tvVazio.visibility = if (vazio) View.VISIBLE else View.GONE
        binding.blocoEscolha.visibility = if (vazio) View.GONE else View.VISIBLE
        binding.tvResumo.text = e.horario?.let { getString(R.string.agendar_resumo, AgendaUtil.descrever(it.chave), e.agenda?.atendente.orEmpty()) }.orEmpty()
        desenhando = false
    }

    private fun confirmar() {
        val e = viewModel.estado.value
        val h = e.horario ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.agendar_confirmar_titulo)
            .setMessage(
                getString(
                    R.string.agendar_confirmar_msg,
                    Categorias.nome(viewModel.categoria),
                    AgendaUtil.descrever(h.chave),
                    e.agenda?.atendente.orEmpty(),
                    viewModel.profissionalNome
                )
            )
            .setPositiveButton(R.string.agendar_confirmar) { _, _ -> viewModel.confirmar() }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    companion object {
        const val EXTRA_PROFISSIONAL = "profissionalId"
        const val EXTRA_NOME = "profissionalNome"
        const val EXTRA_CATEGORIA = "categoria"
    }
}
