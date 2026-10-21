package com.cjstudio.manicure.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Categorias de serviço que a profissional pode oferecer (e ter agenda).
// A chave vai pro banco; o nome é o que aparece na tela.
object Categorias {
    val TODAS = listOf(
        "manicure" to "Manicure",
        "pedicure" to "Pedicure",
        "unhas_gel" to "Unhas em gel / alongamento",
        "nail_art" to "Nail art",
        "podologia" to "Podologia",
        "cabeleireira" to "Cabeleireira",
        "escova" to "Escova e penteado",
        "coloracao" to "Coloração / mechas",
        "barbearia" to "Barbearia",
        "depilacao" to "Depilação",
        "sobrancelhas" to "Design de sobrancelhas",
        "cilios" to "Extensão de cílios",
        "maquiagem" to "Maquiagem",
        "estetica_facial" to "Limpeza de pele / estética facial",
        "massagem" to "Massagem"
    )

    fun nome(chave: String?): String = TODAS.firstOrNull { it.first == chave }?.second ?: chave.orEmpty()
}

// Agenda mensal de uma categoria — profissionais/{uid}/agendas/{id}.
// Os horários livres são: cada dia do mês cujo dia da semana está em
// diasSemana × cada horário de "horarios", menos os "ocupados". Os ocupados
// guardam só a chave do horário (sem nome de cliente): a cliente vê o que
// está livre, mas não quem marcou.
data class Agenda(
    @get:Exclude @set:Exclude
    var id: String? = null,

    var categoria: String? = null,
    // "2026-11"
    var mes: String? = null,
    // Quem atende nesta agenda (a própria profissional ou alguém da equipe).
    var atendente: String? = null,
    // Calendar.SUNDAY (1) .. Calendar.SATURDAY (7).
    var diasSemana: List<Int> = emptyList(),
    // "09:00", "10:00"...
    var horarios: List<String> = emptyList(),
    // Chaves "20261105-0900" já reservadas.
    var ocupados: List<String> = emptyList(),
    // Última chave reservada por uma cliente (as regras conferem a reserva).
    var ultimoReservado: String? = null,

    @ServerTimestamp
    var criadoEm: Date? = null
)

// Horário marcado. Fica em profissionais/{uid}/agendamentos/{agendaId_chave}
// (a profissional vê todos; a cliente só o dela) e uma cópia em
// clientes/{uid}/agendamentos pra cliente ver os próprios horários.
data class Agendamento(
    @get:Exclude @set:Exclude
    var id: String? = null,

    var agendaId: String? = null,
    var profissionalId: String? = null,
    var profissionalNome: String? = null,
    var categoria: String? = null,
    var atendente: String? = null,
    // Chave "20261105-0900".
    var horario: String? = null,
    var clienteId: String? = null,
    var clienteNome: String? = null,
    var clienteTelefone: String? = null,

    @ServerTimestamp
    var criadoEm: Date? = null
)
