package com.cjstudio.manicure.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.cjstudio.manicure.data.model.FotoPortfolio
import com.cjstudio.manicure.data.model.Servico
import com.cjstudio.manicure.databinding.ItemFotoBinding
import com.cjstudio.manicure.databinding.ItemServicoBinding
import com.cjstudio.manicure.util.Formato

// Galeria em grade. Com "podeAdicionar", o primeiro quadro é o "+ Foto".
class FotosAdapter(
    private val aoTocar: (FotoPortfolio) -> Unit,
    private val aoSegurar: ((FotoPortfolio) -> Unit)? = null,
    private val aoAdicionar: (() -> Unit)? = null
) : RecyclerView.Adapter<FotosAdapter.Holder>() {

    class Holder(val binding: ItemFotoBinding) : RecyclerView.ViewHolder(binding.root)

    private var fotos: List<FotoPortfolio> = emptyList()
    private var podeAdicionar = false

    fun atualizar(lista: List<FotoPortfolio>, podeAdicionar: Boolean) {
        fotos = lista.sortedByDescending { it.enviadaEm }
        this.podeAdicionar = podeAdicionar && aoAdicionar != null
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun getItemCount() = fotos.size + if (podeAdicionar) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemFotoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val b = holder.binding
        if (podeAdicionar && position == 0) {
            b.ivFoto.setImageDrawable(null)
            b.tvAdicionar.visibility = View.VISIBLE
            b.root.setOnClickListener { aoAdicionar?.invoke() }
            b.root.setOnLongClickListener(null)
            return
        }
        val foto = fotos[position - if (podeAdicionar) 1 else 0]
        b.tvAdicionar.visibility = View.GONE
        b.ivFoto.clipToOutline = true
        b.ivFoto.load(foto.url)
        b.root.setOnClickListener { aoTocar(foto) }
        b.root.setOnLongClickListener {
            aoSegurar?.invoke(foto)
            aoSegurar != null
        }
    }
}

// Lista de serviços (nome, duração, preço).
class ServicosAdapter(
    private val aoTocar: ((Int, Servico) -> Unit)? = null,
    private val aoSegurar: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<ServicosAdapter.Holder>() {

    class Holder(val binding: ItemServicoBinding) : RecyclerView.ViewHolder(binding.root)

    private var servicos: List<Servico> = emptyList()

    fun atualizar(lista: List<Servico>) {
        servicos = lista
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun getItemCount() = servicos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemServicoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val s = servicos[position]
        val b = holder.binding
        b.tvNome.text = s.nome
        b.tvDetalhe.text = listOfNotNull(
            s.duracaoMin.takeIf { it > 0 }?.let { Formato.duracao(it) },
            s.descricao?.takeIf { it.isNotBlank() }
        ).joinToString("  ·  ")
        b.tvDetalhe.visibility = if (b.tvDetalhe.text.isNullOrBlank()) View.GONE else View.VISIBLE
        b.tvPreco.text = Formato.valor(s.preco)
        b.root.setOnClickListener { aoTocar?.invoke(position, s) }
        b.root.setOnLongClickListener {
            aoSegurar?.invoke(position)
            aoSegurar != null
        }
    }
}
