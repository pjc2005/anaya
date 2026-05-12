package com.anaya.app.di;

import com.anaya.app.data.local.AppDatabase;
import com.anaya.app.data.local.dao.AccountDao;
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
public final class AppModule_Companion_ProvideAccountDaoFactory implements Factory<AccountDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_Companion_ProvideAccountDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public AccountDao get() {
    return provideAccountDao(dbProvider.get());
  }

  public static AppModule_Companion_ProvideAccountDaoFactory create(
      Provider<AppDatabase> dbProvider) {
    return new AppModule_Companion_ProvideAccountDaoFactory(dbProvider);
  }

  public static AccountDao provideAccountDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.Companion.provideAccountDao(db));
  }
}
