package com.cjstudio.manicure.data.repository

import android.net.Uri
import com.cjstudio.manicure.data.model.AcessoAdminRestritoException
import com.cjstudio.manicure.data.model.Admin
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val db: Provider<FirebaseFirestore>,
    private val functions: Provider<FirebaseFunctions>,
    private val auth: IAuthRepository,
    private val arquivos: IArquivoRepository
) : IAdminRepository {

    private fun colecao() = db.get().collection(COLECAO)

    override suspend fun entrar(email: String, senha: String): Result<String> = runCatching {
        val uid = auth.entrar(email, senha).getOrThrow()
        if (!etapa("conferir admin") { souAdmin().getOrDefault(false) }) {
            auth.sair()
            throw AcessoAdminRestritoException()
        }
        uid
    }

    override suspend fun souAdmin(): Result<Boolean> = runCatching {
        val uid = auth.uidLogado() ?: return@runCatching false
        colecao().document(uid).get().await().exists()
    }

    override suspend fun buscarLogado(): Result<Admin> = runCatching {
        val uid = auth.uidLogado() ?: throw IllegalStateException("Não há sessão ativa.")
        val doc = colecao().document(uid).get().await()
        doc.toObject(Admin::class.java)?.also { it.id = doc.id } ?: throw AcessoAdminRestritoException()
    }

    override suspend fun listarTodos(): Result<List<Admin>> = runCatching {
        colecao().orderBy("criadoEm", Query.Direction.DESCENDING).get().await()
            .documents.mapNotNull { doc -> doc.toObject(Admin::class.java)?.also { it.id = doc.id } }
    }

    override suspend fun buscar(uid: String): Result<Admin> = runCatching {
        val doc = colecao().document(uid).get().await()
        doc.toObject(Admin::class.java)?.also { it.id = doc.id } ?: throw IllegalStateException("Administrador não encontrado.")
    }

    override suspend fun cadastrarAdmin(
        nome: String,
        sobrenome: String,
        email: String,
        telefone: String,
        cpf: String,
        senha: String,
        role: String,
        permissoes: Map<String, Boolean>,
        senhaAutorizacao: String
    ): Result<String> = runCatching {
        val dados = mapOf(
            "nome" to nome,
            "sobrenome" to sobrenome,
            "email" to email,
            "telefone" to telefone,
            "cpf" to cpf,
            "senha" to senha,
            "role" to role,
            "permissoes" to permissoes,
            "senhaAutorizacao" to senhaAutorizacao
        )
        val resposta = chamar("cadastrarAdmin", dados, "Não foi possível cadastrar o administrador.")
        (resposta as? Map<*, *>)?.get("uid") as? String ?: throw IllegalStateException("Resposta inesperada do servidor.")
    }

    override suspend fun atualizarAdmin(
        uid: String,
        nome: String,
        sobrenome: String,
        telefone: String,
        cpf: String,
        role: String,
        permissoes: Map<String, Boolean>,
        senhaAutorizacao: String
    ): Result<Unit> = runCatching {
        val dados = mapOf(
            "uid" to uid,
            "nome" to nome,
            "sobrenome" to sobrenome,
            "telefone" to telefone,
            "cpf" to cpf,
            "role" to role,
            "permissoes" to permissoes,
            "senhaAutorizacao" to senhaAutorizacao
        )
        chamar("atualizarAdmin", dados, "Não foi possível salvar o administrador.")
        Unit
    }

    override suspend fun excluirAdmin(uid: String, senhaAutorizacao: String): Result<Unit> = runCatching {
        chamar("excluirAdmin", mapOf("uid" to uid, "senhaAutorizacao" to senhaAutorizacao), "Não foi possível excluir o administrador.")
        Unit
    }

    // Repassa a mensagem do servidor ("Senha do administrador master
    // incorreta.") em vez do texto genérico da exceção.
    private suspend fun chamar(funcao: String, dados: Map<String, Any?>, erroPadrao: String): Any? = try {
        functions.get().getHttpsCallable(funcao).call(dados).await().getData()
    } catch (e: FirebaseFunctionsException) {
        throw IllegalStateException(e.message ?: erroPadrao)
    }

    override suspend fun atualizarMinhaFoto(uri: Uri): Result<String> = runCatching {
        val uid = auth.uidLogado() ?: throw IllegalStateException("Não há sessão ativa.")
        val url = arquivos.enviarImagem("fotos_admin/$uid", uri).getOrThrow()
        colecao().document(uid).update("fotoUrl", url).await()
        url
    }

    private companion object {
        const val COLECAO = "admins"
    }
}
