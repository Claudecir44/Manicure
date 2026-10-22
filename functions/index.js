// Cloud Functions do Manicure (profissional, cliente e admin no mesmo projeto
// Firebase: manicure-8a657): administradores (cadastrar, editar, excluir),
// assinatura da profissional (Google Play),
// exclusão de cadastros e push. O resto (perfil, galeria, agenda, bloqueio)
// é gravação direta, protegida por firestore.rules.
require('dotenv').config();

const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { setGlobalOptions } = require('firebase-functions/v2');
const admin = require('firebase-admin');
// FieldValue pelo módulo próprio: admin.firestore.FieldValue vem
// indefinido nesta versão do firebase-admin (achado no emulador).
const { FieldValue } = require('firebase-admin/firestore');

admin.initializeApp();

// Mesma região usada no app (FirebaseModule.provideFirebaseFunctions).
// Conta de execução: a do SDK Admin do Firebase. A conta padrão do Compute
// nasce SEM papéis neste projeto (a organização do Google Cloud desliga as
// concessões automáticas), e a do Firebase já tem Firestore/Auth/Storage.
setGlobalOptions({
    region: 'southamerica-east1',
    maxInstances: 10,
    serviceAccount: 'firebase-adminsdk-fbsvc@manicure-8a657.iam.gserviceaccount.com',
});

// Senha exigida pra criar administradores — só existe aqui (functions/.env),
// nunca no app.
const ADMIN_MASTER_PASSWORD = process.env.ADMIN_MASTER_PASSWORD || '';

// Precisa bater com Admin.CHAVES_PERMISSOES no app.
const CHAVES_PERMISSOES = ['profissionais', 'clientes', 'administradores'];

// Admin master, identificado pelo CPF (o mesmo do Match/Caronas): acesso
// total sempre, independente do mapa de permissões.
const CPF_ADMIN_MASTER = '56413025034';

function ehMaster(dadosAdmin) {
    return !!dadosAdmin && dadosAdmin.cpf === CPF_ADMIN_MASTER;
}

function sanitizarPermissoes(permissoes) {
    const limpo = {};
    for (const chave of CHAVES_PERMISSOES) {
        limpo[chave] = !!permissoes && permissoes[chave] === true;
    }
    return limpo;
}

function temPermissao(dadosAdmin, chave) {
    if (ehMaster(dadosAdmin)) return true;
    if (!dadosAdmin || !dadosAdmin.permissoes) return true; // sem mapa = acesso total
    return dadosAdmin.permissoes[chave] === true;
}

async function exigirPermissao(uid, chave) {
    const doc = await admin.firestore().collection('admins').doc(uid).get();
    if (!doc.exists) throw new HttpsError('permission-denied', 'Acesso restrito a administradores.');
    if (!temPermissao(doc.data(), chave)) throw new HttpsError('permission-denied', 'Você não tem permissão para esta ação.');
    return doc.data();
}

function cpfValido(cpf) {
    if (!/^\d{11}$/.test(cpf) || /^(\d)\1{10}$/.test(cpf)) return false;
    for (const tamanho of [9, 10]) {
        let soma = 0;
        for (let i = 0; i < tamanho; i++) soma += Number(cpf[i]) * (tamanho + 1 - i);
        if (((soma * 10) % 11) % 10 !== Number(cpf[tamanho])) return false;
    }
    return true;
}

// Chave web do projeto (a mesma que vai dentro do app, não é secreta).
const WEB_API_KEY = process.env.WEB_API_KEY || '';

// Confere a senha de uma conta existente pelo mesmo serviço de login que o
// app usa (Identity Toolkit). O Admin SDK não tem como validar senha.
async function senhaDaContaConfere(email, senha) {
    if (!WEB_API_KEY) return false;
    try {
        const resposta = await fetch(
            // No emulador o login de teste fica no emulador do Auth.
            `${process.env.FIREBASE_AUTH_EMULATOR_HOST ? `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/` : 'https://'}identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${WEB_API_KEY}`,
            {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password: senha, returnSecureToken: false }),
            }
        );
        return resposta.ok;
    } catch (e) {
        return false;
    }
}

