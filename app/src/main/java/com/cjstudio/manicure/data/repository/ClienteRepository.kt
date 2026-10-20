package com.cjstudio.manicure.data.repository

import com.cjstudio.manicure.data.model.Cliente
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

// Cliente das manicures. Só ela mesma e os admins leem o cadastro.
interface IClienteRepository {
    suspend fun cadastrar(nome: String, email: String, telefone: String, senha: String?): Result<ResultadoCadastro>
    suspend fun buscarLogada(): Result<Cliente?>

    // ===== Admin =====
    suspend fun listarTodas(): Result<List<Cliente>>
    suspend fun definirBloqueio(uid: String, bloqueado: Boolean): Result<Unit>
}

@Singleton
class ClienteRepository @Inject constructor(
    private val db: Provider<FirebaseFirestore>,
    private val auth: IAuthRepository
) : IClienteRepository {

    private fun colecao() = db.get().collection("clientes")

    override suspend fun cadastrar(nome: String, email: String, telefone: String, senha: String?): Result<ResultadoCadastro> {
        var conta: ContaCadastro? = null
        return try {
            conta = FluxoCadastro.obterConta(auth, email, senha)
            val ref = colecao().document(conta.uid)
            FluxoCadastro.garantirSemPerfil(ref)
            etapa("gravar cadastro") {
                ref.set(Cliente(nome = nome.trim(), email = auth.emailLogado(), telefone = telefone)).await()
            }
            Result.success(FluxoCadastro.concluir(auth, conta))
        } catch (e: Exception) {
            FluxoCadastro.desfazer(auth, conta, veioDoCadastro = senha != null)
            Result.failure(e)
        }
    }

    override suspend fun buscarLogada(): Result<Cliente?> = runCatching {
        val uid = auth.uidLogado() ?: throw IllegalStateException("Não há sessão ativa.")
        val doc = etapa("ler cadastro") { colecao().document(uid).get().await() }
        doc.toObject(Cliente::class.java)?.also { it.id = doc.id }
    }

    override suspend fun listarTodas(): Result<List<Cliente>> = runCatching {
        colecao().orderBy("criadoEm", Query.Direction.DESCENDING).get().await()
            .documents.mapNotNull { doc -> doc.toObject(Cliente::class.java)?.also { it.id = doc.id } }
    }

    override suspend fun definirBloqueio(uid: String, bloqueado: Boolean): Result<Unit> = runCatching {
        colecao().document(uid).update("bloqueado", bloqueado).await()
        Unit
    }
}
