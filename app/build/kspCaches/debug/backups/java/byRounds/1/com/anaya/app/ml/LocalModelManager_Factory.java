package com.anaya.app.ml;

import android.content.Context;
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
public final class LocalModelManager_Factory implements Factory<LocalModelManager> {
  private final Provider<Context> contextProvider;

  public LocalModelManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public LocalModelManager get() {
    return newInstance(contextProvider.get());
  }

  public static LocalModelManager_Factory create(Provider<Context> contextProvider) {
    return new LocalModelManager_Factory(contextProvider);
  }

  public static LocalModelManager newInstance(Context context) {
    return new LocalModelManager(context);
  }
}
