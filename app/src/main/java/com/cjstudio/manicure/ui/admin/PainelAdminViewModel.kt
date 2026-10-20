package com.cjstudio.manicure.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Admin
import com.cjstudio.manicure.data.model.Cliente
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.repository.IAdminRepository
import com.cjstudio.manicure.data.repository.IAuthRepository
import com.cjstudio.manicure.data.repository.IClienteRepository
import com.cjstudio.manicure.data.repository.IFalhasRepository
import com.cjstudio.manicure.data.repository.IProfissionalRepository
import com.cjstudio.manicure.ui.common.UiTexto
import com.cjstudio.manicure.ui.common.paraUiTexto
import com.cjstudio.manicure.ui.common.texto
import com.cjstudio.manicure.util.CpfUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Painel do admin. Igual ao Caronas: cada seção (Profissionais, Clientes,
// Administradores) abre NA MESMA TELA, logo abaixo dos botões — nunca numa
// Activity separada.
@HiltViewModel
class PainelAdminViewModel @Inject constructor(
    private val auth: IAuthRepository,
    private val admins: IAdminRepository,
    private val profissionais: IProfissionalRepository,
    private val clientes: IClienteRepository,
    private val falhas: IFalhasRepository
) : ViewModel() {

    enum class Secao { PROFISSIONAIS, CLIENTES, ADMINISTRADORES }

    data class Estado(
        val admin: Admin? = null,
        val secao: Secao? = null,
        val itens: List<ItemPessoa> = emptyList(),
        val carregando: Boolean = false
    )

    // Toque numa profissional ou cliente: ver perfil / bloquear.
    data class Alvo(val id: String, val nome: String, val bloqueado: Boolean, val profissional: Boolean)

    sealed interface Evento {
        data object Saiu : Evento
        data class Mensagem(val texto: UiTexto) : Evento
        data class Opcoes(val alvo: Alvo) : Evento
    }

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos: Flow<Evento> = _eventos.receiveAsFlow()

    private var alvos: Map<String, Alvo> = emptyMap()

    init {
        falhas.definirUsuario(auth.uidLogado())
    }

    // Chamado no onResume: volta do cadastro de admin já com a lista atualizada.
    fun atualizar() {
        viewModelScope.launch {
            admins.buscarLogado()
                .onSuccess { admin -> _estado.update { it.copy(admin = admin) } }
                .onFailure {
                    // Acesso de admin revogado com o app aberto.
                    sair()
                    return@launch
                }
            _estado.value.secao?.let { abrir(it) }
        }
    }

    fun abrir(secao: Secao) {
        _estado.update { it.copy(secao = secao, carregando = true, itens = emptyList()) }
        viewModelScope.launch {
            val itens: Result<List<ItemPessoa>> = when (secao) {
                Secao.PROFISSIONAIS -> profissionais.listarTodas().map { lista ->
                    alvos = lista.mapNotNull { p -> p.id?.let { it to Alvo(it, p.nomeExibicao.orEmpty(), p.bloqueado, true) } }.toMap()
                    lista.map { it.paraItem() }
                }
                Secao.CLIENTES -> clientes.listarTodas().map { lista ->
                    alvos = lista.mapNotNull { c -> c.id?.let { it to Alvo(it, c.nome.orEmpty(), c.bloqueado, false) } }.toMap()
                    lista.map { it.paraItem() }
                }
                Secao.ADMINISTRADORES -> admins.listarTodos().map { lista -> lista.map { it.paraItem() } }
            }
            itens.onSuccess { lista -> _estado.update { it.copy(itens = lista, carregando = false) } }
                .onFailure { e ->
                    _estado.update { it.copy(carregando = false) }
                    _eventos.send(Evento.Mensagem(e.paraUiTexto()))
                }
        }
    }

    fun tocou(id: String) {
        if (_estado.value.secao == Secao.ADMINISTRADORES) return
        alvos[id]?.let { _eventos.trySend(Evento.Opcoes(it)) }
    }

    fun alternarBloqueio(alvo: Alvo) {
        viewModelScope.launch {
            val resultado = if (alvo.profissional) {
                profissionais.definirBloqueio(alvo.id, !alvo.bloqueado)
            } else {
                clientes.definirBloqueio(alvo.id, !alvo.bloqueado)
            }
            resultado
                .onSuccess {
                    _eventos.send(Evento.Mensagem(texto(if (alvo.bloqueado) R.string.admin_desbloqueado else R.string.admin_bloqueado)))
                    _estado.value.secao?.let { abrir(it) }
                }
                .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }

    fun trocarMinhaFoto(uri: android.net.Uri) {
        viewModelScope.launch {
            admins.atualizarMinhaFoto(uri)
                .onSuccess { url -> _estado.update { it.copy(admin = it.admin?.copy(fotoUrl = url)) } }
                .onFailure { _eventos.send(Evento.Mensagem(it.paraUiTexto())) }
        }
    }

    fun sair() {
        viewModelScope.launch {
            auth.sair()
            _eventos.send(Evento.Saiu)
        }
    }

    private fun Profissional.paraItem(): ItemPessoa {
        val (selo, cor) = when {
            bloqueado -> R.string.status_bloqueado to R.color.vermelho
            publicado -> R.string.status_publicado to R.color.verde
            else -> R.string.status_incompleto to R.color.texto_secundario
        }
        return ItemPessoa(
            id = id.orEmpty(),
            nome = nomeExibicao.orEmpty(),
            linha2 = listOfNotNull(telefone, email).joinToString("  ·  "),
            linha3 = listOfNotNull(
                codigo,
                listOfNotNull(bairro, cidade).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
                "${fotos.size} fotos"
            ).joinToString("  ·  "),
            fotoUrl = fotoPerfilUrl,
            selo = selo,
            corSelo = cor
        )
    }

    private fun Cliente.paraItem() = ItemPessoa(
        id = id.orEmpty(),
        nome = nome.orEmpty(),
        linha2 = listOfNotNull(telefone, email).joinToString("  ·  "),
        linha3 = "",
        fotoUrl = null,
        selo = if (bloqueado) R.string.status_bloqueado else null,
        corSelo = if (bloqueado) R.color.vermelho else null
    )

    private fun Admin.paraItem() = ItemPessoa(
        id = id.orEmpty(),
        nome = nomeCompleto,
        linha2 = listOfNotNull(telefone, email).joinToString("  ·  "),
        linha3 = "CPF ${CpfUtil.formatar(cpf)}",
        fotoUrl = fotoUrl,
        selo = when {
            ehMaster -> R.string.admin_selo_master
            ehColaborador -> R.string.admin_selo_colaborador
            else -> R.string.admin_selo_admin
        },
        corSelo = R.color.rosa
    )
}
