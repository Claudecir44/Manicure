package com.cjstudio.manicure.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Perfil da manicure — documento profissionais/{uid}. Cada profissional só
// grava o próprio documento (firestore.rules); as clientes leem os perfis
// publicados. Serviços e fotos ficam dentro do próprio perfil (poucos itens,
// uma leitura só pra abrir o perfil inteiro).
data class Profissional(
    @get:Exclude @set:Exclude
    var id: String? = null,

    // Como aparece pras clientes: o próprio nome ou o nome do salão.
    var nomeExibicao: String? = null,
    var tipoPerfil: String = TipoPerfil.PESSOAL,
    var nomeResponsavel: String? = null,
    var email: String? = null,
    var fotoPerfilUrl: String? = null,

    // Apresentação pessoal e descrição do trabalho.
    var apresentacao: String? = null,
    var sobreTrabalho: String? = null,

    var telefone: String? = null,
    var whatsapp: String? = null,
    var instagram: String? = null,
    var cidade: String? = null,
    var bairro: String? = null,
    var endereco: String? = null,
    var horarioAtendimento: String? = null,
    // Atende só no salão, em domicílio ou os dois (TipoAtendimento).
    var atendimento: String = TipoAtendimento.SALAO,

    var servicos: List<Servico> = emptyList(),
    // No máximo MAX_FOTOS (firestore.rules confere o mesmo limite).
    var fotos: List<FotoPortfolio> = emptyList(),

    // Código curto pra divulgar ("Me procure no app: ANA123").
    var codigo: String? = null,
    // Texto em minúsculas sem acento pra busca (nome + cidade + bairro).
    var busca: String? = null,

    // Só aparece pras clientes com o perfil completo e liberado pelo admin.
    var publicado: Boolean = false,
    var bloqueado: Boolean = false,

    @ServerTimestamp
    var criadoEm: Date? = null
) {
    companion object {
        const val MAX_FOTOS = 20
    }
}

object TipoPerfil {
    const val PESSOAL = "pessoal"
    const val SALAO = "salao"
}

object TipoAtendimento {
    const val SALAO = "salao"
    const val DOMICILIO = "domicilio"
    const val AMBOS = "ambos"
}

// Serviço oferecido: "Pé e mão", R$ 50,00, 60 min.
data class Servico(
    var nome: String? = null,
    var preco: Double = 0.0,
    var duracaoMin: Int = 0,
    var descricao: String? = null
)

// Foto da galeria. "caminho" é o arquivo no Storage (pra apagar junto).
data class FotoPortfolio(
    var url: String? = null,
    var caminho: String? = null,
    var legenda: String? = null,
    var enviadaEm: Long = 0
)

// Cliente — documento clientes/{uid}. Só ela e os admins leem.
data class Cliente(
    @get:Exclude @set:Exclude
    var id: String? = null,
    var nome: String? = null,
    var email: String? = null,
    var telefone: String? = null,
    var bloqueado: Boolean = false,
    @ServerTimestamp
    var criadoEm: Date? = null
)
