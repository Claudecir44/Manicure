package com.cjstudio.manicure.data.repository

import android.net.Uri
import com.cjstudio.manicure.data.model.FotoPortfolio
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.model.Servico
import kotlinx.coroutines.flow.Flow

// Perfil da manicure. Cada profissional só altera o PRÓPRIO perfil
// (firestore.rules); a cliente só lê perfis publicados.
interface IProfissionalRepository {
    // Cria a conta (ou usa a que já existe com a mesma senha) e o perfil
    // inicial. senha == null: já entrou pelo login, só falta o perfil.
    suspend fun cadastrar(nome: String, email: String, telefone: String, senha: String?): Result<ResultadoCadastro>

    suspend fun buscar(uid: String): Result<Profissional?>
    suspend fun buscarLogada(): Result<Profissional?>
    fun escutarLogada(): Flow<Profissional?>

    // Apresentação, contatos e localização (campos de texto do perfil).
    suspend fun salvarPerfil(dados: Map<String, Any?>): Result<Unit>
    suspend fun trocarFotoPerfil(uri: Uri): Result<Unit>
    suspend fun salvarServicos(servicos: List<Servico>): Result<Unit>
    suspend fun atualizarPerfilAntigo(): Result<Unit>
    suspend fun salvarCategorias(categorias: List<String>): Result<Unit>

    // Galeria (até Profissional.MAX_FOTOS).
    suspend fun adicionarFoto(uri: Uri): Result<Unit>
    suspend fun removerFoto(foto: FotoPortfolio): Result<Unit>

    // Perfis visíveis pras clientes: publicados (com nome) e não bloqueados. "texto"
    // procura no nome, cidade e bairro (vazio = todas).
    suspend fun listarPublicadas(texto: String): Result<List<Profissional>>
    suspend fun buscarPorCodigo(codigo: String): Result<Profissional?>

    // ===== Admin =====
    suspend fun listarTodas(): Result<List<Profissional>>
    suspend fun definirBloqueio(uid: String, bloqueado: Boolean): Result<Unit>
}
