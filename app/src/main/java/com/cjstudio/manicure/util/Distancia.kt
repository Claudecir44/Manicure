package com.cjstudio.manicure.util

import com.cjstudio.manicure.data.repository.Coordenadas
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Distância em linha reta (fórmula de haversine), em km.
object Distancia {

    fun km(a: Coordenadas, b: Coordenadas): Double {
        val raioTerra = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
        return 2 * raioTerra * asin(sqrt(h))
    }

    // "800 m", "2,3 km", "15 km".
    fun texto(km: Double): String = when {
        km < 1 -> "${(km * 1000).toInt().coerceAtLeast(100) / 100 * 100} m"
        km < 10 -> String.format(Locale("pt", "BR"), "%.1f km", km)
        else -> "${km.toInt()} km"
    }
}
