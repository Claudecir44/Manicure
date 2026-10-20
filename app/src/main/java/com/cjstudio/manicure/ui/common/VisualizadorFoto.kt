package com.cjstudio.manicure.ui.common

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.ImageView
import coil3.load

// Foto da galeria em tela cheia; toque pra fechar.
object VisualizadorFoto {
    fun mostrar(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        val dialogo = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imagem = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            load(url)
            setOnClickListener { dialogo.dismiss() }
        }
        dialogo.setContentView(imagem, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        dialogo.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        dialogo.show()
    }
}
