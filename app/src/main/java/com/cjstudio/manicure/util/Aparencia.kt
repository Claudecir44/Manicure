package com.cjstudio.manicure.util

import android.app.Activity
import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import com.cjstudio.manicure.R

// Cor das telas da profissional, escolhida no botão "Aparência" do painel.
// Fica salva neste aparelho e vale pra painel, perfil, agenda e chat dela.
// Precisa ser lida na hora (antes do super.onCreate), por isso SharedPreferences
// e não o DataStore.
object Aparencia {

    data class Cor(val chave: String, @StringRes val nome: Int, @StyleRes val tema: Int, @ColorInt val amostra: Int)

    val CORES = listOf(
        Cor("verde", R.string.aparencia_verde, R.style.Theme_Manicure_Profissional, 0xFF11A000.toInt()),
        Cor("rosa", R.string.aparencia_rosa, R.style.Theme_Manicure_Profissional_Rosa, 0xFFE6007E.toInt()),
        Cor("roxo", R.string.aparencia_roxo, R.style.Theme_Manicure_Profissional_Roxo, 0xFF9333EA.toInt()),
        Cor("azul", R.string.aparencia_azul, R.style.Theme_Manicure_Profissional_Azul, 0xFF1A7FE0.toInt()),
        Cor("ciano", R.string.aparencia_ciano, R.style.Theme_Manicure_Profissional_Ciano, 0xFF0097B2.toInt()),
        Cor("dourado", R.string.aparencia_dourado, R.style.Theme_Manicure_Profissional_Dourado, 0xFFC99200.toInt()),
        Cor("laranja", R.string.aparencia_laranja, R.style.Theme_Manicure_Profissional_Laranja, 0xFFEA6A00.toInt()),
        Cor("vermelho", R.string.aparencia_vermelho, R.style.Theme_Manicure_Profissional_Vermelho, 0xFFE11D48.toInt()),
        Cor("prata", R.string.aparencia_prata, R.style.Theme_Manicure_Profissional_Prata, 0xFF8A9099.toInt())
    )

    private const val ARQUIVO = "aparencia"
    private const val CHAVE_COR = "cor"

    fun atual(context: Context): Cor {
        val chave = context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).getString(CHAVE_COR, null)
        return CORES.firstOrNull { it.chave == chave } ?: CORES.first()
    }

    fun salvar(context: Context, cor: Cor) {
        context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit().putString(CHAVE_COR, cor.chave).apply()
    }

    // Chamar antes do super.onCreate nas telas da profissional.
    fun aplicar(activity: Activity) = activity.setTheme(atual(activity).tema)

    // ===== Cor do perfil público (a que as clientes veem) =====
    // Vem do Firestore (Profissional.corPerfil); vazio/null = rosa padrão.

    private const val ARQUIVO_PERFIS = "aparencia_perfis"

    fun corPerfil(chave: String?): Cor? = CORES.firstOrNull { it.chave == chave }

    @StyleRes
    fun temaPerfil(chave: String?): Int = corPerfil(chave)?.tema ?: R.style.Theme_Manicure

    // Última cor vista de cada perfil, pra já abrir na cor certa da próxima vez
    // (o tema precisa ser escolhido antes de o perfil chegar do banco).
    fun corPerfilGuardada(context: Context, profissionalId: String): String? =
        context.getSharedPreferences(ARQUIVO_PERFIS, Context.MODE_PRIVATE).getString(profissionalId, null)

    fun guardarCorPerfil(context: Context, profissionalId: String, chave: String?) {
        context.getSharedPreferences(ARQUIVO_PERFIS, Context.MODE_PRIVATE).edit().putString(profissionalId, chave.orEmpty()).apply()
    }
}
