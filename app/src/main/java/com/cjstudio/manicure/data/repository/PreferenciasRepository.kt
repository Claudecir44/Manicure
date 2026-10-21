package com.cjstudio.manicure.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.cjstudio.manicure.di.KEY_PERFIL_FAVORITO
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// Preferências da cliente neste aparelho: o perfil que abre direto ao entrar.
interface IPreferenciasRepository {
    suspend fun perfilFavorito(): String?
    suspend fun definirPerfilFavorito(id: String?)
}

class PreferenciasRepository @Inject constructor(
    private val prefs: DataStore<Preferences>
) : IPreferenciasRepository {

    override suspend fun perfilFavorito(): String? = prefs.data.first()[KEY_PERFIL_FAVORITO]

    override suspend fun definirPerfilFavorito(id: String?) {
        prefs.edit { if (id == null) it.remove(KEY_PERFIL_FAVORITO) else it[KEY_PERFIL_FAVORITO] = id }
    }
}
