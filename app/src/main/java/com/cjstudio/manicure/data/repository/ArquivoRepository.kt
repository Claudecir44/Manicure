package com.cjstudio.manicure.data.repository

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ArquivoRepository @Inject constructor(
    private val storage: Provider<FirebaseStorage>
) : IArquivoRepository {

    override suspend fun enviarImagem(pasta: String, uri: Uri): Result<String> =
        enviarImagemComCaminho(pasta, uri).map { it.first }

    override suspend fun apagar(caminho: String): Result<Unit> = runCatching {
        storage.get().reference.child(caminho).delete().await()
        Unit
    }

    override suspend fun enviarImagemComCaminho(pasta: String, uri: Uri): Result<Pair<String, String>> = runCatching {
        val ref = storage.get().reference.child("$pasta/${UUID.randomUUID()}.jpg")
        // storage.rules só aceita image/* — o content type precisa ir explícito
        // (arquivo temporário local não tem tipo).
        val metadados = StorageMetadata.Builder().setContentType("image/jpeg").build()
        etapa("enviar foto ($pasta)", limiteMs = 90_000) {
            ref.putFile(uri, metadados).await()
            ref.downloadUrl.await().toString() to ref.path
        }
    }
}
