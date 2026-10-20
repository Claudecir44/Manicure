package com.cjstudio.manicure.ui.common

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.manicure.databinding.ActivitySplashBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private val viewModel: SplashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivitySplashBinding.inflate(layoutInflater).root)

        coletar(viewModel.destino.filterNotNull()) { destino ->
            val tela = when (destino) {
                is SplashViewModel.Destino.Inicial -> Navegacao.telaInicial(destino.papel)
                SplashViewModel.Destino.Login -> Navegacao.telaLogin()
            }
            Navegacao.irLimpando(this, tela)
        }
    }
}
