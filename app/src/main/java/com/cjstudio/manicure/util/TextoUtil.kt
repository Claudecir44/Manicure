package com.cjstudio.manicure.util

import java.text.Normalizer
import java.util.Locale

object TextoUtil {
    // Minúsculas, sem acento e sem espaço sobrando — pra busca casar
    // "São José" com "sao jose".
    fun normalizar(texto: String): String =
        Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)
}
