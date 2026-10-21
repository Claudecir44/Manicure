package com.cjstudio.manicure.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.cjstudio.manicure.R

// E-mail pro suporte da empresa (Configurações e botão Suporte do login).
// O destinatário vai com o nome da empresa ("CJ Studio Technology <...>")
// pra não aparecer o nome de quem está no celular.
object Suporte {

    const val EMAIL = "cjstudiotechnology@gmail.com"
    private const val NOME_EMPRESA = "CJ Studio Technology"
    private const val GMAIL = "com.google.android.gm"

    fun abrirEmail(activity: Activity, assunto: String, corpo: String = "") {
        val email = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf("$NOME_EMPRESA <$EMAIL>"))
            .putExtra(Intent.EXTRA_SUBJECT, assunto)
            .putExtra(Intent.EXTRA_TEXT, corpo)
        // Gmail direto quando existe (outros apps, como o do Mercado Pago, também
        // se oferecem pra "mailto" e confundem); se não, o Android pergunta.
        val gmail = Intent(email).setPackage(GMAIL)
        val alvo = if (gmail.resolveActivity(activity.packageManager) != null) gmail else email
        runCatching { activity.startActivity(alvo) }
            .onFailure { android.widget.Toast.makeText(activity, activity.getString(R.string.suporte_sem_app, EMAIL), android.widget.Toast.LENGTH_LONG).show() }
    }
}
