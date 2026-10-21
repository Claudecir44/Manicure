package com.cjstudio.manicure.ui.chat

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Conversa
import com.cjstudio.manicure.data.repository.IAuthRepository
import com.cjstudio.manicure.data.repository.IChatRepository
import com.cjstudio.manicure.databinding.ActivityConversasBinding
import com.cjstudio.manicure.databinding.ItemConversaBinding
import com.cjstudio.manicure.ui.common.coletar
import com.google.android.material.color.MaterialColors
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
    private val adapter by lazy {
        ConversasAdapter(viewModel.meuId) { c ->
            startActivity(
                Intent(this, ChatActivity::class.java)
                    .putExtra(ChatActivity.EXTRA_CONVERSA, c.id)
                    .putExtra(ChatActivity.EXTRA_COMO_PROFISSIONAL, comoProfissional)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (comoProfissional) com.cjstudio.manicure.util.Aparencia.aplicar(this)
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
            adapter.submitList(lista.orEmpty())
        }
    }

    companion object {
        const val EXTRA_COMO_PROFISSIONAL = "comoProfissional"
    }
}

// Não lida: nome em negrito, prévia clara, data na cor de destaque e o número
// de mensagens novas. Lida: tudo mais apagado.
private class ConversasAdapter(
    private val meuId: String?,
    private val aoTocar: (Conversa) -> Unit
) : ListAdapter<Conversa, ConversasAdapter.Holder>(Diferenca) {

    class Holder(val binding: ItemConversaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemConversaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val c = getItem(position)
        val b = holder.binding
        val ctx = b.root.context
        val naoLidas = c.naoLidasParaMim(meuId)
        val nova = naoLidas > 0
        val nome = c.nomeOutro(meuId).orEmpty()
        val destaque = MaterialColors.getColor(b.root, R.attr.corDestaque)

        b.tvInicial.text = nome.trim().firstOrNull()?.uppercase().orEmpty()
        b.tvNome.text = nome
        b.tvNome.setTypeface(null, if (nova) Typeface.BOLD else Typeface.NORMAL)
        b.tvData.text = c.ultimoTimestamp?.let { DATA.format(it) }.orEmpty()
        b.tvData.setTextColor(if (nova) destaque else ContextCompat.getColor(ctx, R.color.dica))
        val minha = c.ultimoRemetenteId == meuId
        b.tvPrevia.text = (if (minha) ctx.getString(R.string.chat_voce) else "") + c.ultimaMensagem.orEmpty()
        b.tvPrevia.setTextColor(ContextCompat.getColor(ctx, if (nova) R.color.texto else R.color.texto_secundario))
        b.tvPrevia.setTypeface(null, if (nova) Typeface.BOLD else Typeface.NORMAL)
        b.tvNaoLidas.visibility = if (nova) View.VISIBLE else View.GONE
        b.tvNaoLidas.text = if (naoLidas > 99) "99+" else naoLidas.toString()
        b.root.setOnClickListener { aoTocar(c) }
    }

    private object Diferenca : DiffUtil.ItemCallback<Conversa>() {
        override fun areItemsTheSame(a: Conversa, b: Conversa) = a.id == b.id
        override fun areContentsTheSame(a: Conversa, b: Conversa) = a == b
    }

    companion object {
        private val DATA = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))
    }
}