async function registrarLog(acao, uidAutor, detalhes) {
    try {
        await admin.firestore().collection('logsAdministracao').add({
            acao,
            uidAutor: uidAutor || null,
            detalhes: detalhes || null,
            criadoEm: FieldValue.serverTimestamp(),
        });
    } catch (e) {
        console.warn('Falha ao registrar log de administração:', e.message);
    }
}

// Cria um administrador/colaborador. Trava dupla, igual ao Caronas:
// 1) senha do admin master sempre;
// 2) a partir do segundo admin, quem chama precisa estar logado como admin
//    com a permissão "administradores" (o primeiro de todos é criado na
//    tela de login, sem sessão, só com a senha master).
// A conta já nasce com e-mail confirmado: quem autorizou foi o master.
exports.cadastrarAdmin = onCall(async (request) => {
    if (!ADMIN_MASTER_PASSWORD) {
        throw new HttpsError('failed-precondition', 'Cadastro de administrador não configurado no servidor.');
    }

    const dados = request.data || {};
    const nome = String(dados.nome || '').trim();
    const sobrenome = String(dados.sobrenome || '').trim();
    const email = String(dados.email || '').trim().toLowerCase();
    const telefone = String(dados.telefone || '').trim();
    const cpf = String(dados.cpf || '').replace(/\D/g, '');
    const senha = String(dados.senha || '');
    const role = dados.role === 'colaborador' ? 'colaborador' : 'admin';
    const permissoes = role === 'colaborador'
        ? sanitizarPermissoes(dados.permissoes)
        : sanitizarPermissoes(Object.fromEntries(CHAVES_PERMISSOES.map((c) => [c, true])));

    if (!nome || !sobrenome || !email || !telefone || !cpf || !senha) {
        throw new HttpsError('invalid-argument', 'Preencha todos os campos.');
    }
    if (!cpfValido(cpf)) throw new HttpsError('invalid-argument', 'CPF inválido.');
    if (senha.length < 6) throw new HttpsError('invalid-argument', 'A senha precisa ter pelo menos 6 caracteres.');
    if (dados.senhaAutorizacao !== ADMIN_MASTER_PASSWORD) {
        throw new HttpsError('permission-denied', 'Senha do administrador master incorreta.');
    }

    const db = admin.firestore();
    const jaExisteAdmin = !(await db.collection('admins').limit(1).get()).empty;
    const uidChamador = request.auth && request.auth.uid;
    if (jaExisteAdmin) {
        if (!uidChamador) {
            throw new HttpsError('unauthenticated', 'Entre como administrador para cadastrar outro administrador.');
        }
        await exigirPermissao(uidChamador, 'administradores');
    }

    const mesmoCpf = await db.collection('admins').where('cpf', '==', cpf).limit(1).get();
    if (!mesmoCpf.empty) throw new HttpsError('already-exists', 'Já existe um administrador com este CPF.');

    let uid;
    try {
        uid = (await admin.auth().createUser({
            email,
            password: senha,
            displayName: `${nome} ${sobrenome}`,
            emailVerified: true,
        })).uid;
    } catch (error) {
        if (error.code !== 'auth/email-already-exists') {
            console.error('Erro ao criar conta de admin:', error);
            throw new HttpsError('internal', 'Erro ao criar a conta: ' + (error.message || error.code));
        }
        // Uma pessoa = uma conta pra tudo: quem já é profissional/cliente
        // pode virar admin com a MESMA conta, desde que digite a senha dela
        // (nunca trocamos a senha de uma conta existente aqui).
        if (!(await senhaDaContaConfere(email, senha))) {
            throw new HttpsError('already-exists',
                'Este e-mail já tem conta no Manicure (profissional ou cliente). Para usar a mesma conta, digite a senha dela.');
        }
        uid = (await admin.auth().getUserByEmail(email)).uid;
        if ((await db.collection('admins').doc(uid).get()).exists) {
            throw new HttpsError('already-exists', 'Esta conta já é administradora.');
        }
        // Quem autorizou foi o master: a conta passa a valer como confirmada.
        await admin.auth().updateUser(uid, { emailVerified: true });
    }

    await db.collection('admins').doc(uid).set({
        nome,
        sobrenome,
        email,
        telefone,
        cpf,
        role,
        permissoes,
        criadoEm: FieldValue.serverTimestamp(),
    });
    await registrarLog('cadastrarAdmin', uidChamador, { uid, email, role });
    return { uid };
});


