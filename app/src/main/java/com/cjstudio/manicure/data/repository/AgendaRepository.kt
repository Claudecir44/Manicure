package com.cjstudio.manicure.data.repository

import com.cjstudio.manicure.data.model.Agenda
import com.cjstudio.manicure.data.model.Agendamento
import com.cjstudio.manicure.data.model.HorarioOcupadoException
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.util.Assinatura
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

// Agendas mensais da profissional e os horários marcados pelas clientes.
interface IAgendaRepository {
    // ===== Profissional (as próprias agendas) =====
    suspend fun minhasAgendas(): Result<List<Agenda>>
    suspend fun salvarAgenda(agenda: Agenda): Result<Unit>
    suspend fun excluirAgenda(agenda: Agenda): Result<Unit>
    suspend fun meusAgendamentos(): Result<List<Agendamento>>
    suspend fun cancelarAgendamento(agendamento: Agendamento): Result<Unit>

    // ===== Cliente =====
    suspend fun agendasDaProfissional(profissionalId: String): Result<List<Agenda>>
    suspend fun reservar(agendamento: Agendamento): Result<Unit>
    suspend fun horariosDaCliente(): Result<List<Agendamento>>
    suspend fun cancelarMeuHorario(agendamento: Agendamento): Result<Unit>
}

