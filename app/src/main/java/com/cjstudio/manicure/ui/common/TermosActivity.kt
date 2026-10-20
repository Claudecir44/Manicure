package com.cjstudio.manicure.ui.common

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.manicure.databinding.ActivityTermosBinding

// Termos de Uso e Política de Privacidade (texto em strings.xml). O aceite
// em si é o checkbox do cadastro — esta tela só mostra o texto.
class TermosActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityTermosBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.topo.tvTitulo.setText(com.cjstudio.manicure.R.string.termos_titulo)
        binding.topo.btnVoltar.setOnClickListener { finish() }
    }
}
