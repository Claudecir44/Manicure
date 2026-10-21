package com.cjstudio.manicure.ui.chat

import android.app.NotificationManager
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Mensagem
import com.cjstudio.manicure.databinding.ActivityChatBinding
import com.cjstudio.manicure.databinding.ItemMensagemBinding
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.util.ajustarPaddingParaTeclado
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Locale

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private val adapter by lazy { MensagensAdapter(viewModel.meuId) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // A profissional vê o chat no verde neon dela.
        if (intent.getBooleanExtra(EXTRA_COMO_PROFISSIONAL, false)) setTheme(R.style.Theme_Manicure_Profissional)
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.ajustarPaddingParaTeclado()

        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.topo.tvTitulo.setText(R.string.chat_titulo)
        binding.rvMensagens.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvMensagens.adapter = adapter
        binding.btnEnviar.setOnClickListener {
            viewModel.enviar(binding.etMensagem.text.toString())
            binding.etMensagem.text?.clear()
        }

        coletar(viewModel.conversa) { c ->
            if (c != null) binding.topo.tvTitulo.text = c.nomeOutro(viewModel.meuId)?.ifBlank { null } ?: getString(R.string.chat_titulo)
        }
        coletar(viewModel.mensagens) { lista ->
            adapter.submitList(lista) { if (lista.isNotEmpty()) binding.rvMensagens.scrollToPosition(lista.size - 1) }
            binding.tvVazio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        }
        coletar(viewModel.idAtual) { id ->
            conversaAberta = id
            id?.let { getSystemService(NotificationManager::class.java)?.cancel(it.hashCode()) }
        }
        coletar(viewModel.erros) { mostrarMensagem(it) }
    }

    override fun onResume() {
        super.onResume()
        conversaAberta = viewModel.idAtual.value
        viewModel.marcarComoLida()
    }

    override fun onPause() {
        super.onPause()
        conversaAberta = null
    }

    // Balão à direita (minha) ou à esquerda (da outra pessoa), com hora e "Lida".
    private class MensagensAdapter(private val meuId: String?) :
        ListAdapter<Mensagem, MensagensAdapter.Holder>(Diferenca) {

        class Holder(val binding: ItemMensagemBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemMensagemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val m = getItem(position)
            val b = holder.binding
            val minha = m.remetenteId == meuId
            b.tvTexto.text = m.conteudo
            val hora = m.timestamp?.let { HORA.format(it) }.orEmpty()
            b.tvHora.text = if (minha) {
                b.root.context.getString(if (m.lida) R.string.chat_lida else R.string.chat_enviada, hora)
            } else {
                hora
            }
            b.balao.setBackgroundResource(if (minha) R.drawable.bg_balao_meu else R.drawable.bg_balao_outro)
            (b.balao.layoutParams as LinearLayout.LayoutParams).gravity = if (minha) Gravity.END else Gravity.START
            b.tvHora.gravity = if (minha) Gravity.END else Gravity.START
        }

        private object Diferenca : DiffUtil.ItemCallback<Mensagem>() {
            override fun areItemsTheSame(a: Mensagem, b: Mensagem) = a.id == b.id
            override fun areContentsTheSame(a: Mensagem, b: Mensagem) = a == b
        }

        companion object {
            val HORA = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))
        }
    }

    companion object {
        const val EXTRA_CONVERSA = "conversaId"
        const val EXTRA_PROFISSIONAL_ID = "chatProfissionalId"
        const val EXTRA_PROFISSIONAL_NOME = "chatProfissionalNome"
        const val EXTRA_PROFISSIONAL_FOTO = "chatProfissionalFoto"
        const val EXTRA_CLIENTE_ID = "chatClienteId"
        const val EXTRA_CLIENTE_NOME = "chatClienteNome"
        const val EXTRA_COMO_PROFISSIONAL = "chatComoProfissional"

        // Conversa na tela agora: o serviço de push não notifica essa.
        @Volatile
        var conversaAberta: String? = null
    }
}
