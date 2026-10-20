package com.cjstudio.manicure.util

import com.cjstudio.manicure.BuildConfig

// Qual dos dois apps está rodando (flavor) — único lugar que lê BuildConfig.TIPO.
// No app "manicure" a pessoa ainda escolhe se é profissional ou cliente
// (ver Papel).
object AppTipo {
    const val MANICURE = "manicure"
    const val ADMIN = "admin"

    val atual: String = BuildConfig.TIPO

    val ehAdmin get() = atual == ADMIN
}

// Papel de quem usa o app "manicure".
enum class Papel { PROFISSIONAL, CLIENTE }
