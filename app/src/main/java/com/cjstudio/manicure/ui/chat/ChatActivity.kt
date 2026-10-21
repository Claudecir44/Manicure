package com.cjstudio.manicure.ui.chat

import android.app.NotificationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Locale

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private val adapter by lazy { MensagensAdapter(viewModel.meuId) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // A profissional vê o chat na cor que ela escolheu em Aparência.
        if (intent.getBooleanExtra(EXTRA_COMO_PROFISSIONAL, false)) com.cjstudio.manicure.util.Aparencia.aplicar(this)
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.ajustarPaddingParaTeclado()

        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.topo.tvTitulo.setText(R.string.chat_titulo)
        // Mensagens de cima pra baixo, a partir do canto superior esquerdo.
        binding.rvMensagens.layoutManager = LinearLayoutManager(this)
        binding.rvMensagens.adapter = adapter
        binding.btnEnviar.setOnClickListener {
            viewModel.enviar(binding.etMensagem.text.toString())
            binding.etMensagem.text?.clear()
        }

        coletar(viewModel.conversa) { c ->
            if (c != null) {
                binding.topo.tvTitulo.text = c.nomeOutro(viewModel.meuId)?.ifBlank { null } ?: getString(R.string.chat_titulo)
                adapter.nomeOutro = c.nomeOutro(viewModel.meuId).orEmpty()
            }
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

    // Balões todos à esquerda: quem escreveu e data/hora no alto; nas minhas,
    // "✓ Enviada" (cinza) ou "✓✓ Lida" (cor de destaque) embaixo.
    private class MensagensAdapter(private val meuId: String?) :
        ListAdapter<Mensagem, MensagensAdapter.Holder>(Diferenca) {

        class Holder(val binding: ItemMensagemBinding) : RecyclerView.ViewHolder(binding.root)

        var nomeOutro: String = ""
            set(valor) {
                if (field == valor) return
                field = valor
                @Suppress("NotifyDataSetChanged")
                notifyDataSetChanged()
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemMensagemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val m = getItem(position)
            val b = holder.binding
            val minha = m.remetenteId == meuId
            val ctx = b.root.context
            val destaque = MaterialColors.getColor(b.root, R.attr.corDestaque)
            val secundaria = ContextCompat.getColor(ctx, R.color.texto_secundario)
            b.tvTexto.text = m.conteudo
            b.tvHora.text = m.timestamp?.let { HORA.format(it) }.orEmpty()
            b.tvAutor.text = if (minha) ctx.getString(R.string.chat_autor_voce) else nomeOutro.ifBlank { m.remetenteNome.orEmpty() }
            b.tvAutor.setTextColor(if (minha) destaque else ContextCompat.getColor(ctx, R.color.texto))
            b.balao.setBackgroundResource(if (minha) R.drawable.bg_balao_meu else R.drawable.bg_balao_outro)
            b.tvStatus.visibility = if (minha) View.VISIBLE else View.GONE
            if (minha) {
                b.tvStatus.setText(if (m.lida) R.string.chat_lida else R.string.chat_enviada)
                b.tvStatus.setTextColor(if (m.lida) destaque else secundaria)
            }
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