// ============================================================
// Editar e excluir administradores (mesmo jeito do Caronas): permissão
// "administradores" de quem chama + senha do admin master. O e-mail e a
// senha de login não mudam por aqui.
// ============================================================
async function exigirAdminComSenhaMaster(request, senhaAutorizacao) {
    if (!ADMIN_MASTER_PASSWORD) {
        throw new HttpsError('failed-precondition', 'Administração não configurada no servidor.');
    }
    const uidChamador = request.auth && request.auth.uid;
    if (!uidChamador) throw new HttpsError('unauthenticated', 'Entre como administrador.');
    await exigirPermissao(uidChamador, 'administradores');
    if (senhaAutorizacao !== ADMIN_MASTER_PASSWORD) {
        throw new HttpsError('permission-denied', 'Senha do administrador master incorreta.');
    }
    return uidChamador;
}

exports.atualizarAdmin = onCall(async (request) => {
    const dados = request.data || {};
    const uidChamador = await exigirAdminComSenhaMaster(request, dados.senhaAutorizacao);
    const uid = String(dados.uid || '').trim();
    const nome = String(dados.nome || '').trim();
    const sobrenome = String(dados.sobrenome || '').trim();
    const telefone = String(dados.telefone || '').trim();
    const cpf = String(dados.cpf || '').replace(/\D/g, '');
    const role = dados.role === 'colaborador' ? 'colaborador' : 'admin';

    if (!uid || !nome || !sobrenome || !telefone || !cpf) {
        throw new HttpsError('invalid-argument', 'Preencha todos os campos.');
    }
    if (!cpfValido(cpf)) throw new HttpsError('invalid-argument', 'CPF inválido.');

    const db = admin.firestore();
    const ref = db.collection('admins').doc(uid);
    const doc = await ref.get();
    if (!doc.exists) throw new HttpsError('not-found', 'Administrador não encontrado.');
    const alvo = doc.data();

    // O CPF identifica o master: ninguém tira nem passa esse CPF por edição.
    if (ehMaster(alvo) && cpf !== CPF_ADMIN_MASTER) {
        throw new HttpsError('failed-precondition', 'O CPF do administrador master não pode ser alterado.');
    }
    const mesmoCpf = await db.collection('admins').where('cpf', '==', cpf).get();
    if (mesmoCpf.docs.some((d) => d.id !== uid)) {
        throw new HttpsError('already-exists', 'Já existe um administrador com este CPF.');
    }

    // Master tem sempre acesso total; o papel dele não muda.
    const novoRole = ehMaster(alvo) ? 'admin' : role;
    const permissoes = novoRole === 'colaborador'
        ? sanitizarPermissoes(dados.permissoes)
        : sanitizarPermissoes(Object.fromEntries(CHAVES_PERMISSOES.map((c) => [c, true])));
    if (novoRole === 'colaborador' && !Object.values(permissoes).some(Boolean)) {
        throw new HttpsError('invalid-argument', 'Marque pelo menos uma permissão para o colaborador.');
    }

    await ref.update({ nome, sobrenome, telefone, cpf, role: novoRole, permissoes });
    await admin.auth().updateUser(uid, { displayName: `${nome} ${sobrenome}` }).catch(() => {});
    await registrarLog('atualizarAdmin', uidChamador, { uid, nome: `${nome} ${sobrenome}`, role: novoRole });
    return { ok: true };
});

