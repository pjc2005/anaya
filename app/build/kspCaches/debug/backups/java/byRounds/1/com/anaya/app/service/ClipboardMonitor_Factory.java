package com.anaya.app.service;

import android.content.Context;
import com.anaya.app.ml.LocalModelInterface;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class ClipboardMonitor_Factory implements Factory<ClipboardMonitor> {
  private final Provider<Context> contextProvider;

  private final Provider<LocalModelInterface> localModelProvider;

  public ClipboardMonitor_Factory(Provider<Context> contextProvider,
      Provider<LocalModelInterface> localModelProvider) {
    this.contextProvider = contextProvider;
    this.localModelProvider = localModelProvider;
  }

  @Override
  public ClipboardMonitor get() {
    return newInstance(contextProvider.get(), localModelProvider.get());
  }

  public static ClipboardMonitor_Factory create(Provider<Context> contextProvider,
      Provider<LocalModelInterface> localModelProvider) {
    return new ClipboardMonitor_Factory(contextProvider, localModelProvider);
  }

  public static ClipboardMonitor newInstance(Context context, LocalModelInterface localModel) {
    return new ClipboardMonitor(context, localModel);
  }
}
