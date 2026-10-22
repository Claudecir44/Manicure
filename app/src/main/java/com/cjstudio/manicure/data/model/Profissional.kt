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
    // Coordenadas do endereço (ou do bairro/cidade), pra ordenar por distância.
    var latitude: Double? = null,
    var longitude: Double? = null,
    // Atende só no salão, em domicílio ou os dois (TipoAtendimento).
    var atendimento: String = TipoAtendimento.SALAO,

    // Chaves de Categorias.TODAS (manicure, pedicure...): cada uma pode ter agenda.
    var categorias: List<String> = emptyList(),

    var servicos: List<Servico> = emptyList(),
    // No máximo MAX_FOTOS (firestore.rules confere o mesmo limite).
    var fotos: List<FotoPortfolio> = emptyList(),

    // Código curto pra divulgar ("Me procure no app: ANA123").
    var codigo: String? = null,
    // Cor do perfil que as clientes veem (chave de util/Aparencia.CORES);
    // vazio = rosa padrão do app.
    var corPerfil: String? = null,
    // Texto em minúsculas sem acento pra busca (nome + cidade + bairro).
    var busca: String? = null,

    // ===== Assinatura (ver util/Assinatura) =====
    // Quando publicou a primeira agenda: começa o teste grátis de 30 dias.
    // A própria profissional grava uma vez só (firestore.rules); o resto só
    // a Cloud Function confirmarCompraManicure grava.
    var testeInicioEm: Date? = null,
    // Fim do período pago (já somados os planos comprados).
    var acessoPagoAte: Date? = null,
    // Último plano pago ("basico" | "plus").
    var plano: String? = null,
    // Agendas compradas além das 3 do plano, valendo até extrasAte.
    var agendasExtras: Int = 0,
    var extrasAte: Date? = null,

    // Aparece pras clientes assim que tem nome; o admin pode bloquear.
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
    // Foto obrigatória no cadastro (perfis/{uid}/ no Storage).
    var fotoUrl: String? = null,
    var bloqueado: Boolean = false,
    @ServerTimestamp
    var criadoEm: Date? = null
)
