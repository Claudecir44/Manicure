package com.cjstudio.manicure.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// Android 13+ só mostra notificação (mensagem nova, horário marcado) com
// esta permissão. Pedida ao abrir a tela inicial de cada papel.
object PermissaoNotificacao {
    private const val CODIGO = 7001

    fun pedir(activity: AppCompatActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permissao = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(activity, permissao) == PackageManager.PERMISSION_GRANTED) return
        ActivityCompat.requestPermissions(activity, arrayOf(permissao), CODIGO)
    }
}
