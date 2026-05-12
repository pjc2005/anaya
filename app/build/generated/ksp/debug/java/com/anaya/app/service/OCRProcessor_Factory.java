package com.anaya.app.service;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class OCRProcessor_Factory implements Factory<OCRProcessor> {
  @Override
  public OCRProcessor get() {
    return newInstance();
  }

  public static OCRProcessor_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static OCRProcessor newInstance() {
    return new OCRProcessor();
  }

  private static final class InstanceHolder {
    private static final OCRProcessor_Factory INSTANCE = new OCRProcessor_Factory();
  }
}
