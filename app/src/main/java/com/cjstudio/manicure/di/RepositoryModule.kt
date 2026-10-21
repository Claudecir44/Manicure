package com.cjstudio.manicure.di

import com.cjstudio.manicure.data.repository.AdminRepository
import com.cjstudio.manicure.data.repository.AgendaRepository
import com.cjstudio.manicure.data.repository.ChatRepository
import com.cjstudio.manicure.data.repository.IChatRepository
import com.cjstudio.manicure.data.repository.INotificacaoRepository
import com.cjstudio.manicure.data.repository.NotificacaoRepository
import com.cjstudio.manicure.data.repository.IAgendaRepository
import com.cjstudio.manicure.data.repository.ArquivoRepository
import com.cjstudio.manicure.data.repository.AuthRepository
import com.cjstudio.manicure.data.repository.ClienteRepository
import com.cjstudio.manicure.data.repository.FalhasRepository
import com.cjstudio.manicure.data.repository.IAdminRepository
import com.cjstudio.manicure.data.repository.IArquivoRepository
import com.cjstudio.manicure.data.repository.IAuthRepository
import com.cjstudio.manicure.data.repository.IClienteRepository
import com.cjstudio.manicure.data.repository.IFalhasRepository
import com.cjstudio.manicure.data.repository.IProfissionalRepository
import com.cjstudio.manicure.data.repository.ILocalizacaoRepository
import com.cjstudio.manicure.data.repository.IPreferenciasRepository
import com.cjstudio.manicure.data.repository.LocalizacaoRepository
import com.cjstudio.manicure.data.repository.PreferenciasRepository
import com.cjstudio.manicure.data.repository.ProfissionalRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepository): IAuthRepository

    @Binds
    @Singleton
    abstract fun bindArquivoRepository(impl: ArquivoRepository): IArquivoRepository

    @Binds
    @Singleton
    abstract fun bindFalhasRepository(impl: FalhasRepository): IFalhasRepository

    @Binds
    @Singleton
    abstract fun bindAdminRepository(impl: AdminRepository): IAdminRepository

    @Binds
    @Singleton
    abstract fun bindProfissionalRepository(impl: ProfissionalRepository): IProfissionalRepository

    @Binds
    @Singleton
    abstract fun bindClienteRepository(impl: ClienteRepository): IClienteRepository

    @Binds
    @Singleton
    abstract fun bindLocalizacaoRepository(impl: LocalizacaoRepository): ILocalizacaoRepository

    @Binds
    @Singleton
    abstract fun bindPreferenciasRepository(impl: PreferenciasRepository): IPreferenciasRepository

    @Binds
    @Singleton
    abstract fun bindAgendaRepository(impl: AgendaRepository): IAgendaRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepository): IChatRepository

    @Binds
    @Singleton
    abstract fun bindNotificacaoRepository(impl: NotificacaoRepository): INotificacaoRepository
}
