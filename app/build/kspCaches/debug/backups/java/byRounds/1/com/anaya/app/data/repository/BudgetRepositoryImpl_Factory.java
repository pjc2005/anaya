package com.anaya.app.data.repository;

import com.anaya.app.data.local.dao.BudgetDao;
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
public final class BudgetRepositoryImpl_Factory implements Factory<BudgetRepositoryImpl> {
  private final Provider<BudgetDao> budgetDaoProvider;

  public BudgetRepositoryImpl_Factory(Provider<BudgetDao> budgetDaoProvider) {
    this.budgetDaoProvider = budgetDaoProvider;
  }

  @Override
  public BudgetRepositoryImpl get() {
    return newInstance(budgetDaoProvider.get());
  }

  public static BudgetRepositoryImpl_Factory create(Provider<BudgetDao> budgetDaoProvider) {
    return new BudgetRepositoryImpl_Factory(budgetDaoProvider);
  }

  public static BudgetRepositoryImpl newInstance(BudgetDao budgetDao) {
    return new BudgetRepositoryImpl(budgetDao);
  }
}
