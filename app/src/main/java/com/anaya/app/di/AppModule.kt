package com.anaya.app.di

import android.content.Context
import androidx.room.Room
import com.anaya.app.data.local.AppDatabase
import com.anaya.app.data.local.dao.AccountDao
import com.anaya.app.data.local.dao.BudgetDao
import com.anaya.app.data.local.dao.CategoryDao
import com.anaya.app.data.local.dao.TransactionDao
import com.anaya.app.data.repository.AccountRepositoryImpl
import com.anaya.app.data.repository.BudgetRepositoryImpl
import com.anaya.app.data.repository.CategoryRepositoryImpl
import com.anaya.app.data.repository.TransactionRepositoryImpl
import com.anaya.app.domain.repository.AccountRepository
import com.anaya.app.domain.repository.BudgetRepository
import com.anaya.app.domain.repository.CategoryRepository
import com.anaya.app.domain.repository.TransactionRepository
import com.anaya.app.ml.LocalModelInterface
import com.anaya.app.ml.LocalModelManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindTransactionRepository(
        impl: TransactionRepositoryImpl
    ): TransactionRepository

    @Binds
    abstract fun bindAccountRepository(
        impl: AccountRepositoryImpl
    ): AccountRepository

    @Binds
    abstract fun bindCategoryRepository(
        impl: CategoryRepositoryImpl
    ): CategoryRepository

    @Binds
    abstract fun bindBudgetRepository(
        impl: BudgetRepositoryImpl
    ): BudgetRepository

    @Binds
    abstract fun bindLocalModel(
        impl: LocalModelManager
    ): LocalModelInterface

    companion object {

        @Provides
        @Singleton
        fun provideDatabase(
            @ApplicationContext context: Context
        ): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "anaya_database"
            ).fallbackToDestructiveMigration().build()
        }

        @Provides
        fun provideTransactionDao(db: AppDatabase): TransactionDao {
            return db.transactionDao()
        }

        @Provides
        fun provideAccountDao(db: AppDatabase): AccountDao {
            return db.accountDao()
        }

        @Provides
        fun provideCategoryDao(db: AppDatabase): CategoryDao {
            return db.categoryDao()
        }

        @Provides
        fun provideBudgetDao(db: AppDatabase): BudgetDao {
            return db.budgetDao()
        }
    }
}
