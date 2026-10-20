package com.cjstudio.manicure.ui.common

import android.app.Activity
import android.content.Intent
import com.cjstudio.manicure.ui.admin.LoginAdminActivity
import com.cjstudio.manicure.ui.admin.PainelAdminActivity
import com.cjstudio.manicure.ui.auth.EscolhaPerfilActivity
import com.cjstudio.manicure.ui.cliente.HomeClienteActivity
import com.cjstudio.manicure.ui.profissional.PainelProfissionalActivity
import com.cjstudio.manicure.util.AppTipo
import com.cjstudio.manicure.util.Papel

// Telas de entrada de cada app/papel num lugar só.
object Navegacao {

    fun telaLogin(): Class<out Activity> =
        if (AppTipo.ehAdmin) LoginAdminActivity::class.java else EscolhaPerfilActivity::class.java

    fun telaInicial(papel: Papel?): Class<out Activity> = when {
        AppTipo.ehAdmin -> PainelAdminActivity::class.java
        papel == Papel.PROFISSIONAL -> PainelProfissionalActivity::class.java
        papel == Papel.CLIENTE -> HomeClienteActivity::class.java
        else -> EscolhaPerfilActivity::class.java
    }

    // Abre a tela e limpa a pilha (depois do login/logout não tem "voltar").
    fun irLimpando(origem: Activity, destino: Class<out Activity>) {
        origem.startActivity(Intent(origem, destino).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        origem.finish()
    }
}
