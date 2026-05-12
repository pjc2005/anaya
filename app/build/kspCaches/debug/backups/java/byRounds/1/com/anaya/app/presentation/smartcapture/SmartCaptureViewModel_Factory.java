package com.anaya.app.presentation.smartcapture;

import com.anaya.app.domain.repository.AccountRepository;
import com.anaya.app.domain.repository.CategoryRepository;
import com.anaya.app.domain.repository.TransactionRepository;
import com.anaya.app.ml.LocalModelInterface;
import com.anaya.app.service.ClipboardMonitor;
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
public final class SmartCaptureViewModel_Factory implements Factory<SmartCaptureViewModel> {
  private final Provider<ClipboardMonitor> clipboardMonitorProvider;

  private final Provider<LocalModelInterface> localModelProvider;

  private final Provider<TransactionRepository> transactionRepositoryProvider;

  private final Provider<CategoryRepository> categoryRepositoryProvider;

  private final Provider<AccountRepository> accountRepositoryProvider;

  public SmartCaptureViewModel_Factory(Provider<ClipboardMonitor> clipboardMonitorProvider,
      Provider<LocalModelInterface> localModelProvider,
      Provider<TransactionRepository> transactionRepositoryProvider,
      Provider<CategoryRepository> categoryRepositoryProvider,
      Provider<AccountRepository> accountRepositoryProvider) {
    this.clipboardMonitorProvider = clipboardMonitorProvider;
    this.localModelProvider = localModelProvider;
    this.transactionRepositoryProvider = transactionRepositoryProvider;
    this.categoryRepositoryProvider = categoryRepositoryProvider;
    this.accountRepositoryProvider = accountRepositoryProvider;
  }

  @Override
  public SmartCaptureViewModel get() {
    return newInstance(clipboardMonitorProvider.get(), localModelProvider.get(), transactionRepositoryProvider.get(), categoryRepositoryProvider.get(), accountRepositoryProvider.get());
  }

  public static SmartCaptureViewModel_Factory create(
      Provider<ClipboardMonitor> clipboardMonitorProvider,
      Provider<LocalModelInterface> localModelProvider,
      Provider<TransactionRepository> transactionRepositoryProvider,
      Provider<CategoryRepository> categoryRepositoryProvider,
      Provider<AccountRepository> accountRepositoryProvider) {
    return new SmartCaptureViewModel_Factory(clipboardMonitorProvider, localModelProvider, transactionRepositoryProvider, categoryRepositoryProvider, accountRepositoryProvider);
  }

  public static SmartCaptureViewModel newInstance(ClipboardMonitor clipboardMonitor,
      LocalModelInterface localModel, TransactionRepository transactionRepository,
      CategoryRepository categoryRepository, AccountRepository accountRepository) {
    return new SmartCaptureViewModel(clipboardMonitor, localModel, transactionRepository, categoryRepository, accountRepository);
  }
}
