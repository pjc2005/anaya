package com.anaya.app.service;

import com.anaya.app.ml.LocalModelInterface;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class PaymentAccessibilityService_MembersInjector implements MembersInjector<PaymentAccessibilityService> {
  private final Provider<LocalModelInterface> localModelProvider;

  public PaymentAccessibilityService_MembersInjector(
      Provider<LocalModelInterface> localModelProvider) {
    this.localModelProvider = localModelProvider;
  }

  public static MembersInjector<PaymentAccessibilityService> create(
      Provider<LocalModelInterface> localModelProvider) {
    return new PaymentAccessibilityService_MembersInjector(localModelProvider);
  }

  @Override
  public void injectMembers(PaymentAccessibilityService instance) {
    injectLocalModel(instance, localModelProvider.get());
  }

  @InjectedFieldSignature("com.anaya.app.service.PaymentAccessibilityService.localModel")
  public static void injectLocalModel(PaymentAccessibilityService instance,
      LocalModelInterface localModel) {
    instance.localModel = localModel;
  }
}
