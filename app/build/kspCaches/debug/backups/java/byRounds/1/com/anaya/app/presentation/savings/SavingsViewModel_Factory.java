package com.anaya.app.presentation.savings;

import com.anaya.app.domain.repository.CategoryRepository;
import com.anaya.app.domain.repository.TransactionRepository;
import com.anaya.app.ml.LocalModelManager;
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
public final class SavingsViewModel_Factory implements Factory<SavingsViewModel> {
  private final Provider<TransactionRepository> transactionRepositoryProvider;

  private final Provider<CategoryRepository> categoryRepositoryProvider;

  private final Provider<LocalModelManager> localModelProvider;

  public SavingsViewModel_Factory(Provider<TransactionRepository> transactionRepositoryProvider,
      Provider<CategoryRepository> categoryRepositoryProvider,
      Provider<LocalModelManager> localModelProvider) {
    this.transactionRepositoryProvider = transactionRepositoryProvider;
    this.categoryRepositoryProvider = categoryRepositoryProvider;
    this.localModelProvider = localModelProvider;
  }

  @Override
  public SavingsViewModel get() {
    return newInstance(transactionRepositoryProvider.get(), categoryRepositoryProvider.get(), localModelProvider.get());
  }

  public static SavingsViewModel_Factory create(
      Provider<TransactionRepository> transactionRepositoryProvider,
      Provider<CategoryRepository> categoryRepositoryProvider,
      Provider<LocalModelManager> localModelProvider) {
    return new SavingsViewModel_Factory(transactionRepositoryProvider, categoryRepositoryProvider, localModelProvider);
  }

  public static SavingsViewModel newInstance(TransactionRepository transactionRepository,
      CategoryRepository categoryRepository, LocalModelManager localModel) {
    return new SavingsViewModel(transactionRepository, categoryRepository, localModel);
  }
}
