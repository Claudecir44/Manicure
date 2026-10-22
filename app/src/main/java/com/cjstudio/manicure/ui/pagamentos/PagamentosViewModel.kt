package com.cjstudio.manicure.ui.pagamentos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.repository.IAgendaRepository
import com.cjstudio.manicure.data.repository.IAuthRepository
import com.cjstudio.manicure.data.repository.IPagamentoRepository
import com.cjstudio.manicure.data.repository.IProfissionalRepository
import com.cjstudio.manicure.data.repository.Pagamento
import com.cjstudio.manicure.data.repository.ResultadoCompra
import com.cjstudio.manicure.ui.common.UiTexto
import com.cjstudio.manicure.ui.common.paraUiTexto
import com.cjstudio.manicure.ui.common.texto
import com.cjstudio.manicure.util.AgendaUtil
import com.cjstudio.manicure.util.Assinatura
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Pagamentos da profissional: situação da assinatura, planos, agenda extra
// e histórico. A cobrança em si é do Google Play (CobrancaGooglePlay, na
// tela); aqui só as regras de quando pode pagar e a confirmação no servidor.
@HiltViewModel
class PagamentosViewModel @Inject constructor(
    private val auth: IAuthRepository,
    private val profissionais: IProfissionalRepository,
    private val agendas: IAgendaRepository,
    private val pagamentos: IPagamentoRepository
) : ViewModel() {

    val uid: String? = auth.uidLogado()

    data class Estado(
        val perfil: Profissional? = null,
        val assinatura: Assinatura.Estado? = null,
        // Agendas de meses que ainda não acabaram.
        val agendasEmUso: Int = 0,
        val historico: List<Pagamento> = emptyList(),
        val carregando: Boolean = true,
        val confirmando: Boolean = false
    )

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado

    private val _mensagens = Channel<UiTexto>(Channel.BUFFERED)
    val mensagens: Flow<UiTexto> = _mensagens.receiveAsFlow()

    fun atualizar() {
        viewModelScope.launch {
            _estado.update { it.copy(carregando = true) }
            val perfil = profissionais.buscarLogada().getOrNull()
            val emUso = agendas.minhasAgendas().getOrDefault(emptyList()).count { !AgendaUtil.mesPassado(it.mes) }
            val historico = pagamentos.meusPagamentos().getOrDefault(emptyList())
            _estado.update {
                it.copy(
                    perfil = perfil,
                    assinatura = perfil?.let { p -> Assinatura.estado(p) },
                    agendasEmUso = emUso,
                    historico = historico,
                    carregando = false
                )
            }
        }
    }

    // Antes de abrir o Google Play: confere as regras (a cobrança não volta atrás).
    fun motivoParaNaoComprar(produto: String): UiTexto? {
        val a = _estado.value.assinatura ?: return texto(R.string.pag_aguarde)
        return when {
            produto == Assinatura.PRODUTO_AGENDA_EXTRA && !a.podeComprarExtra -> texto(R.string.pag_extra_so_pago)
            produto != Assinatura.PRODUTO_AGENDA_EXTRA && !a.podeRenovar ->
                texto(R.string.pag_renovar_bloqueado, Assinatura.data(a.renovarAPartirDe), Assinatura.DIAS_PARA_RENOVAR)
            else -> null
        }
    }

    fun confirmar(purchaseToken: String, produto: String) {
        _estado.update { it.copy(confirmando = true) }
        viewModelScope.launch {
            pagamentos.confirmarCompra(purchaseToken, produto)
                .onSuccess { r ->
                    _mensagens.send(texto(if (r == ResultadoCompra.Pendente) R.string.pag_pendente else R.string.pag_liberado))
                }
                .onFailure { _mensagens.send(it.paraUiTexto()) }
            _estado.update { it.copy(confirmando = false) }
            atualizar()
        }
    }
}
