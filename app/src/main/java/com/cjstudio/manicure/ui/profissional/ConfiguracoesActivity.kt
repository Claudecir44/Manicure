package com.cjstudio.manicure.ui.profissional

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.manicure.BuildConfig
import com.cjstudio.manicure.R
import com.cjstudio.manicure.databinding.ActivityConfiguracoesBinding
import com.cjstudio.manicure.ui.common.TermosActivity
import com.cjstudio.manicure.util.Aparencia
import com.cjstudio.manicure.util.Suporte

// Configurações da profissional (mesmo jeito do Caronas): instruções do app,
// termos e contato com o suporte.
class ConfiguracoesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfiguracoesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Aparencia.aplicar(this)
        super.onCreate(savedInstanceState)
        binding = ActivityConfiguracoesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topo.tvTitulo.setText(R.string.config_titulo)
        binding.topo.btnVoltar.setOnClickListener { finish() }
        binding.btnInstrucoes.setOnClickListener { startActivity(Intent(this, InstrucoesActivity::class.java)) }
        binding.btnTermos.setOnClickListener { startActivity(Intent(this, TermosActivity::class.java)) }
        binding.btnAjuda.setOnClickListener { Suporte.abrirEmail(this, getString(R.string.config_ajuda_assunto)) }
        binding.tvVersao.text = getString(R.string.config_versao, BuildConfig.VERSION_NAME)
    }
}