// Tira o acesso ao painel (apaga admins/{uid}). A conta de login continua
// se a pessoa também for profissional ou cliente; se não for mais nada, a
// conta é apagada junto. O master e o próprio admin logado não se excluem.
exports.excluirAdmin = onCall(async (request) => {
    const dados = request.data || {};
    const uidChamador = await exigirAdminComSenhaMaster(request, dados.senhaAutorizacao);
    const uid = String(dados.uid || '').trim();
    if (!uid) throw new HttpsError('invalid-argument', 'Administrador não informado.');
    if (uid === uidChamador) {
        throw new HttpsError('failed-precondition', 'Você não pode excluir o seu próprio acesso de administrador.');
    }

    const db = admin.firestore();
    const ref = db.collection('admins').doc(uid);
    const doc = await ref.get();
    if (!doc.exists) throw new HttpsError('not-found', 'Administrador não encontrado.');
    const alvo = doc.data();
    if (ehMaster(alvo)) {
        throw new HttpsError('failed-precondition', 'O administrador master não pode ser excluído.');
    }

    await ref.delete();
    if (alvo.fotoUrl) await apagarArquivoPorUrl(alvo.fotoUrl);
    const contaApagada = await apagarContaSemPerfis(db, uid);
    await registrarLog('excluirAdmin', uidChamador, {
        uid,
        nome: [alvo.nome, alvo.sobrenome].filter(Boolean).join(' '),
        contaApagada,
    });
    return { ok: true, contaApagada };
});

// ============================================================
// Exclusão de cadastro (profissional ou cliente). Uma conta pode ter os
// dois cadastros (e ainda ser admin): apaga só o cadastro pedido e tudo
// que é dele; a conta de login só é apagada quando não sobra nenhum.
//   - a própria pessoa: sem uid (o app confirma a senha dela antes);
//   - admin: uid + permissão da seção + senha master OU a senha do próprio
//     admin.
// ============================================================

// "https://firebasestorage.googleapis.com/v0/b/<bucket>/o/<caminho>?alt=..."
// -> apaga <caminho>. Melhor esforço: arquivo que já sumiu não é erro.
async function apagarArquivo(caminho) {
    try {
        await admin.storage().bucket().file(caminho).delete({ ignoreNotFound: true });
    } catch (e) {
        console.warn('Falha ao apagar arquivo:', e.message);
    }
}

async function apagarArquivoPorUrl(url) {
    const m = /\/o\/([^?]+)/.exec(String(url || ''));
    if (m) await apagarArquivo(decodeURIComponent(m[1]));
}

async function apagarEmLotes(db, refs) {
    for (let i = 0; i < refs.length; i += 400) {
        const lote = db.batch();
        refs.slice(i, i + 400).forEach((r) => lote.delete(r));
        await lote.commit();
    }
}

// Conversas do chat em que a pessoa está do lado "campo" (profissionalId ou
// clienteId), com as mensagens.
async function apagarConversas(db, campo, uid) {
    const conversas = await db.collection('conversas').where(campo, '==', uid).get();
    for (const conversa of conversas.docs) {
        const mensagens = await conversa.ref.collection('mensagens').get();
        await apagarEmLotes(db, mensagens.docs.map((d) => d.ref));
        await conversa.ref.delete();
    }
}

async function excluirCadastroProfissional(db, uid) {
    const ref = db.collection('profissionais').doc(uid);
    const perfil = await ref.get();
    // Horários marcados: some também a cópia em "Meus horários" da cliente.
    const agendamentos = await ref.collection('agendamentos').get();
    const refs = [];
    for (const a of agendamentos.docs) {
        refs.push(a.ref);
        const clienteId = a.data().clienteId;
        if (clienteId) refs.push(db.collection('clientes').doc(clienteId).collection('agendamentos').doc(a.id));
    }
    const agendas = await ref.collection('agendas').get();
    refs.push(...agendas.docs.map((d) => d.ref));
    await apagarEmLotes(db, refs);
    await apagarConversas(db, 'profissionalId', uid);
    if (!perfil.exists) return '-';
    const p = perfil.data();
    if (p.fotoPerfilUrl) await apagarArquivoPorUrl(p.fotoPerfilUrl);
    for (const foto of p.fotos || []) {
        if (foto && foto.caminho) await apagarArquivo(foto.caminho);
        else if (foto && foto.url) await apagarArquivoPorUrl(foto.url);
    }
    await ref.delete();
    return p.nomeExibicao || p.nomeResponsavel || p.email || '-';
}

