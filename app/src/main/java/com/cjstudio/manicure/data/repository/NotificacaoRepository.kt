package com.cjstudio.manicure.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

// Token de push do aparelho em dispositivos/{uid} — um lugar só, porque a
// mesma pessoa pode ser profissional e cliente. As Cloud Functions leem
// daqui pra avisar de mensagem nova e de horário marcado.
interface INotificacaoRepository {
    suspend fun registrarToken(token: String? = null)
}

@Singleton
class NotificacaoRepository @Inject constructor(
    private val db: Provider<FirebaseFirestore>,
    private val auth: IAuthRepository
) : INotificacaoRepository {

    override suspend fun registrarToken(token: String?) {
        val uid = auth.uidLogado() ?: return
        runCatching {
            val atual = token ?: FirebaseMessaging.getInstance().token.await()
            db.get().collection("dispositivos").document(uid).set(mapOf("fcmToken" to atual), SetOptions.merge()).await()
        }
    }
}
