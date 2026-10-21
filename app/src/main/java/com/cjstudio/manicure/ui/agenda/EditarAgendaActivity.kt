package com.cjstudio.manicure.ui.agenda

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.manicure.R
import com.cjstudio.manicure.databinding.ActivityEditarAgendaBinding
import com.cjstudio.manicure.ui.common.adicionarChip
import com.cjstudio.manicure.ui.common.adicionarChipRemovivel
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.util.AgendaUtil
import com.cjstudio.manicure.util.ajustarPaddingParaTeclado
import com.google.android.material.chip.Chip
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
        super.onCreate(savedInstanceState)
        binding = ActivityEditarAgendaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.scrollRoot.ajustarPaddingParaTeclado()

        binding.topo.tvTitulo.setText(if (viewModel.editando) R.string.agenda_editar else R.string.agenda_nova)
        binding.topo.btnVoltar.setOnClickListener { finish() }

        // Dom..Sáb, com seg a sex marcados numa agenda nova.
        resources.getStringArray(R.array.dias_semana_curto).forEachIndexed { i, nome ->
            binding.cgDias.adicionarChip(nome, (i + 1).toString(), marcado = i in 1..5)
        }

        binding.btnGerar.setOnClickListener {
            viewModel.gerarHorarios(binding.etInicio.text.toString(), binding.etFim.text.toString(), binding.etIntervalo.text.toString())
        }
        binding.tvAdicionarHorario.setOnClickListener { pedirHorario() }
        binding.btnSalvar.setOnClickListener { salvar() }

        coletar(viewModel.inicio) { inicio -> if (inicio != null) preencher(inicio) }
        coletar(viewModel.horarios) { mostrarHorarios(it) }
        coletar(viewModel.salvando) {
            binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE
            binding.btnSalvar.isEnabled = !it
        }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                EditarAgendaViewModel.Evento.Salvo -> finish()
                is EditarAgendaViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    private fun preencher(inicio: EditarAgendaViewModel.Inicio) {
        if (preenchido) return
        preenchido = true
        categorias = inicio.categorias
        meses = inicio.meses
        binding.spCategoria.adapter = adaptador(categorias.map { it.second })
        binding.spMes.adapter = adaptador(meses.map { AgendaUtil.nomeMes(it) })
        val a = inicio.agenda
        if (a == null) {
            binding.etAtendente.setText(inicio.nomeProfissional)
            return
        }
        binding.spCategoria.setSelection(categorias.indexOfFirst { it.first == a.categoria }.coerceAtLeast(0))
        binding.spMes.setSelection(meses.indexOf(a.mes).coerceAtLeast(0))
        binding.etAtendente.setText(a.atendente)
        for (i in 0 until binding.cgDias.childCount) {
            val chip = binding.cgDias.getChildAt(i) as Chip
            chip.isChecked = (chip.tag as String).toInt() in a.diasSemana
        }
    }

    private fun adaptador(itens: List<String>) =
        ArrayAdapter(this, R.layout.item_spinner, itens).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }

    private fun mostrarHorarios(lista: List<String>) {
        binding.cgHorarios.removeAllViews()
        lista.forEach { hora -> binding.cgHorarios.adicionarChipRemovivel(hora) { viewModel.removerHorario(hora) } }
        binding.tvSemHorarios.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun pedirHorario() {
        val campo = EditText(this).apply {
            setHint(R.string.agenda_hora_hint)
            inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
        }
        val margem = (20 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(margem, margem / 2, margem, 0)
            addView(campo)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.agenda_adicionar_horario)
            .setView(container)
            .setPositiveButton(R.string.salvar) { _, _ -> viewModel.adicionarHorario(campo.text.toString()) }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun salvar() {
        val dias = (0 until binding.cgDias.childCount)
            .map { binding.cgDias.getChildAt(it) as Chip }
            .filter { it.isChecked }
            .map { (it.tag as String).toInt() }
        viewModel.salvar(
            categoria = categorias.getOrNull(binding.spCategoria.selectedItemPosition)?.first,
            mes = meses.getOrNull(binding.spMes.selectedItemPosition),
            atendente = binding.etAtendente.text.toString(),
            dias = dias
        )
    }

    companion object {
        const val EXTRA_ID = "agendaId"
    }
}
