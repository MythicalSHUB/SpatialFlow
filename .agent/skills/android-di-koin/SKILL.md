---
name: android-di-koin
description: |
  Koin dependency injection setup for Android/KMP - module definitions per layer, ViewModel injection, assembling modules in :app, and koinViewModel() in composables. Use this skill whenever setting up Koin, defining a DI module, providing a repository or ViewModel, injecting a dependency, or wiring modules in the Application class. Trigger on phrases like "set up Koin", "add a Koin module", "inject a dependency", "DI module", "koinViewModel", "provide a ViewModel", "startKoin", or "single/viewModel/factory".
---
 
# Android / KMP Dependency Injection (Koin)
 
## Principles
 
- One Koin module per feature layer — create it only if there are dependencies to provide.
- Modules are assembled in `:app`, never in feature modules themselves.
- In Compose root composables, always inject ViewModels via `koinViewModel()`.
 
---
 
## Module Definitions

Prefer the constructor-reference overloads (`singleOf`, `viewModelOf`, `factoryOf`) — they are more concise and let Koin resolve parameters automatically. Only fall back to the lambda overloads (`single { ... }`, `viewModel { ... }`, `factory { ... }`) when constructor injection alone is not enough, e.g. when you need to call a factory method, pass a named/qualified dependency, or do post-construction setup.

### Data layer module

```kotlin
// feature:notes:data
val notesDataModule = module {
    singleOf(::RoomNoteRepository) { bind<NoteRepository>() }
}
```

### Presentation layer module

```kotlin
// feature:notes:presentation
val notesPresentationModule = module {
    viewModelOf(::NoteListViewModel)
    viewModelOf(::NoteDetailViewModel)
}
```

### Core data module (example)

```kotlin
// core:data
val coreDataModule = module {
    // singleOf works when the class has a single injectable constructor
    singleOf(::SessionPreferences)

    // Lambda overload needed here — calling a factory method, not a constructor
    single { HttpClientFactory.create(get()) }
    single { createDataStore(get()) }
}
```
 
---
 
## Assembly in `:app`
 
Register all modules in the `Application` class:
 
```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(
                // core
                coreDataModule,
                // features
                notesDataModule,
                notesPresentationModule,
                authDataModule,
                authPresentationModule,
                // ...
            )
        }
    }
}
```
 
---
 
## Injecting in Composables
 
Always use `koinViewModel()` in Root composables:
 
```kotlin
@Composable
fun NoteListRoot(
    onNavigateToDetail: (String) -> Unit,
    viewModel: NoteListViewModel = koinViewModel()
) { ... }
```
 
Never pass ViewModels down the composable tree — inject at the Root level only.
 
---
 
## Scoping Rules

| Scope | Preferred form | Fallback form | When to use |
|---|---|---|---|
| Singleton | `singleOf(::Impl) { bind<Interface>() }` | `single { ... }` | One instance for the app lifetime (repositories, HttpClient, DB) |
| ViewModel | `viewModelOf(::MyViewModel)` | `viewModel { ... }` | ViewModel instances scoped to their lifecycle |
| Factory | `factoryOf(::Impl)` | `factory { ... }` | New instance on every injection (rare — prefer singleton or ViewModel) |

Use the `*Of` constructor-reference form by default. Only use the lambda form when you cannot express the binding with a constructor reference (factory methods, named qualifiers, manual setup).
 
---
 
## Naming Conventions
 
| Thing | Convention | Example |
|---|---|---|
| Koin module | `<feature><Layer>Module` | `notesDataModule`, `notesPresentationModule` |
 
---
 
## Checklist: Adding DI for a New Feature
 
- [ ] Define `val <feature>DataModule = module { ... }` in `feature:data`
- [ ] Define `val <feature>PresentationModule = module { ... }` in `feature:presentation`
- [ ] Register both modules in `:app`'s `startKoin { modules(...) }`
- [ ] Use `koinViewModel()` in all Root composables
 