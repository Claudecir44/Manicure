package com.cjstudio.manicure.util

import android.content.Context
import android.util.Log
import me.leolin.shortcutbadger.ShortcutBadger

// Número em cima do ícone do app na tela inicial (mensagens não lidas).
// Quem desenha é o launcher de cada fabricante (MIUI, Samsung...); o
// ShortcutBadger fala com cada um. Launcher sem suporte só ignora.
object BadgeIcone {
    fun atualizar(context: Context, total: Int) {
        try {
            if (total > 0) ShortcutBadger.applyCount(context.applicationContext, total)
            else ShortcutBadger.removeCount(context.applicationContext)
        } catch (e: Exception) {
            Log.w("BadgeIcone", "Launcher não suporta badge: ${e.message}")
        }
    }
}
