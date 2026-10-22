package com.cjstudio.manicure.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

// Qual cadastro apagar — a mesma conta pode ser profissional e cliente.
enum class TipoCadastro(val chave: String) { PROFISSIONAL("profissional"), CLIENTE("cliente") }

// Exclusão de cadastro (Cloud Function "excluirCadastro"): apaga o cadastro,
// as agendas/horários marcados, o chat e as fotos dele; a conta de login só
// some quando não sobra nenhum outro cadastro nela.
interface IContaRepository {
    // A própria pessoa: confirma a senha dela antes (celular desbloqueado na
    // mão de outra pessoa não basta) e sai da conta no fim.
    suspend fun excluirMeuCadastro(tipo: TipoCadastro, senha: String): Result<Unit>

    // Admin: senha do master ou a do próprio admin (o servidor confere).
    suspend fun excluirCadastroDe(uid: String, tipo: TipoCadastro, senhaAutorizacao: String): Result<Unit>
}

@Singleton
class ContaRepository @Inject constructor(
    private val firebaseAuth: Provider<FirebaseAuth>,
    private val functions: Provider<FirebaseFunctions>,
    private val auth: IAuthRepository
) : IContaRepository {

    override suspend fun excluirMeuCadastro(tipo: TipoCadastro, senha: String): Result<Unit> = runCatching {
        val usuario = firebaseAuth.get().currentUser ?: throw IllegalStateException("Sessão expirada. Entre de novo.")
        val email = usuario.email ?: throw IllegalStateException("Conta sem e-mail.")
        etapa("confirmar senha") { usuario.reauthenticate(EmailAuthProvider.getCredential(email, senha)).await() }
        chamar(mapOf("tipo" to tipo.chave))
        auth.sair()
    }

    override suspend fun excluirCadastroDe(uid: String, tipo: TipoCadastro, senhaAutorizacao: String): Result<Unit> = runCatching {
        chamar(mapOf("uid" to uid, "tipo" to tipo.chave, "senhaAutorizacao" to senhaAutorizacao))
    }

    private suspend fun chamar(dados: Map<String, Any>) {
        try {
            etapa("excluir cadastro", limiteMs = 60_000) { functions.get().getHttpsCallable("excluirCadastro").call(dados).await() }
        } catch (e: FirebaseFunctionsException) {
            // Mensagem do servidor ("Senha incorreta.") em vez do texto genérico.
            throw IllegalStateException(e.message ?: "Não foi possível excluir o cadastro.")
        }
    }
}
