package com.cjstudio.manicure.ui.agenda

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.manicure.R
import com.cjstudio.manicure.databinding.ActivityEditarAgendaBinding
import com.cjstudio.manicure.ui.common.adicionarChip
import com.cjstudio.manicure.ui.common.adicionarChipRemovivel
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.util.Aparencia
import com.cjstudio.manicure.util.AgendaUtil
import com.cjstudio.manicure.util.ajustarPaddingParaTeclado
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EditarAgendaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditarAgendaBinding
    private val viewModel: EditarAgendaViewModel by viewModels()
    private var categorias: List<Pair<String, String>> = emptyList()
    private var meses: List<String> = emptyList()
    private var preenchido = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Aparencia.aplicar(this)
        super.onCreate(savedInstanceState)
        binding = ActivityEditarAgendaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.scrollRoot.ajustarPaddingParaTeclado()

        binding.topo.tvTitulo.setText(if (viewModel.editando) R.string.agenda_editar else R.string.agenda_nova)
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.btnGerar.setOnClickListener {
            viewModel.gerarHorarios(binding.etInicio.text.toString(), binding.etFim.text.toString(), binding.etIntervalo.text.toString())
        }
        binding.btnAjustarDia.setOnClickListener { escolherDiaParaAjustar() }
        binding.btnExcluir.visibility = if (viewModel.editando) View.VISIBLE else View.GONE
        binding.btnExcluir.setOnClickListener { confirmarExclusao() }
        binding.btnSalvar.setOnClickListener {
            viewModel.salvar(
                categoria = categorias.getOrNull(binding.spCategoria.selectedItemPosition)?.first,
                atendente = binding.etAtendente.text.toString()
            )
        }

        coletar(viewModel.inicio) { inicio -> if (inicio != null) preencher(inicio) }
        coletar(viewModel.montagem) { mostrar(it) }
        coletar(viewModel.salvando) {
            binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE
            binding.btnSalvar.isEnabled = !it
            binding.btnExcluir.isEnabled = !it
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                EditarAgendaViewModel.Evento.Salvo, EditarAgendaViewModel.Evento.Excluida -> finish()
                is EditarAgendaViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    private fun confirmarExclusao() {
        val marcados = viewModel.inicio.value?.agenda?.ocupados?.size ?: 0
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.agenda_excluir)
            .setMessage(
                if (marcados == 0) getString(R.string.agenda_excluir_msg)
                else resources.getQuantityString(R.plurals.agenda_excluir_com_marcados, marcados, marcados)
            )
            .setPositiveButton(R.string.excluir) { _, _ -> viewModel.excluir() }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun preencher(inicio: EditarAgendaViewModel.Inicio) {
        if (preenchido) return
        preenchido = true
        categorias = inicio.categorias
        meses = inicio.meses
        binding.spCategoria.adapter = adaptador(categorias.map { it.second })
        binding.spMes.adapter = adaptador(meses.map { AgendaUtil.nomeMes(it) })
        val a = inicio.agenda
        binding.etAtendente.setText(a?.atendente ?: inicio.nomeProfissional)
        if (a != null) {
            binding.spCategoria.setSelection(categorias.indexOfFirst { it.first == a.categoria }.coerceAtLeast(0))
            binding.spMes.setSelection(meses.indexOf(a.mes).coerceAtLeast(0))
            // Horários da agenda nos campos de início/fim.
            a.horarios.sortedBy { AgendaUtil.minutos(it) }.let { h ->
                if (h.isNotEmpty()) binding.etInicio.setText(h.first())
            }
        }
        binding.spMes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, posicao: Int, id: Long) {
                meses.getOrNull(posicao)?.let { viewModel.escolherMes(it) }
            }
            override fun onNothingSelected(p: AdapterView<*>?) = Unit
        }
    }

    private fun adaptador(itens: List<String>) =
        ArrayAdapter(this, R.layout.item_spinner, itens).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }

    // Todos os dias do mês: marcado = atende. ✎ = dia com horário ajustado.
    private fun mostrar(m: EditarAgendaViewModel.Montagem) {
        binding.cgDias.removeAllViews()
        AgendaUtil.diasDoMes(m.mes).forEach { d ->
            val texto = if (d.data in m.diasAjustados && d.data in m.dias) "${d.titulo} ✎" else d.titulo
            val chip = binding.cgDias.adicionarChip(texto, d.data, d.data in m.dias && !d.passado)
            chip.isEnabled = !d.passado
            chip.setOnClickListener { viewModel.alternarDia(d.data) }
            chip.setOnLongClickListener {
                if (d.data in m.dias) ajustarDia(d.data)
                true
            }
        }
        binding.tvContadorDias.text = getString(R.string.agenda_dias_marcados, m.dias.size)

        binding.cgHorarios.removeAllViews()
        m.horarios.sortedBy { AgendaUtil.minutos(it) }.forEach { hora ->
            binding.cgHorarios.adicionarChipRemovivel(hora) { viewModel.removerHorario(hora) }
        }
        binding.tvSemHorarios.visibility = if (m.horarios.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun escolherDiaParaAjustar() {
        val dias = viewModel.montagem.value.dias.sorted()
        if (dias.isEmpty()) {
            mostrarMensagem(com.cjstudio.manicure.ui.common.texto(R.string.agenda_erro_dias))
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.agenda_ajustar_dia)
            .setItems(dias.map { AgendaUtil.descreverDia(it) }.toTypedArray()) { _, i -> ajustarDia(dias[i]) }
            .show()
    }

    // Horários de um dia: desmarcar = indisponível só nesse dia.
    private fun ajustarDia(data: String) {
        val horarios = viewModel.horariosDoDia(data)
        if (horarios.isEmpty()) {
            mostrarMensagem(com.cjstudio.manicure.ui.common.texto(R.string.agenda_erro_horarios))
            return
        }
        val marcados = horarios.map { it.second }.toBooleanArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.agenda_horarios_do_dia, AgendaUtil.descreverDia(data)))
            .setMultiChoiceItems(horarios.map { it.first }.toTypedArray(), marcados) { _, i, marcado -> marcados[i] = marcado }
            .setPositiveButton(R.string.salvar) { _, _ ->
                viewModel.definirDisponiveisDoDia(data, horarios.filterIndexed { i, _ -> marcados[i] }.map { it.first }.toSet())
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    companion object {
        const val EXTRA_ID = "agendaId"
    }
}
