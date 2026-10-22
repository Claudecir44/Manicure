package com.cjstudio.manicure.ui.agenda

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Agenda
import com.cjstudio.manicure.data.model.Agendamento
import com.cjstudio.manicure.data.model.Categorias
import com.cjstudio.manicure.databinding.ActivityAgendaProfissionalBinding
import com.cjstudio.manicure.ui.common.Linha
import com.cjstudio.manicure.ui.common.LinhaAdapter
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.util.Aparencia
import com.cjstudio.manicure.util.AgendaUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AgendaProfissionalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgendaProfissionalBinding
    private val viewModel: AgendaProfissionalViewModel by viewModels()
    private val agendasAdapter = LinhaAdapter { linha -> agenda(linha.id)?.let { opcoesAgenda(it) } }
    private val marcadosAdapter = LinhaAdapter { linha -> agendamento(linha.id)?.let { opcoesAgendamento(it) } }
    // Tocar num dia mostra embaixo quem marcou e rola a tela até lá.
    private val calendarioAdapter = CalendarioAdapter { data ->
        viewModel.selecionarDia(data)
        binding.scroll.post { binding.scroll.smoothScrollTo(0, binding.tvDiaSelecionado.top) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Aparencia.aplicar(this)
        super.onCreate(savedInstanceState)
        binding = ActivityAgendaProfissionalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topo.tvTitulo.setText(R.string.agenda_titulo)
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.rvAgendas.layoutManager = LinearLayoutManager(this)
        binding.rvAgendas.adapter = agendasAdapter
        binding.rvAgendas.isNestedScrollingEnabled = false
        binding.rvAgendamentos.layoutManager = LinearLayoutManager(this)
        binding.rvAgendamentos.adapter = marcadosAdapter
        binding.rvAgendamentos.isNestedScrollingEnabled = false
        binding.btnNovaAgenda.setOnClickListener { startActivity(Intent(this, EditarAgendaActivity::class.java)) }

        binding.rvCalendario.layoutManager = GridLayoutManager(this, 7)
        binding.rvCalendario.adapter = calendarioAdapter
        binding.rvCalendario.isNestedScrollingEnabled = false
        binding.btnMesAnterior.setOnClickListener { viewModel.mudarMes(-1) }
        binding.btnMesSeguinte.setOnClickListener { viewModel.mudarMes(1) }
        montarSemanaELegenda()

        coletar(viewModel.estado) { mostrar(it) }
        coletar(viewModel.mensagens) { mostrarMensagem(it) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.atualizar()
    }

    private fun mostrar(e: AgendaProfissionalViewModel.Estado) {
        binding.progressBar.visibility = if (e.carregando) View.VISIBLE else View.GONE
        agendasAdapter.submitList(e.agendas.map { a ->
            Linha(
                id = a.id.orEmpty(),
                titulo = "${Categorias.nome(a.categoria)} · ${AgendaUtil.nomeMes(a.mes)}",
                linha2 = getString(R.string.agenda_linha_atendente, a.atendente.orEmpty()),
                linha3 = "${getString(R.string.agenda_n_dias, AgendaUtil.diasAtivos(a).size)}  ·  ${getString(R.string.agenda_horarios_dia, a.horarios.size)}",
                selo = getString(R.string.agenda_ocupados, a.ocupados.size, AgendaUtil.totalHorarios(a))
            )
        })
        binding.tvSemAgendas.visibility = if (!e.carregando && e.agendas.isEmpty()) View.VISIBLE else View.GONE

        // Calendário do mês e, embaixo, as clientes do dia tocado.
        binding.tvMes.text = AgendaUtil.nomeMes(e.mes)
        calendarioAdapter.atualizar(e.calendario, e.diaSelecionado)
        binding.tvSemAgendamentos.visibility = if (!e.carregando && e.agendamentos.isEmpty()) View.VISIBLE else View.GONE
        binding.tvDicaCalendario.visibility = if (e.agendamentos.isEmpty()) View.GONE else View.VISIBLE

        val dia = e.diaSelecionado
        binding.tvDiaSelecionado.visibility = if (dia != null) View.VISIBLE else View.GONE
        binding.tvDiaSelecionado.text = dia?.let { AgendaUtil.descreverDia(it) }
        val doDia = e.marcadosDoDia
        binding.tvDiaSemMarcados.visibility = if (dia != null && doDia.isEmpty()) View.VISIBLE else View.GONE
        marcadosAdapter.submitList(doDia.map { m ->
            Linha(
                id = m.id.orEmpty(),
                titulo = "${m.horario?.takeLast(4)?.let { "${it.take(2)}:${it.takeLast(2)}" }.orEmpty()}  ·  ${m.clienteNome.orEmpty()}",
                linha2 = m.clienteTelefone.orEmpty(),
                linha3 = "${Categorias.nome(m.categoria)}  ·  ${m.atendente.orEmpty()}"
            )
        })
    }

    // Letras dos dias da semana em cima da grade e a legenda das cores embaixo.
    private fun montarSemanaELegenda() {
        resources.getStringArray(R.array.agenda_semana).forEach { letra ->
            binding.linhaSemana.addView(TextView(this).apply {
                text = letra
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.texto_secundario))
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        val densidade = resources.displayMetrics.density
        listOf(
            R.color.verde_neon to R.string.agenda_legenda_aberto,
            R.color.vermelho to R.string.agenda_legenda_marcado,
            R.color.preto to R.string.agenda_legenda_fechado,
            R.color.cinza_dia to R.string.agenda_legenda_passado
        ).forEach { (cor, nome) ->
            binding.legenda.addView(TextView(this).apply {
                setText(nome)
                setTextColor(ContextCompat.getColor(context, R.color.texto_secundario))
                textSize = 11f
                compoundDrawablePadding = (5 * densidade).toInt()
                val ponto = ContextCompat.getDrawable(context, R.drawable.bg_ponto)!!.mutate()
                ponto.setTint(ContextCompat.getColor(context, cor))
                setCompoundDrawablesRelativeWithIntrinsicBounds(ponto, null, null, null)
                setPadding((6 * densidade).toInt(), 0, (6 * densidade).toInt(), 0)
            })
        }
    }

    private fun agenda(id: String) = viewModel.estado.value.agendas.firstOrNull { it.id == id }
    private fun agendamento(id: String) = viewModel.estado.value.agendamentos.firstOrNull { it.id == id }

    private fun opcoesAgenda(a: Agenda) {
        MaterialAlertDialogBuilder(this)
            .setTitle("${Categorias.nome(a.categoria)} · ${AgendaUtil.nomeMes(a.mes)}")
            .setItems(arrayOf(getString(R.string.agenda_editar), getString(R.string.agenda_excluir))) { _, i ->
                if (i == 0) {
                    startActivity(Intent(this, EditarAgendaActivity::class.java).putExtra(EditarAgendaActivity.EXTRA_ID, a.id))
                } else {
                    confirmarExclusao(a)
                }
            }
            .show()
    }

    private fun confirmarExclusao(a: Agenda) {
        val marcados = a.ocupados.size
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.agenda_excluir)
            .setMessage(
                if (marcados == 0) getString(R.string.agenda_excluir_msg)
                else resources.getQuantityString(R.plurals.agenda_excluir_com_marcados, marcados, marcados)
            )
            .setPositiveButton(R.string.excluir) { _, _ -> viewModel.excluir(a) }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun abrirChat(m: Agendamento) {
        val clienteId = m.clienteId ?: return
        startActivity(
            Intent(this, com.cjstudio.manicure.ui.chat.ChatActivity::class.java)
                .putExtra(com.cjstudio.manicure.ui.chat.ChatActivity.EXTRA_PROFISSIONAL_ID, m.profissionalId)
                .putExtra(com.cjstudio.manicure.ui.chat.ChatActivity.EXTRA_PROFISSIONAL_NOME, m.profissionalNome.orEmpty())
                .putExtra(com.cjstudio.manicure.ui.chat.ChatActivity.EXTRA_CLIENTE_ID, clienteId)
                .putExtra(com.cjstudio.manicure.ui.chat.ChatActivity.EXTRA_CLIENTE_NOME, m.clienteNome.orEmpty())
                .putExtra(com.cjstudio.manicure.ui.chat.ChatActivity.EXTRA_COMO_PROFISSIONAL, true)
        )
    }

    // Horário marcado: conversar com a cliente no chat ou cancelar.
    private fun opcoesAgendamento(m: Agendamento) {
        MaterialAlertDialogBuilder(this)
            .setTitle(AgendaUtil.descrever(m.horario))
            .setMessage("${m.clienteNome.orEmpty()}\n${m.clienteTelefone.orEmpty()}\n${Categorias.nome(m.categoria)} · ${m.atendente.orEmpty()}")
            .setPositiveButton(R.string.perfil_chat) { _, _ -> abrirChat(m) }
            .setNegativeButton(R.string.agenda_cancelar_horario) { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.agenda_cancelar_horario)
                    .setMessage(R.string.agenda_cancelar_msg)
                    .setPositiveButton(R.string.agenda_cancelar_horario) { _, _ -> viewModel.cancelar(m) }
                    .setNegativeButton(R.string.voltar, null)
                    .show()
            }
            .setNeutralButton(R.string.ok, null)
            .show()
    }
}
