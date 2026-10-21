package com.cjstudio.manicure.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject

data class Coordenadas(val latitude: Double, val longitude: Double)

// Posição do aparelho (cliente: profissionais mais perto no topo) e
// coordenadas de um endereço (perfil da profissional).
interface ILocalizacaoRepository {
    fun temPermissao(): Boolean

    // null sem permissão, sem GPS ou se demorar demais.
    suspend fun posicaoAtual(): Coordenadas?

    // "Rua X, Bairro, Cidade" -> coordenadas; null se não achar.
    suspend fun coordenadasDoEndereco(endereco: String): Coordenadas?
}

class LocalizacaoRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : ILocalizacaoRepository {

    override fun temPermissao(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission")
    override suspend fun posicaoAtual(): Coordenadas? {
        if (!temPermissao()) return null
        val cliente = LocationServices.getFusedLocationProviderClient(context)
        return runCatching {
            // Última posição conhecida é instantânea; se não houver, pede uma nova.
            val local = cliente.lastLocation.await()
                ?: withTimeoutOrNull(10_000) {
                    cliente.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
                }
            local?.let { Coordenadas(it.latitude, it.longitude) }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    override suspend fun coordenadasDoEndereco(endereco: String): Coordenadas? = withContext(Dispatchers.IO) {
        if (endereco.isBlank() || !Geocoder.isPresent()) return@withContext null
        runCatching {
            Geocoder(context, Locale("pt", "BR")).getFromLocationName("$endereco, Brasil", 1)
                ?.firstOrNull()
                ?.let { Coordenadas(it.latitude, it.longitude) }
        }.getOrNull()
    }
}
