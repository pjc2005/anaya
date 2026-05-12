package com.anaya.app.presentation.transaction.editor;

import androidx.lifecycle.SavedStateHandle;
import com.anaya.app.domain.repository.AccountRepository;
import com.anaya.app.domain.repository.CategoryRepository;
import com.anaya.app.domain.repository.TransactionRepository;
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
public final class TransactionEditorViewModel_Factory implements Factory<TransactionEditorViewModel> {
  private final Provider<TransactionRepository> transactionRepositoryProvider;

  private final Provider<CategoryRepository> categoryRepositoryProvider;

  private final Provider<AccountRepository> accountRepositoryProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  public TransactionEditorViewModel_Factory(
      Provider<TransactionRepository> transactionRepositoryProvider,
      Provider<CategoryRepository> categoryRepositoryProvider,
      Provider<AccountRepository> accountRepositoryProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.transactionRepositoryProvider = transactionRepositoryProvider;
    this.categoryRepositoryProvider = categoryRepositoryProvider;
    this.accountRepositoryProvider = accountRepositoryProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public TransactionEditorViewModel get() {
    return newInstance(transactionRepositoryProvider.get(), categoryRepositoryProvider.get(), accountRepositoryProvider.get(), savedStateHandleProvider.get());
  }

  public static TransactionEditorViewModel_Factory create(
      Provider<TransactionRepository> transactionRepositoryProvider,
      Provider<CategoryRepository> categoryRepositoryProvider,
      Provider<AccountRepository> accountRepositoryProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new TransactionEditorViewModel_Factory(transactionRepositoryProvider, categoryRepositoryProvider, accountRepositoryProvider, savedStateHandleProvider);
  }

  public static TransactionEditorViewModel newInstance(TransactionRepository transactionRepository,
      CategoryRepository categoryRepository, AccountRepository accountRepository,
      SavedStateHandle savedStateHandle) {
    return new TransactionEditorViewModel(transactionRepository, categoryRepository, accountRepository, savedStateHandle);
  }
}
