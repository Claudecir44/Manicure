package com.cjstudio.manicure.ui.auth

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.repository.IClienteRepository
import com.cjstudio.manicure.data.repository.IProfissionalRepository
import com.cjstudio.manicure.data.repository.ResultadoCadastro
import com.cjstudio.manicure.ui.common.UiTexto
import com.cjstudio.manicure.ui.common.paraUiTexto
import com.cjstudio.manicure.ui.common.texto
import com.cjstudio.manicure.util.Papel
import com.cjstudio.manicure.util.Validacao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Cadastro de profissional ou cliente (mesmos campos básicos). A
// profissional completa o perfil público depois, no próprio painel.
@HiltViewModel
class CadastroViewModel @Inject constructor(
    private val profissionais: IProfissionalRepository,
    private val clientes: IClienteRepository,
    private val estado: SavedStateHandle
) : ViewModel() {

    val papel: Papel = (estado.get<String>(LoginViewModel.EXTRA_PAPEL))?.let { Papel.valueOf(it) } ?: Papel.CLIENTE
    val completando: Boolean = estado[LoginViewModel.EXTRA_COMPLETAR_CADASTRO] ?: false

    // Foto da cliente (obrigatória), já reduzida pra envio. Fica no
    // SavedStateHandle pra não sumir ao girar a tela.
    val foto: StateFlow<String?> = estado.getStateFlow(CHAVE_FOTO, null)

    fun limparFoto() {
        estado[CHAVE_FOTO] = null
    }

    fun escolherFoto(uri: Uri) {
        estado[CHAVE_FOTO] = uri.toString()
    }

    data class Formulario(
        val nome: String,
        val telefone: String,
        val email: String,
        val senha: String,
        val confirmarSenha: String,
        val aceitouTermos: Boolean
    )

    sealed interface Evento {
        data class ConfirmarEmail(val email: String) : Evento
        data object IrParaInicio : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    private val _enviando = MutableStateFlow(false)
    val enviando: StateFlow<Boolean> = _enviando

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    fun cadastrar(form: Formulario) {
        if (_enviando.value) return
        validar(form)?.let {
            _eventos.trySend(Evento.Mensagem(it))
            return
        }
        _enviando.value = true
        val senha = form.senha.takeIf { !completando }
        viewModelScope.launch {
            val resultado = when (papel) {
                Papel.PROFISSIONAL -> profissionais.cadastrar(form.nome, form.email.trim(), form.telefone, senha)
                Papel.CLIENTE -> clientes.cadastrar(form.nome, form.email.trim(), form.telefone, senha, Uri.parse(foto.value))
            }
            _enviando.value = false
            _eventos.send(
                resultado.fold(
                    { fim -> if (fim == ResultadoCadastro.PRONTO) Evento.IrParaInicio else Evento.ConfirmarEmail(form.email.trim()) },
                    { Evento.Mensagem(it.paraUiTexto()) }
                )
            )
        }
    }

    private fun validar(form: Formulario): UiTexto? = when {
        papel == Papel.CLIENTE && foto.value == null -> texto(R.string.cadastro_erro_foto)
        form.nome.trim().length < 3 -> texto(R.string.cadastro_erro_nome)
        !Validacao.telefone(form.telefone) -> texto(R.string.cadastro_erro_telefone)
        !completando && !Validacao.email(form.email.trim()) -> texto(R.string.cadastro_erro_email)
        !completando && !Validacao.senha(form.senha) -> texto(R.string.cadastro_erro_senha)
        !completando && form.senha != form.confirmarSenha -> texto(R.string.cadastro_erro_senhas_diferentes)
        !form.aceitouTermos -> texto(R.string.cadastro_erro_termos)
        else -> null
    }

    private companion object {
        const val CHAVE_FOTO = "fotoCliente"
    }
}