@Singleton
class AgendaRepository @Inject constructor(
    private val db: Provider<FirebaseFirestore>,
    private val auth: IAuthRepository
) : IAgendaRepository {

    private fun uid() = auth.uidLogado() ?: throw IllegalStateException("Não há sessão ativa.")
    private fun agendas(profissionalId: String) = db.get().collection("profissionais").document(profissionalId).collection("agendas")
    private fun agendamentos(profissionalId: String) = db.get().collection("profissionais").document(profissionalId).collection("agendamentos")
    private fun copiasDaCliente(clienteId: String) = db.get().collection("clientes").document(clienteId).collection("agendamentos")

    override suspend fun minhasAgendas(): Result<List<Agenda>> = agendasDaProfissional(uid())

    override suspend fun agendasDaProfissional(profissionalId: String): Result<List<Agenda>> = runCatching {
        // Assinatura vencida: as clientes não veem as agendas (as regras
        // também recusam a reserva). A própria profissional sempre vê.
        if (profissionalId != auth.uidLogado()) {
            val perfil = etapa("ler perfil") { db.get().collection("profissionais").document(profissionalId).get().await() }
                .toObject(Profissional::class.java)
            if (perfil != null && Assinatura.estado(perfil).situacao == Assinatura.Situacao.VENCIDA) return@runCatching emptyList()
        }
        etapa("ler agendas") { agendas(profissionalId).get().await() }
            .documents.mapNotNull { d -> d.toObject(Agenda::class.java)?.also { it.id = d.id } }
            .sortedWith(compareBy<Agenda> { it.mes }.thenBy { it.categoria })
    }

    // Nova (id null) ou edição: na edição os horários já ocupados ficam.
    override suspend fun salvarAgenda(agenda: Agenda): Result<Unit> = runCatching {
        val dados = mapOf(
            "categoria" to agenda.categoria,
            "mes" to agenda.mes,
            "atendente" to agenda.atendente,
            "dias" to agenda.dias.sorted(),
            // Agenda nova não usa mais dias da semana (vale a lista de dias).
            "diasSemana" to emptyList<Int>(),
            "horarios" to agenda.horarios,
            "bloqueados" to agenda.bloqueados
        )
        etapa("salvar agenda") {
            val id = agenda.id
            if (id == null) {
                // A primeira agenda publicada começa os 30 dias grátis (no
                // mesmo lote: as regras conferem o teste ao criar a agenda).
                val perfilRef = db.get().collection("profissionais").document(uid())
                val semTeste = perfilRef.get().await().get("testeInicioEm") == null
                val lote = db.get().batch()
                lote.set(agendas(uid()).document(), dados + mapOf("ocupados" to emptyList<String>(), "criadoEm" to FieldValue.serverTimestamp()))
                if (semTeste) lote.update(perfilRef, "testeInicioEm", FieldValue.serverTimestamp())
                lote.commit().await()
            } else {
                agendas(uid()).document(id).update(dados).await()
            }
        }
        Unit
    }

    // Os horários já marcados nesta agenda são desmarcados junto (some
    // também a cópia em "Meus horários" da cliente) — senão ficavam
    // marcações apontando pra uma agenda que não existe mais.
    override suspend fun excluirAgenda(agenda: Agenda): Result<Unit> = runCatching {
        val id = agenda.id ?: return@runCatching
        val marcados = etapa("ler horários da agenda") { agendamentos(uid()).whereEqualTo("agendaId", id).get().await() }
        val lote = db.get().batch()
        marcados.documents.forEach { d ->
            lote.delete(d.reference)
            d.getString("clienteId")?.let { lote.delete(copiasDaCliente(it).document(d.id)) }
        }
        lote.delete(agendas(uid()).document(id))
        etapa("excluir agenda") { lote.commit().await() }
        Unit
    }

    override suspend fun meusAgendamentos(): Result<List<Agendamento>> = runCatching {
        etapa("ler agendamentos") { agendamentos(uid()).get().await() }
            .documents.mapNotNull { d -> d.toObject(Agendamento::class.java)?.also { it.id = d.id } }
            .sortedBy { it.horario }
    }

    // Libera o horário de novo e apaga a marcação (e a cópia da cliente).
    override suspend fun cancelarAgendamento(agendamento: Agendamento): Result<Unit> = runCatching {
        val id = agendamento.id ?: return@runCatching
        val lote = db.get().batch()
        lote.delete(agendamentos(uid()).document(id))
        agendamento.clienteId?.let { lote.delete(copiasDaCliente(it).document(id)) }
        agendamento.agendaId?.let { agendaId ->
            lote.update(agendas(uid()).document(agendaId), "ocupados", FieldValue.arrayRemove(agendamento.horario))
        }
        etapa("cancelar agendamento") { lote.commit().await() }
        Unit
    }

    // Tudo num lote: marca o horário como ocupado, cria a marcação e a cópia
    // da cliente. Se outra cliente pegou o mesmo horário antes, as regras
    // recusam (o documento da marcação já existe).
    override suspend fun reservar(agendamento: Agendamento): Result<Unit> = runCatching {
        val profissionalId = agendamento.profissionalId ?: throw IllegalStateException("Profissional não informada.")
        val agendaId = agendamento.agendaId ?: throw IllegalStateException("Agenda não informada.")
        val chave = agendamento.horario ?: throw IllegalStateException("Horário não informado.")
        val cliente = uid()
        val id = "${agendaId}_$chave"
        val dados = agendamento.copy(clienteId = cliente)
        val lote = db.get().batch()
        lote.update(
            agendas(profissionalId).document(agendaId),
            mapOf("ocupados" to FieldValue.arrayUnion(chave), "ultimoReservado" to chave)
        )
        lote.set(agendamentos(profissionalId).document(id), dados)
        lote.set(copiasDaCliente(cliente).document(id), dados)
        try {
            etapa("reservar horário") { lote.commit().await() }
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) throw HorarioOcupadoException()
            throw e
        }
        Unit
    }

    override suspend fun horariosDaCliente(): Result<List<Agendamento>> = runCatching {
        etapa("ler meus horários") { copiasDaCliente(uid()).get().await() }
            .documents.mapNotNull { d -> d.toObject(Agendamento::class.java)?.also { it.id = d.id } }
            .sortedBy { it.horario }
    }

    // A cliente desmarca: apaga a marcação (e a cópia dela) e devolve o
    // horário pra agenda, livre pra outra pessoa. Se a profissional já
    // excluiu a agenda, só apaga as marcações.
    override suspend fun cancelarMeuHorario(agendamento: Agendamento): Result<Unit> = runCatching {
        val id = agendamento.id ?: return@runCatching
        val profissionalId = agendamento.profissionalId ?: throw IllegalStateException("Profissional não informada.")
        val chave = agendamento.horario ?: throw IllegalStateException("Horário não informado.")
        val agendaRef = agendamento.agendaId?.let { agendas(profissionalId).document(it) }
        val agendaExiste = agendaRef?.let { etapa("ler agenda") { it.get().await() }.exists() } ?: false
        val lote = db.get().batch()
        lote.delete(agendamentos(profissionalId).document(id))
        lote.delete(copiasDaCliente(uid()).document(id))
        if (agendaExiste && agendaRef != null) {
            lote.update(agendaRef, mapOf("ocupados" to FieldValue.arrayRemove(chave), "ultimoLiberado" to chave))
        }
        etapa("desmarcar horário") { lote.commit().await() }
        Unit
    }
}
