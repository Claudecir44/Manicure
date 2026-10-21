package com.cjstudio.manicure.data.repository

import android.net.Uri
import com.cjstudio.manicure.data.model.Cliente
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

// Cliente das manicures. Só ela mesma e os admins leem o cadastro.
interface IClienteRepository {
    // A foto é obrigatória: sobe junto, antes de o cadastro ser gravado.
    suspend fun cadastrar(nome: String, email: String, telefone: String, senha: String?, foto: Uri): Result<ResultadoCadastro>
    suspend fun buscarLogada(): Result<Cliente?>

    // Editar perfil (nome, telefone e foto). E-mail não muda (é o login).
    suspend fun salvarPerfil(nome: String, telefone: String): Result<Unit>
    suspend fun trocarFoto(uri: Uri): Result<String>

    // ===== Admin =====
    suspend fun listarTodas(): Result<List<Cliente>>
    suspend fun definirBloqueio(uid: String, bloqueado: Boolean): Result<Unit>
}

@Singleton
class ClienteRepository @Inject constructor(
    private val db: Provider<FirebaseFirestore>,
    private val auth: IAuthRepository,
    private val arquivos: IArquivoRepository
) : IClienteRepository {

    private fun colecao() = db.get().collection("clientes")

    private fun uid() = auth.uidLogado() ?: throw IllegalStateException("Não há sessão ativa.")

    override suspend fun cadastrar(nome: String, email: String, telefone: String, senha: String?, foto: Uri): Result<ResultadoCadastro> {
        var conta: ContaCadastro? = null
        return try {
            conta = FluxoCadastro.obterConta(auth, email, senha)
            val ref = colecao().document(conta.uid)
            FluxoCadastro.garantirSemPerfil(ref)
            // Com a conta já logada (as regras do Storage exigem), antes de
            // FluxoCadastro.concluir encerrar a sessão pra confirmar o e-mail.
            val fotoUrl = etapa("enviar foto") { arquivos.enviarImagem("perfis/${conta.uid}", foto).getOrThrow() }
            etapa("gravar cadastro") {
                ref.set(Cliente(nome = nome.trim(), email = auth.emailLogado(), telefone = telefone, fotoUrl = fotoUrl)).await()
            }
            Result.success(FluxoCadastro.concluir(auth, conta))
        } catch (e: Exception) {
            FluxoCadastro.desfazer(auth, conta, veioDoCadastro = senha != null)
            Result.failure(e)
        }
    }

    override suspend fun buscarLogada(): Result<Cliente?> = runCatching {
        val doc = etapa("ler cadastro") { colecao().document(uid()).get().await() }
        doc.toObject(Cliente::class.java)?.also { it.id = doc.id }
    }

    override suspend fun salvarPerfil(nome: String, telefone: String): Result<Unit> = runCatching {
        etapa("salvar perfil") { colecao().document(uid()).update(mapOf("nome" to nome.trim(), "telefone" to telefone)).await() }
        Unit
    }

    override suspend fun trocarFoto(uri: Uri): Result<String> = runCatching {
        val url = etapa("enviar foto") { arquivos.enviarImagem("perfis/${uid()}", uri).getOrThrow() }
        etapa("salvar foto") { colecao().document(uid()).update("fotoUrl", url).await() }
        url
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
