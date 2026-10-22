package com.cjstudio.manicure.ui.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Admin
import com.cjstudio.manicure.data.repository.IAdminRepository
import com.cjstudio.manicure.ui.common.UiTexto
import com.cjstudio.manicure.ui.common.paraUiTexto
import com.cjstudio.manicure.ui.common.texto
import com.cjstudio.manicure.util.CpfUtil
import com.cjstudio.manicure.util.Validacao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Novo administrador/colaborador, ou edição de um existente (EXTRA_ID).
// A trava de verdade (senha do master, permissão "administradores" de quem
// chama) é da Cloud Function.
@HiltViewModel
class CadastroAdminViewModel @Inject constructor(
    private val admins: IAdminRepository,
    estado: SavedStateHandle
) : ViewModel() {

    private val id: String? = estado[CadastroAdminActivity.EXTRA_ID]
    val editando: Boolean = id != null

    data class Formulario(
        val nome: String,
        val sobrenome: String,
        val email: String,
        val telefone: String,
        val cpf: String,
        val senha: String,
        val confirmarSenha: String,
        val colaborador: Boolean,
        val permissoes: Map<String, Boolean>,
        val senhaAutorizacao: String
    )

    sealed interface Evento {
        data object Cadastrado : Evento
        data object Salvo : Evento
        // Edição: dados atuais pra preencher a tela.
        data class Preencher(val admin: Admin) : Evento
        data class Mensagem(val texto: UiTexto) : Evento
    }

    private val _enviando = MutableStateFlow(false)
    val enviando: StateFlow<Boolean> = _enviando

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    init {
        if (id != null) {
            _enviando.value = true
            viewModelScope.launch {
                admins.buscar(id)
                    .onSuccess { _eventos.send(Evento.Preencher(it)) }
                    .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
                _enviando.value = false
            }
        }
    }

    fun cadastrar(form: Formulario) {
        if (_enviando.value) return
        validar(form)?.let {
            _eventos.trySend(Evento.Mensagem(it))
            return
        }
        _enviando.value = true
        viewModelScope.launch {
            val role = if (form.colaborador) "colaborador" else "admin"
            // Admin (não colaborador) tem tudo liberado.
            val permissoes = if (form.colaborador) form.permissoes else Admin.CHAVES_PERMISSOES.associate { it.first to true }
            val evento = if (id != null) {
                admins.atualizarAdmin(
                    uid = id,
                    nome = form.nome.trim(),
                    sobrenome = form.sobrenome.trim(),
                    telefone = form.telefone,
                    cpf = CpfUtil.somenteDigitos(form.cpf),
                    role = role,
                    permissoes = permissoes,
                    senhaAutorizacao = form.senhaAutorizacao
                ).fold({ Evento.Salvo }, { Evento.Mensagem(it.paraUiTexto()) })
            } else {
                admins.cadastrarAdmin(
                    nome = form.nome.trim(),
                    sobrenome = form.sobrenome.trim(),
                    email = form.email.trim(),
                    telefone = form.telefone,
                    cpf = CpfUtil.somenteDigitos(form.cpf),
                    senha = form.senha,
                    role = role,
                    permissoes = permissoes,
                    senhaAutorizacao = form.senhaAutorizacao
                ).fold({ Evento.Cadastrado }, { Evento.Mensagem(it.paraUiTexto()) })
            }
            _enviando.value = false
            _eventos.send(evento)
        }
    }

    // Na edição e-mail e senha não aparecem (não mudam por aqui).
    private fun validar(form: Formulario): UiTexto? = when {
        form.nome.isBlank() || form.sobrenome.isBlank() -> texto(R.string.cadastro_erro_nome)
        !editando && !Validacao.email(form.email.trim()) -> texto(R.string.cadastro_erro_email)
        !Validacao.telefone(form.telefone) -> texto(R.string.cadastro_erro_telefone)
        !CpfUtil.valido(form.cpf) -> texto(R.string.cadastro_erro_cpf)
        !editando && !Validacao.senha(form.senha) -> texto(R.string.cadastro_erro_senha)
        !editando && form.senha != form.confirmarSenha -> texto(R.string.cadastro_erro_senhas_diferentes)
        form.colaborador && form.permissoes.values.none { it } -> texto(R.string.admin_erro_permissoes)
        form.senhaAutorizacao.isBlank() -> texto(R.string.admin_erro_senha_master)
        else -> null
    }
}
