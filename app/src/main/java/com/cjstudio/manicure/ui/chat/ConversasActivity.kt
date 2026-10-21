package com.cjstudio.manicure.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Conversa
import com.cjstudio.manicure.data.repository.IAuthRepository
import com.cjstudio.manicure.data.repository.IChatRepository
import com.cjstudio.manicure.databinding.ActivityConversasBinding
import com.cjstudio.manicure.ui.common.Linha
import com.cjstudio.manicure.ui.common.LinhaAdapter
import com.cjstudio.manicure.ui.common.coletar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

// Lista de conversas de UM papel: a profissional vê as clientes que
// falaram com ela; a cliente vê as profissionais.
@HiltViewModel
class ConversasViewModel @Inject constructor(
    chat: IChatRepository,
    auth: IAuthRepository,
    estado: SavedStateHandle
) : ViewModel() {

    val meuId: String? = auth.uidLogado()
    private val comoProfissional: Boolean = estado[ConversasActivity.EXTRA_COMO_PROFISSIONAL] ?: false

    val conversas: StateFlow<List<Conversa>?> = chat.escutarMinhasConversas()
        .map { lista -> lista.filter { it.souProfissional(meuId) == comoProfissional } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@AndroidEntryPoint
class ConversasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversasBinding
    private val viewModel: ConversasViewModel by viewModels()
    private val comoProfissional by lazy { intent.getBooleanExtra(EXTRA_COMO_PROFISSIONAL, false) }
    private val adapter = LinhaAdapter { linha ->
        startActivity(
            Intent(this, ChatActivity::class.java)
                .putExtra(ChatActivity.EXTRA_CONVERSA, linha.id)
                .putExtra(ChatActivity.EXTRA_COMO_PROFISSIONAL, comoProfissional)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (comoProfissional) setTheme(R.style.Theme_Manicure_Profissional)
        super.onCreate(savedInstanceState)
        binding = ActivityConversasBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.topo.tvTitulo.setText(R.string.chat_conversas)
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.rvConversas.layoutManager = LinearLayoutManager(this)
        binding.rvConversas.adapter = adapter

        coletar(viewModel.conversas) { lista ->
            binding.progressBar.visibility = if (lista == null) View.VISIBLE else View.GONE
            binding.tvVazio.visibility = if (lista?.isEmpty() == true) View.VISIBLE else View.GONE
            adapter.submitList(lista.orEmpty().map { c ->
                val naoLidas = c.naoLidasParaMim(viewModel.meuId)
                val minha = c.ultimoRemetenteId == viewModel.meuId
                Linha(
                    id = c.id.orEmpty(),
                    titulo = c.nomeOutro(viewModel.meuId).orEmpty(),
                    linha2 = (if (minha) getString(R.string.chat_voce) else "") + c.ultimaMensagem.orEmpty(),
                    linha3 = c.ultimoTimestamp?.let { DATA.format(it) }.orEmpty(),
                    selo = if (naoLidas > 0) resources.getQuantityString(R.plurals.chat_nao_lidas, naoLidas, naoLidas) else ""
                )
            })
        }
    }

    companion object {
        const val EXTRA_COMO_PROFISSIONAL = "comoProfissional"
        private val DATA = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))
    }
}
