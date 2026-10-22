package com.cjstudio.manicure.ui.chat

import android.content.Context
import com.cjstudio.manicure.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// "Apagar conversa": só para mim ou para todos (com confirmação extra), igual
// no chat aberto e ao segurar a conversa na lista.
object ApagarConversa {

    fun perguntar(context: Context, nomeOutro: String, aoEscolher: (paraTodos: Boolean) -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.chat_apagar_conversa)
            .setMessage(context.getString(R.string.chat_apagar_conversa_msg, nomeOutro))
            .setPositiveButton(R.string.chat_apagar_conversa_mim) { _, _ -> aoEscolher(false) }
            .setNegativeButton(R.string.chat_apagar_conversa_todos) { _, _ ->
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.chat_apagar_conversa_todos)
                    .setMessage(context.getString(R.string.chat_apagar_conversa_todos_msg, nomeOutro))
                    .setPositiveButton(R.string.chat_apagar_conversa_todos) { _, _ -> aoEscolher(true) }
                    .setNegativeButton(R.string.cancelar, null)
                    .show()
            }
            .setNeutralButton(R.string.cancelar, null)
            .show()
    }
}
