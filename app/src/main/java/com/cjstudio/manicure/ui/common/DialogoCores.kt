package com.cjstudio.manicure.ui.common

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.cjstudio.manicure.R
import com.cjstudio.manicure.util.Aparencia
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// Caixa de cores do botão "Aparência" (painel da profissional e perfil
// público). Mostra as cores em 3 colunas, com ✓ na atual; tocar numa
// cor diferente chama aoEscolher.
object DialogoCores {

    fun mostrar(
        activity: Activity,
        @StringRes titulo: Int,
        @StringRes dica: Int,
        atual: Aparencia.Cor?,
        aoEscolher: (Aparencia.Cor) -> Unit
    ) {
        val d = activity.resources.displayMetrics.density
        val grade = GridLayout(activity).apply {
            columnCount = 3
            setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), 0)
        }
        val dialogo = MaterialAlertDialogBuilder(activity)
            .setTitle(titulo)
            .setMessage(dica)
            .setView(grade)
            .setNegativeButton(R.string.cancelar, null)
            .create()
        Aparencia.CORES.forEach { cor ->
            val escolhida = cor == atual
            val bolinha = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams((54 * d).toInt(), (54 * d).toInt())
                gravity = Gravity.CENTER
                text = if (escolhida) "✓" else ""
                textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(activity, R.color.preto))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(cor.amostra)
                    setStroke(((if (escolhida) 3 else 1) * d).toInt(), ContextCompat.getColor(activity, if (escolhida) R.color.texto else R.color.borda))
                }
            }
            val nome = TextView(activity).apply {
                setText(cor.nome)
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(ContextCompat.getColor(activity, if (escolhida) R.color.texto else R.color.texto_secundario))
                setPadding(0, (6 * d).toInt(), 0, 0)
            }
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, (10 * d).toInt(), 0, (10 * d).toInt())
                isClickable = true
                isFocusable = true
                addView(bolinha)
                addView(nome)
                setOnClickListener {
                    dialogo.dismiss()
                    if (!escolhida) aoEscolher(cor)
                }
            }
            grade.addView(item, GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply { width = 0 })
        }
        dialogo.show()
    }
}
