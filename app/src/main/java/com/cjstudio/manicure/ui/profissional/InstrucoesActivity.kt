package com.cjstudio.manicure.ui.profissional

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.manicure.R
import com.cjstudio.manicure.databinding.ActivityInstrucoesBinding
import com.cjstudio.manicure.util.Aparencia

// Instruções de uso para a profissional: um cartão por assunto, com o texto
// de strings_instrucoes.xml (títulos e textos na mesma ordem).
class InstrucoesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInstrucoesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Aparencia.aplicar(this)
        super.onCreate(savedInstanceState)
        binding = ActivityInstrucoesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topo.tvTitulo.setText(R.string.instrucoes_titulo)
        binding.topo.btnVoltar.setOnClickListener { finish() }

        // O estilo dá fundo, cores e espaçamento interno; os layout_* vão à mão
        // (estilo passado no construtor não aplica parâmetros de layout).
        val d = resources.displayMetrics.density
        val largura = LinearLayout.LayoutParams.MATCH_PARENT
        val altura = LinearLayout.LayoutParams.WRAP_CONTENT
        val titulos = resources.getStringArray(R.array.instrucoes_titulos)
        val textos = resources.getStringArray(R.array.instrucoes_textos)
        titulos.zip(textos).forEach { (titulo, texto) ->
            val cartao = LinearLayout(this, null, 0, R.style.CartaoSecao).apply { orientation = LinearLayout.VERTICAL }
            cartao.addView(
                TextView(this, null, 0, R.style.TituloCartao).apply { text = titulo },
                LinearLayout.LayoutParams(largura, altura).apply { bottomMargin = (8 * d).toInt() }
            )
            cartao.addView(TextView(this, null, 0, R.style.TextoCorpo).apply { text = texto }, LinearLayout.LayoutParams(largura, altura))
            binding.listaInstrucoes.addView(cartao, LinearLayout.LayoutParams(largura, altura).apply { topMargin = (14 * d).toInt() })
        }
    }
}
