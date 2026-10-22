package com.cjstudio.manicure.ui.common

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import androidx.annotation.StringRes
import com.cjstudio.manicure.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// Confirmação de ação destrutiva com senha (excluir cadastro, excluir
// admin). Senha em branco não confirma.
fun Context.pedirSenha(
    titulo: String,
    mensagem: String,
    @StringRes dica: Int,
    @StringRes botao: Int,
    aoConfirmar: (String) -> Unit
) {
    val densidade = resources.displayMetrics.density
    val campo = EditText(this).apply {
        setHint(dica)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
    val moldura = FrameLayout(this).apply {
        val lado = (24 * densidade).toInt()
        setPadding(lado, (8 * densidade).toInt(), lado, 0)
        addView(campo)
    }
    MaterialAlertDialogBuilder(this)
        .setTitle(titulo)
        .setMessage(mensagem)
        .setView(moldura)
        .setPositiveButton(botao) { _, _ ->
            val senha = campo.text.toString()
            if (senha.isNotBlank()) aoConfirmar(senha)
        }
        .setNegativeButton(R.string.cancelar, null)
        .show()
}
