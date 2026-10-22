package com.cjstudio.manicure.ui.agenda

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Agendamento
import com.cjstudio.manicure.data.model.Categorias
import com.cjstudio.manicure.data.repository.IAgendaRepository
import com.cjstudio.manicure.databinding.ActivityMeusHorariosBinding
import com.cjstudio.manicure.ui.common.Linha
import com.cjstudio.manicure.ui.common.LinhaAdapter
import com.cjstudio.manicure.ui.common.UiTexto
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.ui.common.paraUiTexto
import com.cjstudio.manicure.util.AgendaUtil
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// Horários que a cliente marcou (os que ainda não passaram): remarcar ou
// desmarcar.
@HiltViewModel
class MeusHorariosViewModel @Inject constructor(
    private val agendas: IAgendaRepository
) : ViewModel() {

    private val _lista = MutableStateFlow<List<Agendamento>?>(null)
    val lista: StateFlow<List<Agendamento>?> = _lista

    private val _mensagens = Channel<UiTexto>(Channel.BUFFERED)
    val mensagens: Flow<UiTexto> = _mensagens.receiveAsFlow()

    fun atualizar() {
        viewModelScope.launch {
            val agora = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            agendas.horariosDaCliente()
                .onSuccess { todos -> _lista.value = todos.filter { (it.horario ?: "") >= agora } }
                .onFailure {
                    _lista.value = emptyList()
                    _mensagens.send(it.paraUiTexto())
                }
        }
    }

    fun cancelar(agendamento: Agendamento) {
        viewModelScope.launch {
            agendas.cancelarMeuHorario(agendamento)
                .onSuccess { _mensagens.send(com.cjstudio.manicure.ui.common.texto(R.string.agendar_cancelado)) }
                .onFailure { _mensagens.send(it.paraUiTexto()) }
            atualizar()
        }
    }
}

@AndroidEntryPoint
class MeusHorariosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMeusHorariosBinding
    private val viewModel: MeusHorariosViewModel by viewModels()
    private val adapter = LinhaAdapter { linha ->
        viewModel.lista.value?.firstOrNull { it.id == linha.id }?.let { opcoes(it) }
    }

    private fun opcoes(m: Agendamento) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(AgendaUtil.descrever(m.horario))
            .setMessage("${Categorias.nome(m.categoria)}  ·  ${m.atendente.orEmpty()}\n${m.profissionalNome.orEmpty()}")
            .setPositiveButton(R.string.agendar_remarcar) { _, _ -> remarcar(m) }
            .setNegativeButton(R.string.agendar_cancelar_sim) { _, _ ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.agendar_cancelar_titulo)
                    .setMessage(getString(R.string.agendar_cancelar_msg, AgendaUtil.descrever(m.horario)))
                    .setPositiveButton(R.string.agendar_cancelar_sim) { _, _ -> viewModel.cancelar(m) }
                    .setNegativeButton(R.string.voltar, null)
                    .show()
            }
            .setNeutralButton(R.string.voltar, null)
            .show()
    }

    private fun remarcar(m: Agendamento) {
        startActivity(
            android.content.Intent(this, AgendarActivity::class.java)
                .putExtra(AgendarActivity.EXTRA_PROFISSIONAL, m.profissionalId)
                .putExtra(AgendarActivity.EXTRA_NOME, m.profissionalNome.orEmpty())
                .putExtra(AgendarActivity.EXTRA_CATEGORIA, m.categoria)
                .putExtra(AgendarActivity.EXTRA_REMARCAR_ID, m.id)
                .putExtra(AgendarActivity.EXTRA_REMARCAR_AGENDA, m.agendaId)
                .putExtra(AgendarActivity.EXTRA_REMARCAR_HORARIO, m.horario)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMeusHorariosBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.topo.tvTitulo.setText(R.string.meus_horarios)
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.rvHorarios.layoutManager = LinearLayoutManager(this)
        binding.rvHorarios.adapter = adapter

        coletar(viewModel.lista) { lista ->
            binding.progressBar.visibility = if (lista == null) View.VISIBLE else View.GONE
            binding.tvVazio.visibility = if (lista?.isEmpty() == true) View.VISIBLE else View.GONE
            adapter.submitList(lista.orEmpty().map { m ->
                Linha(
                    id = m.id.orEmpty(),
                    titulo = AgendaUtil.descrever(m.horario),
                    linha2 = "${Categorias.nome(m.categoria)}  ·  ${m.atendente.orEmpty()}",
                    linha3 = m.profissionalNome.orEmpty()
                )
            })
        }
        coletar(viewModel.mensagens) { mostrarMensagem(it) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.atualizar()
    }
}
