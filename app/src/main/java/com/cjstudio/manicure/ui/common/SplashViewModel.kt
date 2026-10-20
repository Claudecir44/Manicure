package com.cjstudio.manicure.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.data.repository.IAdminRepository
import com.cjstudio.manicure.data.repository.IAuthRepository
import com.cjstudio.manicure.data.repository.IClienteRepository
import com.cjstudio.manicure.data.repository.IProfissionalRepository
import com.cjstudio.manicure.util.AppTipo
import com.cjstudio.manicure.util.Papel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Decide a primeira tela: sessão válida + perfil existente. No app manicure,
// o perfil diz o papel (profissional tem prioridade se a conta tiver os dois).
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val auth: IAuthRepository,
    private val profissionais: IProfissionalRepository,
    private val clientes: IClienteRepository,
    private val admins: IAdminRepository
) : ViewModel() {

    sealed interface Destino {
        data object Login : Destino
        data class Inicial(val papel: Papel?) : Destino
    }

    private val _destino = MutableStateFlow<Destino?>(null)
    val destino: StateFlow<Destino?> = _destino

    init {
        viewModelScope.launch {
            _destino.value = try {
                when {
                    !auth.sessaoValida() -> Destino.Login
                    AppTipo.ehAdmin -> if (admins.souAdmin().getOrDefault(false)) Destino.Inicial(null) else Destino.Login
                    else -> papelAtual()?.let { Destino.Inicial(it) } ?: Destino.Login
                }
            } catch (_: Exception) {
                Destino.Login
            }
        }
    }

    private suspend fun papelAtual(): Papel? {
        profissionais.buscarLogada().getOrNull()?.let { if (!it.bloqueado) return Papel.PROFISSIONAL }
        clientes.buscarLogada().getOrNull()?.let { if (!it.bloqueado) return Papel.CLIENTE }
        return null
    }
}