async function excluirCadastroCliente(db, uid) {
    const ref = db.collection('clientes').doc(uid);
    const perfil = await ref.get();
    // Cada horário marcado volta a ficar livre na agenda da profissional.
    const copias = await ref.collection('agendamentos').get();
    for (const copia of copias.docs) {
        const a = copia.data();
        if (!a.profissionalId) continue;
        const profissional = db.collection('profissionais').doc(a.profissionalId);
        await profissional.collection('agendamentos').doc(copia.id).delete();
        if (a.agendaId && a.horario) {
            await profissional.collection('agendas').doc(a.agendaId)
                .update({ ocupados: FieldValue.arrayRemove(a.horario) })
                .catch(() => {}); // agenda já excluída
        }
    }
    await apagarEmLotes(db, copias.docs.map((d) => d.ref));
    await apagarConversas(db, 'clienteId', uid);
    if (!perfil.exists) return '-';
    const c = perfil.data();
    if (c.fotoUrl) await apagarArquivoPorUrl(c.fotoUrl);
    await ref.delete();
    return c.nome || c.email || '-';
}

// Sem cadastro nenhum (profissional, cliente, admin) a conta de login some,
// e o e-mail fica livre pra um cadastro novo.
async function apagarContaSemPerfis(db, uid) {
    const [p, c, a] = await Promise.all([
        db.collection('profissionais').doc(uid).get(),
        db.collection('clientes').doc(uid).get(),
        db.collection('admins').doc(uid).get(),
    ]);
    if (p.exists || c.exists || a.exists) return false;
    await db.collection('dispositivos').doc(uid).delete().catch(() => {});
    try {
        await admin.auth().deleteUser(uid);
    } catch (e) {
        if (e.code !== 'auth/user-not-found') throw e;
    }
    return true;
}

exports.excluirCadastro = onCall(async (request) => {
    const dados = request.data || {};
    const tipo = ['profissional', 'cliente'].includes(dados.tipo) ? dados.tipo : null;
    if (!tipo) throw new HttpsError('invalid-argument', 'Tipo de cadastro inválido.');
    const uidChamador = request.auth && request.auth.uid;
    if (!uidChamador) throw new HttpsError('unauthenticated', 'Entre na sua conta.');

    const uid = String(dados.uid || '').trim() || uidChamador;
    let autorizadoPor = 'proprio';
    if (uid !== uidChamador) {
        const dadosAdmin = await exigirPermissao(uidChamador, tipo === 'profissional' ? 'profissionais' : 'clientes');
        const senha = String(dados.senhaAutorizacao || '');
        if (ADMIN_MASTER_PASSWORD && senha === ADMIN_MASTER_PASSWORD) {
            autorizadoPor = 'senhaMaster';
        } else if (senha && await senhaDaContaConfere(dadosAdmin.email, senha)) {
            autorizadoPor = 'senhaPropria';
        } else {
            throw new HttpsError('permission-denied', 'Senha incorreta.');
        }
    }

    const db = admin.firestore();
    const nome = tipo === 'profissional'
        ? await excluirCadastroProfissional(db, uid)
        : await excluirCadastroCliente(db, uid);
    const contaApagada = await apagarContaSemPerfis(db, uid);
    if (autorizadoPor !== 'proprio') {
        await registrarLog('excluirCadastro', uidChamador, { uid, tipo, nome, autorizadoPor, contaApagada });
    }
    return { ok: true, contaApagada };
});


// ============================================================
// Assinatura da profissional (a cliente não paga). Pagamento pelo Google
// Play com produtos avulsos consumíveis — mesmo desenho do Caronas
// (confirmarCompraGooglePlayMotorista): o app compra, manda o token pra cá,
// conferimos no Google, consumimos e liberamos. Regras no app em
// util/Assinatura.kt (precisam bater com estas):
// - teste grátis de 30 dias a partir da primeira agenda publicada;
// - Básico R$ 200,00 / 30 dias; Plus R$ 500,00 / 90 dias; 3 agendas ao
//   mesmo tempo; R$ 50,00 por agenda a mais até o fim do período pago;
// - o período novo começa quando o atual (pago ou teste) acaba.
// ============================================================
const { GoogleAuth } = require('google-auth-library');

