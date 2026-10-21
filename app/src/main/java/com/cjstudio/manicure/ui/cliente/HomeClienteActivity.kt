package com.cjstudio.manicure.ui.cliente

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cjstudio.manicure.R
import com.cjstudio.manicure.data.model.Profissional
import com.cjstudio.manicure.databinding.ActivityHomeClienteBinding
import com.cjstudio.manicure.databinding.ItemProfissionalBinding
import com.cjstudio.manicure.ui.common.Navegacao
import com.cjstudio.manicure.ui.common.coletar
import com.cjstudio.manicure.ui.common.mostrarMensagem
import com.cjstudio.manicure.ui.perfil.PerfilProfissionalActivity
import com.cjstudio.manicure.ui.profissional.ResumoPerfil
import com.cjstudio.manicure.util.Distancia
import com.cjstudio.manicure.util.Formato
import com.cjstudio.manicure.util.carregarFotoRedonda
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeClienteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeClienteBinding
    private val viewModel: HomeClienteViewModel by viewModels()
    private val adapter = ProfissionaisAdapter { viewModel.tocou(it) }

    // Com a localização, as profissionais mais perto vão pro topo. Negada,
    // a lista continua funcionando (ordem por nome).
    private val pedirLocalizacao = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) viewModel.localizacaoLiberada()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeClienteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvProfissionais.layoutManager = LinearLayoutManager(this)
        binding.rvProfissionais.adapter = adapter
        binding.btnSair.setOnClickListener { viewModel.sair() }
        binding.etBusca.doAfterTextChanged { viewModel.digitar(it.toString()) }
        binding.tvCodigo.setOnClickListener { pedirCodigo() }
        binding.chat.btnChat.setOnClickListener { startActivity(Intent(this, com.cjstudio.manicure.ui.chat.ConversasActivity::class.java)) }
        coletar(viewModel.naoLidas) { n ->
            binding.chat.tvBadgeChat.visibility = if (n.comoCliente > 0) View.VISIBLE else View.GONE
            binding.chat.tvBadgeChat.text = if (n.comoCliente > 99) "99+" else n.comoCliente.toString()
            com.cjstudio.manicure.util.BadgeIcone.atualizar(this, n.total)
        }
        com.cjstudio.manicure.ui.common.PermissaoNotificacao.pedir(this)
        binding.tvMeusHorarios.setOnClickListener { startActivity(Intent(this, com.cjstudio.manicure.ui.agenda.MeusHorariosActivity::class.java)) }

        pedirLocalizacao.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

        coletar(viewModel.cliente) { c ->
            binding.tvOla.text = getString(R.string.home_ola, c?.nome.orEmpty().substringBefore(" "))
        }
        coletar(viewModel.lista) { lista ->
            adapter.lista = lista
            binding.tvVazio.visibility = if (lista.isEmpty() && !viewModel.carregando.value) View.VISIBLE else View.GONE
        }
        coletar(viewModel.carregando) { binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE }
        coletar(viewModel.eventos) { evento ->
            when (evento) {
                HomeClienteViewModel.Evento.Saiu -> Navegacao.irLimpando(this, Navegacao.telaLogin())
                is HomeClienteViewModel.Evento.AbrirPerfil -> abrirPerfil(evento.id)
                is HomeClienteViewModel.Evento.PerguntarPreferido -> perguntarPreferido(evento.id, evento.nome)
                is HomeClienteViewModel.Evento.Mensagem -> mostrarMensagem(evento.texto)
            }
        }
    }

    private fun abrirPerfil(id: String) {
        startActivity(Intent(this, PerfilProfissionalActivity::class.java).putExtra(PerfilProfissionalActivity.EXTRA_ID, id))
    }

    // Ao tocar numa profissional: caixa "abrir sempre este perfil ao entrar".
    private fun perguntarPreferido(id: String, nome: String) {
        val margem = (20 * resources.displayMetrics.density).toInt()
        val caixa = CheckBox(this).apply {
            setText(R.string.cliente_preferido_check)
            buttonTintList = androidx.core.content.ContextCompat.getColorStateList(context, R.color.rosa)
        }
        val container = android.widget.FrameLayout(this).apply {
            setPadding(margem, margem / 2, margem, 0)
            addView(caixa)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(nome)
            .setMessage(R.string.cliente_preferido_msg)
            .setView(container)
            .setPositiveButton(R.string.cliente_ver_perfil) { _, _ -> viewModel.abrir(id, caixa.isChecked) }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun pedirCodigo() {
        val campo = EditText(this).apply {
            setHint(R.string.cliente_codigo_hint)
            filters = arrayOf(android.text.InputFilter.AllCaps(), android.text.InputFilter.LengthFilter(10))
        }
        val margem = (20 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(margem, margem / 2, margem, 0)
            addView(campo)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cliente_codigo_titulo)
            .setView(container)
            .setPositiveButton(R.string.buscar) { _, _ -> viewModel.buscarCodigo(campo.text.toString()) }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private class ProfissionaisAdapter(private val aoTocar: (Profissional) -> Unit) :
        RecyclerView.Adapter<ProfissionaisAdapter.Holder>() {

        var lista: List<HomeClienteViewModel.ItemLista> = emptyList()
            set(valor) {
                field = valor
                @Suppress("NotifyDataSetChanged")
                notifyDataSetChanged()
            }

        class Holder(val binding: ItemProfissionalBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemProfissionalBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = lista.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = lista[position]
            val p = item.profissional
            val b = holder.binding
            val contexto = b.root.context
            b.ivFoto.carregarFotoRedonda(p.fotoPerfilUrl)
            b.tvNome.text = p.nomeExibicao
            b.tvLocal.text = listOfNotNull(
                item.distanciaKm?.let { contexto.getString(R.string.cliente_distancia, Distancia.texto(it)) },
                ResumoPerfil.local(p).ifBlank { null }
            ).joinToString("  ·  ")
            val menor = p.servicos.minOfOrNull { it.preco }
            b.tvResumo.text = listOfNotNull(
                contexto.getString(R.string.cliente_n_servicos, p.servicos.size),
                menor?.let { contexto.getString(R.string.cliente_a_partir, Formato.valor(it)) },
                contexto.getString(R.string.cliente_n_fotos, p.fotos.size)
            ).joinToString("  ·  ")
            b.root.setOnClickListener { aoTocar(p) }
        }
    }
}
