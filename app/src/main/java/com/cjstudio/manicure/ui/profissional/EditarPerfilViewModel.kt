package com.cjstudio.manicure.ui.profissional

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.FotoPortfolio
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.model.Servico
import com.cjstudio.manicure.data.repository.IProfissionalRepository
import com.cjstudio.manicure.ui.common.UiTexto
import com.cjstudio.manicure.ui.common.paraUiTexto
import com.cjstudio.manicure.ui.common.texto
import com.cjstudio.manicure.util.Validacao
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

// Meu perfil da manicure: dados de texto e categorias (gravados no "Salvar"),
// e foto, serviços e galeria (gravados na hora, como antes no painel).
@HiltViewModel
class EditarPerfilViewModel @Inject constructor(
    private val profissionais: IProfissionalRepository
) : ViewModel() {

    data class Formulario(
        val nomeExibicao: String,
        val tipoPerfil: String,
        val nomeResponsavel: String,
        val apresentacao: String,
        val sobreTrabalho: String,
        val whatsapp: String,
        val telefone: String,
        val instagram: String,
        val cidade: String,
        val bairro: String,
        val endereco: String,
        val horarioAtendimento: String,
        val atendimento: String,
        val categorias: List<String>
    )

    sealed interface Evento {
        data object Salvo : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    // Declarado antes do perfil: o catch dele usa o canal.
    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    val perfil: StateFlow<Profissional?> = profissionais.escutarLogada()
        .catch {
            _eventos.send(Evento.Mensagem(it.paraUiTexto()))
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Salvando o formulário ou enviando foto/serviço.
    private val _salvando = MutableStateFlow(false)
    val salvando: StateFlow<Boolean> = _salvando

    fun salvar(f: Formulario) {
        if (_salvando.value) return
        val erro = when {
            f.nomeExibicao.trim().length < 3 -> texto(R.string.editar_erro_nome)
            f.cidade.isBlank() -> texto(R.string.editar_erro_cidade)
            f.whatsapp.isNotBlank() && !Validacao.telefone(f.whatsapp) -> texto(R.string.editar_erro_whatsapp)
            else -> null
        }
        if (erro != null) {
            _eventos.trySend(Evento.Mensagem(erro))
            return
        }
        val dados = mapOf(
            "nomeExibicao" to f.nomeExibicao.trim(),
            "tipoPerfil" to f.tipoPerfil,
            "nomeResponsavel" to f.nomeResponsavel.trim(),
            "apresentacao" to f.apresentacao.trim(),
            "sobreTrabalho" to f.sobreTrabalho.trim(),
            "whatsapp" to f.whatsapp,
            "telefone" to f.telefone,
            // Instagram sempre sem @ e sem espaço (vira link).
            "instagram" to f.instagram.trim().removePrefix("@").replace(" ", ""),
            "cidade" to f.cidade.trim(),
            "bairro" to f.bairro.trim(),
            "endereco" to f.endereco.trim(),
            "horarioAtendimento" to f.horarioAtendimento.trim(),
            "atendimento" to f.atendimento
        )
        val mudouCategorias = f.categorias.toSet() != perfil.value?.categorias.orEmpty().toSet()
        _salvando.value = true
        viewModelScope.launch {
            val r = profissionais.salvarPerfil(dados).let { r ->
                if (r.isSuccess && mudouCategorias) profissionais.salvarCategorias(f.categorias) else r
            }
            _salvando.value = false
            r.onSuccess {
                _eventos.send(Evento.Mensagem(texto(R.string.editar_salvo)))
                _eventos.send(Evento.Salvo)
            }.onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
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

    fun removerServico(posicao: Int) {
        val lista = perfil.value?.servicos.orEmpty().toMutableList()
        if (posicao !in lista.indices) return
        lista.removeAt(posicao)
        executar(R.string.painel_servico_removido) { profissionais.salvarServicos(lista) }
    }

    private fun executar(sucesso: Int, acao: suspend () -> Result<Unit>) {
        if (_salvando.value) return
        _salvando.value = true
        viewModelScope.launch {
            val r = acao()
            _salvando.value = false
            _eventos.send(Evento.Mensagem(r.fold({ texto(sucesso) }, { it.paraUiTexto() })))
        }
    }
}
