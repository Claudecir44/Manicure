package com.cjstudio.manicure.data.repository

import android.net.Uri
import com.cjstudio.manicure.data.model.LimiteFotosException
import com.cjstudio.manicure.data.model.FotoPortfolio
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.model.Servico
import com.cjstudio.manicure.util.TextoUtil
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ProfissionalRepository @Inject constructor(
    private val db: Provider<FirebaseFirestore>,
    private val auth: IAuthRepository,
    private val arquivos: IArquivoRepository
) : IProfissionalRepository {

    private fun colecao() = db.get().collection(COLECAO)

    private fun uid() = auth.uidLogado() ?: throw IllegalStateException("Não há sessão ativa.")

    override suspend fun cadastrar(nome: String, email: String, telefone: String, senha: String?): Result<ResultadoCadastro> {
        var conta: ContaCadastro? = null
        return try {
            conta = FluxoCadastro.obterConta(auth, email, senha)
            val ref = colecao().document(conta.uid)
            FluxoCadastro.garantirSemPerfil(ref)
            val perfil = Profissional(
                nomeExibicao = nome.trim(),
                nomeResponsavel = nome.trim(),
                email = auth.emailLogado(),
                telefone = telefone,
                whatsapp = telefone,
                codigo = gerarCodigo(nome),
                busca = TextoUtil.normalizar(nome)
            )
            etapa("gravar perfil") { ref.set(perfil).await() }
            Result.success(FluxoCadastro.concluir(auth, conta))
        } catch (e: Exception) {
            FluxoCadastro.desfazer(auth, conta, veioDoCadastro = senha != null)
            Result.failure(e)
        }
    }

    // 3 letras do nome + 3 números ("ANA482"); colisão é improvável e só
    // atrapalha a busca por código (o perfil continua achável pelo nome).
    private fun gerarCodigo(nome: String): String {
        val letras = TextoUtil.normalizar(nome).filter { it.isLetter() }.uppercase().take(3).padEnd(3, 'M')
        return letras + (100..999).random()
    }

    override suspend fun buscar(uid: String): Result<Profissional?> = runCatching {
        val doc = etapa("ler perfil") { colecao().document(uid).get().await() }
        doc.toObject(Profissional::class.java)?.also { it.id = doc.id }
    }

    override suspend fun buscarLogada(): Result<Profissional?> {
        val uid = auth.uidLogado() ?: return Result.failure(IllegalStateException("Não há sessão ativa."))
        return buscar(uid)
    }

    override fun escutarLogada(): Flow<Profissional?> = callbackFlow {
        val uid = auth.uidLogado()
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val registro = colecao().document(uid).addSnapshotListener { doc, erro ->
            if (erro != null) {
                close(erro)
                return@addSnapshotListener
            }
            trySend(doc?.toObject(Profissional::class.java)?.also { it.id = doc.id })
        }
        awaitClose { registro.remove() }
    }

    // Recalcula o texto de busca e a publicação (perfil com nome, cidade e
    // pelo menos um serviço aparece pras clientes).
    override suspend fun salvarPerfil(dados: Map<String, Any?>): Result<Unit> = runCatching {
        val atual = buscarLogada().getOrThrow() ?: throw IllegalStateException("Perfil não encontrado.")
        val nome = (dados["nomeExibicao"] as? String) ?: atual.nomeExibicao.orEmpty()
        val cidade = (dados["cidade"] as? String) ?: atual.cidade.orEmpty()
        val bairro = (dados["bairro"] as? String) ?: atual.bairro.orEmpty()
        val completo = dados.toMutableMap()
        completo["busca"] = TextoUtil.normalizar("$nome $cidade $bairro")
        completo["publicado"] = podePublicar(nome, cidade, atual.servicos)
        etapa("salvar perfil") { colecao().document(uid()).update(completo).await() }
        Unit
    }

    private fun podePublicar(nome: String, cidade: String, servicos: List<Servico>) =
        nome.isNotBlank() && cidade.isNotBlank() && servicos.isNotEmpty()

    override suspend fun trocarFotoPerfil(uri: Uri): Result<Unit> = runCatching {
        val url = arquivos.enviarImagem("perfis/${uid()}", uri).getOrThrow()
        colecao().document(uid()).update("fotoPerfilUrl", url).await()
        Unit
    }

    override suspend fun salvarServicos(servicos: List<Servico>): Result<Unit> = runCatching {
        val atual = buscarLogada().getOrThrow() ?: throw IllegalStateException("Perfil não encontrado.")
        etapa("salvar serviços") {
            colecao().document(uid()).update(
                mapOf(
                    "servicos" to servicos,
                    "publicado" to podePublicar(atual.nomeExibicao.orEmpty(), atual.cidade.orEmpty(), servicos)
                )
            ).await()
        }
        Unit
    }

    override suspend fun adicionarFoto(uri: Uri): Result<Unit> = runCatching {
        val atual = buscarLogada().getOrThrow() ?: throw IllegalStateException("Perfil não encontrado.")
        if (atual.fotos.size >= Profissional.MAX_FOTOS) throw LimiteFotosException()
        val (url, caminho) = arquivos.enviarImagemComCaminho("portfolio/${uid()}", uri).getOrThrow()
        val foto = FotoPortfolio(url = url, caminho = caminho, enviadaEm = System.currentTimeMillis())
        etapa("salvar foto") { colecao().document(uid()).update("fotos", FieldValue.arrayUnion(foto)).await() }
        Unit
    }

    override suspend fun removerFoto(foto: FotoPortfolio): Result<Unit> = runCatching {
        etapa("remover foto") { colecao().document(uid()).update("fotos", FieldValue.arrayRemove(foto)).await() }
        // Arquivo apagado depois: se falhar, só sobra um arquivo sem uso.
        foto.caminho?.let { arquivos.apagar(it) }
        Unit
    }

    override suspend fun listarPublicadas(texto: String): Result<List<Profissional>> = runCatching {
        val termo = TextoUtil.normalizar(texto)
        etapa("listar profissionais") {
            colecao().whereEqualTo("publicado", true).whereEqualTo("bloqueado", false).get().await()
        }.documents
            .mapNotNull { doc -> doc.toObject(Profissional::class.java)?.also { it.id = doc.id } }
            .filter { termo.isBlank() || it.busca.orEmpty().contains(termo) }
            .sortedBy { it.nomeExibicao.orEmpty().lowercase() }
    }

    override suspend fun buscarPorCodigo(codigo: String): Result<Profissional?> = runCatching {
        etapa("buscar código") {
            colecao().whereEqualTo("codigo", codigo.trim().uppercase())
                .whereEqualTo("publicado", true).whereEqualTo("bloqueado", false).limit(1).get().await()
        }.documents.firstOrNull()?.let { doc -> doc.toObject(Profissional::class.java)?.also { it.id = doc.id } }
    }

    override suspend fun listarTodas(): Result<List<Profissional>> = runCatching {
        colecao().orderBy("criadoEm", Query.Direction.DESCENDING).get().await()
            .documents.mapNotNull { doc -> doc.toObject(Profissional::class.java)?.also { it.id = doc.id } }
    }

    override suspend fun definirBloqueio(uid: String, bloqueado: Boolean): Result<Unit> = runCatching {
        colecao().document(uid).update("bloqueado", bloqueado).await()
        Unit
    }

    private companion object {
        const val COLECAO = "profissionais"
    }
}
