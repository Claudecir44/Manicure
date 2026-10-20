package com.cjstudio.manicure.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.manicure.databinding.ActivityEscolhaPerfilBinding
import com.cjstudio.manicure.util.Papel

// Primeira tela do app: "Sou profissional" ou "Sou cliente".
class EscolhaPerfilActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityEscolhaPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.cardProfissional.setOnClickListener { abrir(Papel.PROFISSIONAL) }
        binding.cardCliente.setOnClickListener { abrir(Papel.CLIENTE) }
    }

    private fun abrir(papel: Papel) {
        startActivity(Intent(this, LoginActivity::class.java).putExtra(LoginViewModel.EXTRA_PAPEL, papel.name))
    }
}
