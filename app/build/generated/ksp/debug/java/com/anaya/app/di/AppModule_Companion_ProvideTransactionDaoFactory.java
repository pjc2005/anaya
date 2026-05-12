package com.anaya.app.di;

import com.anaya.app.data.local.AppDatabase;
import com.anaya.app.data.local.dao.TransactionDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava"
})
public final class AppModule_Companion_ProvideTransactionDaoFactory implements Factory<TransactionDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_Companion_ProvideTransactionDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public TransactionDao get() {
    return provideTransactionDao(dbProvider.get());
  }

  public static AppModule_Companion_ProvideTransactionDaoFactory create(
      Provider<AppDatabase> dbProvider) {
    return new AppModule_Companion_ProvideTransactionDaoFactory(dbProvider);
  }

  public static TransactionDao provideTransactionDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.Companion.provideTransactionDao(db));
  }
}
