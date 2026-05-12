package com.anaya.app.service;

import com.anaya.app.domain.repository.TransactionRepository;
import com.anaya.app.ml.LocalModelInterface;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class PaymentAutoCapture_Factory implements Factory<PaymentAutoCapture> {
  private final Provider<LocalModelInterface> localModelProvider;

  private final Provider<ClipboardMonitor> clipboardMonitorProvider;

  private final Provider<TransactionRepository> transactionRepositoryProvider;

  public PaymentAutoCapture_Factory(Provider<LocalModelInterface> localModelProvider,
      Provider<ClipboardMonitor> clipboardMonitorProvider,
      Provider<TransactionRepository> transactionRepositoryProvider) {
    this.localModelProvider = localModelProvider;
    this.clipboardMonitorProvider = clipboardMonitorProvider;
    this.transactionRepositoryProvider = transactionRepositoryProvider;
  }

  @Override
  public PaymentAutoCapture get() {
    return newInstance(localModelProvider.get(), clipboardMonitorProvider.get(), transactionRepositoryProvider.get());
  }

  public static PaymentAutoCapture_Factory create(Provider<LocalModelInterface> localModelProvider,
      Provider<ClipboardMonitor> clipboardMonitorProvider,
      Provider<TransactionRepository> transactionRepositoryProvider) {
    return new PaymentAutoCapture_Factory(localModelProvider, clipboardMonitorProvider, transactionRepositoryProvider);
  }

  public static PaymentAutoCapture newInstance(LocalModelInterface localModel,
      ClipboardMonitor clipboardMonitor, TransactionRepository transactionRepository) {
    return new PaymentAutoCapture(localModel, clipboardMonitor, transactionRepository);
  }
}