const PACOTE_APP = 'com.cjstudio.manicure';
const DIAS_TESTE = 30;
const DIA_MS = 24 * 60 * 60 * 1000;

const PRODUTOS_MANICURE = {
    manicure_basico_30: { tipo: 'plano', plano: 'basico', dias: 30, valor: 200 },
    manicure_plus_90: { tipo: 'plano', plano: 'plus', dias: 90, valor: 500 },
    manicure_agenda_extra: { tipo: 'extra', valor: 50 },
};

// Conta de serviço com acesso à API do Google Play Developer: a da própria
// função (firebase-adminsdk-fbsvc, convidada no Play Console em Usuários e
// permissões) ou um JSON em GOOGLE_PLAY_SERVICE_ACCOUNT_JSON (functions/.env).
let clientePlay = null;
async function clienteGooglePlay() {
    if (clientePlay) return clientePlay;
    const json = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
    const auth = new GoogleAuth({
        credentials: json ? JSON.parse(json) : undefined,
        scopes: ['https://www.googleapis.com/auth/androidpublisher'],
    });
    clientePlay = await auth.getClient();
    return clientePlay;
}

function urlCompra(productId, token) {
    return `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACOTE_APP}`
        + `/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(token)}`;
}

function millis(valor) {
    if (!valor) return 0;
    if (typeof valor.toMillis === 'function') return valor.toMillis();
    if (valor instanceof Date) return valor.getTime();
    return Number(valor) || 0;
}

// Calcula os campos novos do perfil pra uma compra (sem gravar nada).
function aplicarCompraManicure(perfil, produto, agora) {
    const fimTeste = perfil.testeInicioEm ? millis(perfil.testeInicioEm) + DIAS_TESTE * DIA_MS : 0;
    const pagoAte = millis(perfil.acessoPagoAte);
    if (produto.tipo === 'plano') {
        const inicio = Math.max(agora, pagoAte, fimTeste);
        const fim = inicio + produto.dias * DIA_MS;
        return {
            campos: { acessoPagoAte: new Date(fim), plano: produto.plano },
            inicio,
            fim,
        };
    }
    // Agenda extra: vale até o fim do período pago atual (paga só a diferença).
    const extrasAte = millis(perfil.extrasAte);
    const extrasAtuais = extrasAte > agora ? (perfil.agendasExtras || 0) : 0;
    const fim = pagoAte > agora ? pagoAte : agora + 30 * DIA_MS;
    return {
        campos: { agendasExtras: extrasAtuais + 1, extrasAte: new Date(Math.max(fim, extrasAte)) },
        inicio: agora,
        fim,
    };
}

exports.confirmarCompraManicure = onCall(async (request) => {
    const uid = request.auth && request.auth.uid;
    if (!uid) throw new HttpsError('unauthenticated', 'Entre na sua conta.');
    const { purchaseToken, productId } = request.data || {};
    const produto = PRODUTOS_MANICURE[productId];
    if (!purchaseToken || !produto) throw new HttpsError('invalid-argument', 'Compra inválida.');

    let compra;
    try {
        const cliente = await clienteGooglePlay();
        compra = (await cliente.request({ url: urlCompra(productId, purchaseToken) })).data;
    } catch (e) {
        console.error('Erro ao conferir compra no Google Play:', e.message);
        throw new HttpsError('internal', 'Não foi possível conferir a compra no Google Play. Tente de novo em instantes.');
    }
    // 0 = comprado, 1 = cancelado, 2 = pendente (Pix/boleto).
    if (compra.purchaseState === 2) return { ok: false, pendente: true };
    if (compra.purchaseState !== 0) throw new HttpsError('failed-precondition', 'Compra não concluída.');
    if (compra.obfuscatedExternalAccountId && compra.obfuscatedExternalAccountId !== uid) {
        throw new HttpsError('permission-denied', 'Esta compra pertence a outra conta.');
    }

    const db = admin.firestore();
    // Idempotência: o mesmo token libera uma vez só.
    const travaRef = db.collection('pagamentosProcessados').doc(`gp_${purchaseToken}`);
    try {
        await travaRef.create({ uid, productId, processadoEm: FieldValue.serverTimestamp() });
    } catch (e) {
        if (e.code === 6) return { ok: true, jaProcessado: true }; // ALREADY_EXISTS
        throw e;
    }

    // Consumir: o mesmo produto é comprado de novo a cada período.
    if (compra.consumptionState !== 1) {
        try {
            const cliente = await clienteGooglePlay();
            await cliente.request({ url: urlCompra(productId, purchaseToken) + ':consume', method: 'POST' });
        } catch (e) {
            console.error('Erro ao consumir compra (acesso liberado mesmo assim):', e.message);
        }
    }

    const perfilRef = db.collection('profissionais').doc(uid);
    const resultado = await db.runTransaction(async (tx) => {
        const perfil = (await tx.get(perfilRef)).data() || {};
        const r = aplicarCompraManicure(perfil, produto, Date.now());
        tx.update(perfilRef, r.campos);
        tx.set(db.collection('pagamentos').doc(), {
            uid,
            nome: perfil.nomeExibicao || perfil.email || '',
            produto: productId,
            tipo: produto.tipo,
            plano: produto.plano || null,
            valor: produto.valor,
            inicio: new Date(r.inicio),
            fim: new Date(r.fim),
            googlePlayOrderId: compra.orderId || '',
            criadoEm: FieldValue.serverTimestamp(),
        });
        return r;
    });
    return { ok: true, fim: resultado.fim };
});


