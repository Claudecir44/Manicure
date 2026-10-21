package com.cjstudio.manicure.ui.agenda

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.cjstudio.manicure.R
import com.cjstudio.manicure.databinding.ItemDiaCalendarioBinding
import com.cjstudio.manicure.util.AgendaUtil
import com.cjstudio.manicure.util.AgendaUtil.SituacaoDia

// Grade do mês (7 colunas, começando no domingo). Verde = aberto,
// preto = sem agenda, vermelho = tem horário marcado, cinza = já passou.
class CalendarioAdapter(private val aoTocar: (String) -> Unit) : RecyclerView.Adapter<CalendarioAdapter.Holder>() {

    class Holder(val binding: ItemDiaCalendarioBinding) : RecyclerView.ViewHolder(binding.root)

    private var dias: List<AgendaUtil.DiaCalendario> = emptyList()
    private var selecionado: String? = null

    fun atualizar(lista: List<AgendaUtil.DiaCalendario>, diaSelecionado: String?) {
        dias = lista
        selecionado = diaSelecionado
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun getItemCount() = dias.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemDiaCalendarioBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val dia = dias[position]
        val tv = holder.binding.tvDia
        val data = dia.data
        if (data == null) {
            tv.visibility = View.INVISIBLE
            holder.binding.root.setOnClickListener(null)
            return
        }
        val ctx = tv.context
        val densidade = ctx.resources.displayMetrics.density
        val (fundo, texto) = when (dia.situacao) {
            SituacaoDia.PASSADO -> R.color.cinza_dia to R.color.texto_secundario
            SituacaoDia.MARCADO -> R.color.vermelho to R.color.branco
            SituacaoDia.ABERTO -> R.color.verde_neon to R.color.preto
            SituacaoDia.FECHADO -> R.color.preto to R.color.dica
        }
        // Dia tocado: anel branco grosso. Hoje: anel fino na cor de destaque.
        val (anel, corAnel) = when {
            data == selecionado -> 3 to R.color.branco
            dia.hoje -> 2 to null
            dia.situacao == SituacaoDia.FECHADO -> 1 to R.color.borda
            else -> 0 to R.color.borda
        }
        tv.visibility = View.VISIBLE
        tv.text = dia.numero.toString()
        tv.setTextColor(ContextCompat.getColor(ctx, texto))
        tv.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(ctx, fundo))
            // Anel de hoje na cor que a profissional escolheu em Aparência.
            val cor = corAnel?.let { ContextCompat.getColor(ctx, it) } ?: MaterialColors.getColor(tv, R.attr.corDestaqueClaro)
            if (anel > 0) setStroke((anel * densidade).toInt(), cor)
        }
        holder.binding.root.setOnClickListener { aoTocar(data) }
    }
}
