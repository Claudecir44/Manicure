package com.cjstudio.manicure.ui.agenda

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
import com.cjstudio.manicure.util.AgendaUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AgendaProfissionalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgendaProfissionalBinding
    private val viewModel: AgendaProfissionalViewModel by viewModels()
    private val agendasAdapter = LinhaAdapter { linha -> agenda(linha.id)?.let { opcoesAgenda(it) } }
    private val marcadosAdapter = LinhaAdapter { linha -> agendamento(linha.id)?.let { opcoesAgendamento(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
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
                linha3 = "${diasDaSemana(a.diasSemana)}  ·  ${getString(R.string.agenda_horarios_dia, a.horarios.size)}",
                selo = getString(R.string.agenda_ocupados, a.ocupados.size, AgendaUtil.totalHorarios(a))
            )
        })
        binding.tvSemAgendas.visibility = if (!e.carregando && e.agendas.isEmpty()) View.VISIBLE else View.GONE

        val proximos = e.agendamentos.filter { !passou(it.horario) }
        marcadosAdapter.submitList(proximos.map { m ->
            Linha(
                id = m.id.orEmpty(),
                titulo = AgendaUtil.descrever(m.horario),
                linha2 = "${m.clienteNome.orEmpty()}  ·  ${m.clienteTelefone.orEmpty()}",
                linha3 = "${Categorias.nome(m.categoria)}  ·  ${m.atendente.orEmpty()}"
            )
        })
        binding.tvSemAgendamentos.visibility = if (!e.carregando && proximos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun passou(chave: String?): Boolean {
        val c = chave ?: return true
        val agora = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())
        return c < agora
    }

    private fun diasDaSemana(dias: List<Int>): String {
        val nomes = resources.getStringArray(R.array.dias_semana_curto)
        return dias.sorted().joinToString(", ") { nomes.getOrElse(it - 1) { "" } }
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
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.agenda_excluir)
                        .setMessage(R.string.agenda_excluir_msg)
                        .setPositiveButton(R.string.remover) { _, _ -> viewModel.excluir(a) }
                        .setNegativeButton(R.string.cancelar, null)
                        .show()
                }
            }
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
