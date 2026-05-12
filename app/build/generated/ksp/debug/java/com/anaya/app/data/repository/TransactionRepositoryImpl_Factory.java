package com.anaya.app.data.repository;

import com.anaya.app.data.local.dao.TransactionDao;
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
public final class TransactionRepositoryImpl_Factory implements Factory<TransactionRepositoryImpl> {
  private final Provider<TransactionDao> transactionDaoProvider;

  public TransactionRepositoryImpl_Factory(Provider<TransactionDao> transactionDaoProvider) {
    this.transactionDaoProvider = transactionDaoProvider;
  }

  @Override
  public TransactionRepositoryImpl get() {
    return newInstance(transactionDaoProvider.get());
  }

  public static TransactionRepositoryImpl_Factory create(
      Provider<TransactionDao> transactionDaoProvider) {
    return new TransactionRepositoryImpl_Factory(transactionDaoProvider);
  }

  public static TransactionRepositoryImpl newInstance(TransactionDao transactionDao) {
    return new TransactionRepositoryImpl(transactionDao);
  }
}
