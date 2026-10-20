package com.cjstudio.manicure.util

import java.text.NumberFormat
import java.util.Locale

// Valores em reais e durações em texto ("R$ 50,00", "1 h 30 min").
object Formato {
    private val moeda = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    fun valor(v: Double): String = moeda.format(v)

    fun duracao(minutos: Int): String = when {
        minutos < 60 -> "$minutos min"
        minutos % 60 == 0 -> "${minutos / 60} h"
        else -> "${minutos / 60} h ${minutos % 60} min"
    }

    // "50" / "50,5" / "1.050,50" / "50.5" -> número (null se não for).
    fun lerValor(texto: String): Double? {
        val limpo = texto.replace("R$", "").trim()
        return if (limpo.contains(',')) limpo.replace(".", "").replace(",", ".").toDoubleOrNull() else limpo.toDoubleOrNull()
    }
}
