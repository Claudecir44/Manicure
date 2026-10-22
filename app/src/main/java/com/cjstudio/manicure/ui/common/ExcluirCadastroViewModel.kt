package com.cjstudio.manicure.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.data.repository.IContaRepository
import com.cjstudio.manicure.data.repository.TipoCadastro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// "Excluir meu cadastro" (Configurações da profissional e Meu perfil da
// cliente). Depois de excluir, a sessão já foi encerrada: a tela volta pro
// começo do app.
@HiltViewModel
class ExcluirCadastroViewModel @Inject constructor(
    private val conta: IContaRepository
) : ViewModel() {

    sealed interface Evento {
        data object Excluido : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    private val _excluindo = MutableStateFlow(false)
    val excluindo: StateFlow<Boolean> = _excluindo

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    fun excluir(tipo: TipoCadastro, senha: String) {
        if (_excluindo.value) return
        _excluindo.value = true
        viewModelScope.launch {
            conta.excluirMeuCadastro(tipo, senha)
                .onSuccess { _eventos.send(Evento.Excluido) }
                .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
            _excluindo.value = false
        }
    }
}