// ============================================================
// Notificações (push). Sempre só "data": quem monta a notificação é o
// ManicureMessagingService no app, pelo campo "tipo". Token em
// dispositivos/{uid}.fcmToken (a mesma pessoa pode ser profissional e
// cliente, então o token fica num lugar só).
// ============================================================
const { onDocumentCreated } = require('firebase-functions/v2/firestore');

async function enviarPush(uid, dados) {
    if (!uid) return;
    const db = admin.firestore();
    const doc = await db.collection('dispositivos').doc(uid).get();
    const token = doc.exists ? doc.data().fcmToken : null;
    if (!token) return;
    try {
        await admin.messaging().send({ token, data: dados, android: { priority: 'high' } });
    } catch (error) {
        console.warn('Erro ao enviar push:', error.message);
        if (error.code === 'messaging/registration-token-not-registered') {
            await db.collection('dispositivos').doc(uid).update({ fcmToken: FieldValue.delete() });
        }
    }
}

// Mensagem nova no chat -> avisa quem recebeu (com um trecho da mensagem,
// como nos apps de conversa) e abre direto a conversa ao tocar.
exports.notificarMensagem = onDocumentCreated('conversas/{conversaId}/mensagens/{mensagemId}', async (event) => {
    const dados = event.data?.data();
    if (!dados || !dados.destinatarioId) return;
    const conversa = (await admin.firestore().collection('conversas').doc(event.params.conversaId).get()).data() || {};
    const texto = String(dados.conteudo || '');
    const trecho = texto.length > 120 ? texto.slice(0, 117) + '...' : texto;
    await enviarPush(dados.destinatarioId, {
        tipo: 'mensagem',
        id: event.params.mensagemId,
        conversaId: event.params.conversaId,
        paraProfissional: String(conversa.profissionalId === dados.destinatarioId),
        titulo: dados.remetenteNome || 'Nova mensagem',
        corpo: trecho,
    });
});

// Cliente marcou horário -> avisa a profissional.
exports.notificarHorarioMarcado = onDocumentCreated('profissionais/{profissionalId}/agendamentos/{id}', async (event) => {
    const d = event.data?.data();
    if (!d) return;
    const h = String(d.horario || '');
    // "20261105-0900" -> "05/11 às 09:00"
    const quando = h.length === 13 ? `${h.slice(6, 8)}/${h.slice(4, 6)} às ${h.slice(9, 11)}:${h.slice(11, 13)}` : h;
    await enviarPush(event.params.profissionalId, {
        tipo: 'horarioMarcado',
        id: event.params.id,
        corpo: `${d.clienteNome || 'Uma cliente'} marcou ${quando} (${d.atendente || ''}).`,
    });
});
