package com.cjstudio.manicure.ui.profissional

import android.content.Context
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.data.model.TipoAtendimento

// Textos do perfil usados no painel e no perfil público.
object ResumoPerfil {

    fun local(p: Profissional): String =
        listOf(p.bairro, p.cidade).filter { !it.isNullOrBlank() }.joinToString(" · ")

    fun atendimento(context: Context, p: Profissional): String = context.getString(
        when (p.atendimento) {
            TipoAtendimento.DOMICILIO -> R.string.atendimento_domicilio
            TipoAtendimento.AMBOS -> R.string.atendimento_ambos
            else -> R.string.atendimento_salao
        }
    )

    fun contatos(context: Context, p: Profissional): String = listOfNotNull(
        p.endereco?.takeIf { it.isNotBlank() }?.let { context.getString(R.string.resumo_endereco, it) },
        local(p).takeIf { it.isNotBlank() }?.let { context.getString(R.string.resumo_local, it) },
        context.getString(R.string.resumo_atendimento, atendimento(context, p)),
        p.horarioAtendimento?.takeIf { it.isNotBlank() }?.let { context.getString(R.string.resumo_horario, it) },
        p.whatsapp?.takeIf { it.isNotBlank() }?.let { context.getString(R.string.resumo_whatsapp, it) },
        p.instagram?.takeIf { it.isNotBlank() }?.let { context.getString(R.string.resumo_instagram, it) }
    ).joinToString("\n")
}
