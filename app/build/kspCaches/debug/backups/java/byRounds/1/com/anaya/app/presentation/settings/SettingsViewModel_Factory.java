package com.anaya.app.presentation.settings;

import com.anaya.app.domain.repository.AccountRepository;
import com.anaya.app.domain.repository.CategoryRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<CategoryRepository> categoryRepositoryProvider;

  private final Provider<AccountRepository> accountRepositoryProvider;

  public SettingsViewModel_Factory(Provider<CategoryRepository> categoryRepositoryProvider,
      Provider<AccountRepository> accountRepositoryProvider) {
    this.categoryRepositoryProvider = categoryRepositoryProvider;
    this.accountRepositoryProvider = accountRepositoryProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(categoryRepositoryProvider.get(), accountRepositoryProvider.get());
  }

  public static SettingsViewModel_Factory create(
      Provider<CategoryRepository> categoryRepositoryProvider,
      Provider<AccountRepository> accountRepositoryProvider) {
    return new SettingsViewModel_Factory(categoryRepositoryProvider, accountRepositoryProvider);
  }

  public static SettingsViewModel newInstance(CategoryRepository categoryRepository,
      AccountRepository accountRepository) {
    return new SettingsViewModel(categoryRepository, accountRepository);
  }
}
