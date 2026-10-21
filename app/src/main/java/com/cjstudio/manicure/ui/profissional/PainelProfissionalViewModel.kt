package com.cjstudio.manicure.ui.profissional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.repository.IAuthRepository
import com.cjstudio.manicure.data.repository.IChatRepository
import com.cjstudio.manicure.data.repository.IFalhasRepository
import com.cjstudio.manicure.data.repository.INotificacaoRepository
import com.cjstudio.manicure.data.repository.IProfissionalRepository
import com.cjstudio.manicure.data.repository.NaoLidas
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Painel da manicure: o perfil dela em tempo real (pro resumo) e o sair.
// Dados, categorias, serviços e galeria são editados em EditarPerfilActivity.
@HiltViewModel
class PainelProfissionalViewModel @Inject constructor(
    private val auth: IAuthRepository,
    profissionais: IProfissionalRepository,
    falhas: IFalhasRepository,
    chat: IChatRepository,
    private val notificacoes: INotificacaoRepository
) : ViewModel() {

    // Mensagens não lidas (ícone de chat do painel e número no ícone do app).
    val naoLidas: StateFlow<NaoLidas> = chat.escutarNaoLidas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NaoLidas())

    sealed interface Evento {
        data object Saiu : Evento
    }

    val perfil: StateFlow<Profissional?> = profissionais.escutarLogada()
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    init {
        falhas.definirUsuario(auth.uidLogado())
        viewModelScope.launch { profissionais.atualizarPerfilAntigo() }
        viewModelScope.launch { notificacoes.registrarToken() }
    }

    fun sair() {
        viewModelScope.launch {
            auth.sair()
            _eventos.send(Evento.Saiu)
        }
    }
}
