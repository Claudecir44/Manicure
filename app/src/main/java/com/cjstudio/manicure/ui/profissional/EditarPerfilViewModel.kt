package com.cjstudio.manicure.ui.profissional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.repository.IProfissionalRepository
import com.cjstudio.manicure.ui.common.UiTexto
import com.cjstudio.manicure.ui.common.paraUiTexto
import com.cjstudio.manicure.ui.common.texto
import com.cjstudio.manicure.util.Validacao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Apresentação, contatos e localização do perfil da manicure.
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
        val atendimento: String
    )

    sealed interface Evento {
        data object Salvo : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    private val _perfil = MutableStateFlow<Profissional?>(null)
    val perfil: StateFlow<Profissional?> = _perfil

    private val _salvando = MutableStateFlow(false)
    val salvando: StateFlow<Boolean> = _salvando

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    init {
        viewModelScope.launch {
            profissionais.buscarLogada()
                .onSuccess { _perfil.value = it }
                .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }

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
        _salvando.value = true
        viewModelScope.launch {
            val r = profissionais.salvarPerfil(dados)
            _salvando.value = false
            r.onSuccess {
                _eventos.send(Evento.Mensagem(texto(R.string.editar_salvo)))
                _eventos.send(Evento.Salvo)
            }.onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }
}
