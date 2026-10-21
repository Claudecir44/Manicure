package com.cjstudio.manicure.ui.profissional

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.FotoPortfolio
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.model.Servico
import com.cjstudio.manicure.data.repository.IAuthRepository
import com.cjstudio.manicure.data.repository.IFalhasRepository
import com.cjstudio.manicure.data.repository.IProfissionalRepository
import com.cjstudio.manicure.ui.common.UiTexto
import com.cjstudio.manicure.ui.common.paraUiTexto
import com.cjstudio.manicure.ui.common.texto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Painel da manicure: o perfil dela em tempo real e as ações de galeria,
// serviços e foto. Os textos do perfil são editados em EditarPerfilActivity.
@HiltViewModel
class PainelProfissionalViewModel @Inject constructor(
    private val auth: IAuthRepository,
    private val profissionais: IProfissionalRepository,
    private val falhas: IFalhasRepository
) : ViewModel() {

    sealed interface Evento {
        data object Saiu : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    val perfil: StateFlow<Profissional?> = profissionais.escutarLogada()
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Envio de foto em andamento (galeria ou foto do perfil).
    private val _enviando = MutableStateFlow(false)
    val enviando: StateFlow<Boolean> = _enviando

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    init {
        falhas.definirUsuario(auth.uidLogado())
        viewModelScope.launch { profissionais.atualizarPerfilAntigo() }
    }

    fun trocarFotoPerfil(uri: Uri) = executar(R.string.painel_foto_perfil_salva) { profissionais.trocarFotoPerfil(uri) }

    fun adicionarFoto(uri: Uri) {
        if ((perfil.value?.fotos?.size ?: 0) >= Profissional.MAX_FOTOS) {
            _eventos.trySend(Evento.Mensagem(texto(R.string.erro_limite_fotos, Profissional.MAX_FOTOS)))
            return
        }
        executar(R.string.painel_foto_adicionada) { profissionais.adicionarFoto(uri) }
    }

    fun removerFoto(foto: FotoPortfolio) = executar(R.string.painel_foto_removida) { profissionais.removerFoto(foto) }

    // posicao == null: serviço novo.
    fun salvarServico(posicao: Int?, servico: Servico) {
        val lista = perfil.value?.servicos.orEmpty().toMutableList()
        if (posicao != null && posicao in lista.indices) lista[posicao] = servico else lista += servico
        executar(R.string.painel_servico_salvo) { profissionais.salvarServicos(lista) }
    }

    fun salvarCategorias(categorias: List<String>) =
        executar(R.string.painel_categorias_salvas) { profissionais.salvarCategorias(categorias) }

    fun removerServico(posicao: Int) {
        val lista = perfil.value?.servicos.orEmpty().toMutableList()
        if (posicao !in lista.indices) return
        lista.removeAt(posicao)
        executar(R.string.painel_servico_removido) { profissionais.salvarServicos(lista) }
    }

    private fun executar(sucesso: Int, acao: suspend () -> Result<Unit>) {
        if (_enviando.value) return
        _enviando.value = true
        viewModelScope.launch {
            val r = acao()
            _enviando.value = false
            _eventos.send(Evento.Mensagem(r.fold({ texto(sucesso) }, { it.paraUiTexto() })))
        }
    }

    fun sair() {
        viewModelScope.launch {
            auth.sair()
            _eventos.send(Evento.Saiu)
        }
    }
}
