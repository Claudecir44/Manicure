package com.cjstudio.manicure.ui.common

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cjstudio.manicure.R
import com.cjstudio.manicure.databinding.ItemLinhaBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

// Linha de lista simples (agendas, horários marcados): o ViewModel ou a
// tela entrega o texto pronto, o adapter só desenha.
data class Linha(
    val id: String,
    val titulo: String,
    val linha2: String = "",
    val linha3: String = "",
    val selo: String = ""
)

class LinhaAdapter(private val aoTocar: (Linha) -> Unit) : ListAdapter<Linha, LinhaAdapter.Holder>(Diferenca) {

    class Holder(val binding: ItemLinhaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemLinhaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvTitulo.text = item.titulo
            tvLinha2.text = item.linha2
            tvLinha2.visibility = if (item.linha2.isBlank()) View.GONE else View.VISIBLE
            tvLinha3.text = item.linha3
            tvLinha3.visibility = if (item.linha3.isBlank()) View.GONE else View.VISIBLE
            tvSelo.text = item.selo
            tvSelo.visibility = if (item.selo.isBlank()) View.GONE else View.VISIBLE
            root.setOnClickListener { aoTocar(item) }
        }
    }

    private object Diferenca : DiffUtil.ItemCallback<Linha>() {
        override fun areItemsTheSame(a: Linha, b: Linha) = a.id == b.id
        override fun areContentsTheSame(a: Linha, b: Linha) = a == b
    }
}

// Chip marcável na cor de destaque da tela (rosa ou verde).
fun ChipGroup.adicionarChip(texto: String, chave: String, marcado: Boolean = false): Chip {
    val chip = Chip(context).apply {
        text = texto
        tag = chave
        isCheckable = true
        isCheckedIconVisible = false
        chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.chip_fundo)
        setTextColor(ContextCompat.getColorStateList(context, R.color.chip_texto))
        chipStrokeWidth = 0f
        isChecked = marcado
    }
    addView(chip)
    return chip
}

// Chip com "x" pra remover (lista de horários da agenda).
fun ChipGroup.adicionarChipRemovivel(texto: String, aoRemover: () -> Unit): Chip {
    val chip = Chip(context).apply {
        text = texto
        isCloseIconVisible = true
        chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.superficie_alta))
        setTextColor(ContextCompat.getColor(context, R.color.texto))
        setOnCloseIconClickListener { aoRemover() }
    }
    addView(chip)
    return chip
}
