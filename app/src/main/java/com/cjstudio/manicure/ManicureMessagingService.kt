package com.cjstudio.manicure

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cjstudio.manicure.data.repository.INotificacaoRepository
import com.cjstudio.manicure.ui.agenda.AgendaProfissionalActivity
import com.cjstudio.manicure.ui.chat.ChatActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

// Mesmo padrão do Caronas: o servidor manda só "data" e quem monta a
// notificação é este serviço, pelo campo "tipo" (functions/index.js):
//  - mensagem: abre direto o chat daquela conversa;
//  - horarioMarcado: abre a agenda da profissional.
// Canal com som, vibração e badge no ícone do app.
@AndroidEntryPoint
class ManicureMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificacoes: INotificacaoRepository

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        escopo.launch { notificacoes.registrarToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val dados = message.data
        val corpo = dados["corpo"] ?: return
        val idNotificacao = (dados["conversaId"] ?: dados["id"])?.hashCode() ?: System.currentTimeMillis().toInt()

        when (dados["tipo"]) {
            "mensagem" -> {
                val conversaId = dados["conversaId"] ?: return
                // Uma conversa aberta na tela não gera notificação.
                if (ChatActivity.conversaAberta == conversaId) return
                mostrar(
                    canal = "mensagens", nomeCanal = getString(R.string.notificacao_canal_mensagens),
                    titulo = dados["titulo"] ?: getString(R.string.notificacao_titulo_mensagem), corpo = corpo, id = idNotificacao,
                    intent = Intent(this, ChatActivity::class.java)
                        .putExtra(ChatActivity.EXTRA_CONVERSA, conversaId)
                        .putExtra(ChatActivity.EXTRA_COMO_PROFISSIONAL, dados["paraProfissional"] == "true")
                )
            }
            "horarioMarcado" -> mostrar(
                canal = "agenda", nomeCanal = getString(R.string.notificacao_canal_agenda),
                titulo = getString(R.string.notificacao_titulo_horario), corpo = corpo, id = idNotificacao,
                intent = Intent(this, AgendaProfissionalActivity::class.java)
            )
        }
    }

    private fun mostrar(canal: String, nomeCanal: String, titulo: String, corpo: String, id: Int, intent: Intent) {
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pendente = PendingIntent.getActivity(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val som = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(canal, nomeCanal, NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(som, AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    enableVibration(true)
                    setShowBadge(true)
                }
            )
        }
        val notificacao = NotificationCompat.Builder(this, canal)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(ContextCompat.getColor(this, R.color.rosa))
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(corpo))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setNumber(1)
            .setContentIntent(pendente)
            .setSound(som)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()
        manager.notify(id, notificacao)
    }
}
