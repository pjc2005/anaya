# Anaya — 智能记账 Android App

## 技术栈
- 语言: Kotlin
- UI: Jetpack Compose + Material 3
- 架构: MVVM + Clean Architecture (data / domain / presentation)
- DI: Hilt
- 数据库: Room + SQLite
- 异步: Kotlin Coroutines + Flow
- 导航: Navigation Compose (单 Activity)
- 图表: Vico
- 模板引擎: Mustache / Kotlin String Template

## 项目结构规范
```
app/src/main/java/com/anaya/app/
├── AnayaApp.kt              # Application 入口
├── MainActivity.kt          # 单 Activity
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── dao/
│   │   ├── entity/
│   │   └── converter/
│   ├── repository/
│   └── mapper/
├── domain/
│   ├── model/
│   ├── repository/          # 接口
│   └── usecase/
├── ml/                      # 本地小模型相关
│   ├── LlamaEngine.kt
│   ├── PaymentClassifier.kt
│   ├── AutoClassifier.kt
│   └── SavingPlanGenerator.kt
├── service/
│   ├── PaymentAccessibilityService.kt
│   └── PaymentNotificationListener.kt
├── presentation/
│   ├── navigation/
│   ├── theme/
│   ├── common/
│   ├── home/
│   ├── transaction/
│   ├── stats/
│   ├── budget/
│   └── settings/
└── util/
```

## 金额处理
- 所有金额以 `分(Long)` 为单位存储，避免浮点精度问题
- 显示时统一格式化为 `元.角分`

## 包名
com.anaya.app

## Gradle 版本
- AGP: 8.2.0
- Kotlin: 1.9.20
- Compose BOM: 2024.02.00
- Room: 2.6.1
- Hilt: 2.50
- Navigation Compose: 2.7.7

## 编码规范
- 文件编码 UTF-8
- 缩进 4 spaces
- 函数/类需要 KDoc 注释
- ViewModel 使用 `stateIn` + `StateFlow`
- UI State 使用 sealed class / data class
