---
name: android-module-structure
description: Module layout, dependency rules, and Gradle convention plugins for Android and Kotlin Multiplatform (KMP) projects. Use this skill whenever setting up a new Android/KMP project, deciding where a new module should live, asking "how should I structure this", creating a new feature module, adding a core submodule, configuring Gradle convention plugins, working with version catalogs, or making any decision about project-level architecture. Trigger on phrases like "set up the project", "add a module", "create a feature", "how should I structure", "project structure", "convention plugin", "build-logic", or "where does X live".
---
 
# Android / KMP Module Structure
 
## Core Philosophy
 
- **Feature-layered modularization**: split by feature first, then by layer within each feature.
- **Clean Architecture layers**: `presentation` → `domain` ← `data`. Domain is innermost and depends on nothing.
- **Code lives in a feature module unless it is needed by more than one feature** — then it moves to the appropriate `core` submodule.
- Features **never depend on each other**. Cross-feature shared data belongs in `core:domain` (domain models) or `core:presentation` (shared composables/UI logic), not in the owning feature.
 
---
 
## Module Layout
 
```
:app
:build-logic                    ← Gradle convention plugins
:core:domain                    ← Shared domain models, repository interfaces, error types, Result
:core:data                      ← Shared data logic, Ktor HttpClient factory, shared DB schemas/DAOs
:core:presentation              ← Shared UI utilities (ObserveAsEvents, UiText, etc.)
:core:design-system             ← Reusable Compose components, colors, theme, typography
:feature:<name>:domain          ← Feature-specific domain models, repo interfaces, error types
:feature:<name>:data            ← Repo implementations, DTOs, mappers, Room DAOs
:feature:<name>:presentation    ← ViewModel, screen composables, state, actions, events
```
 
For standalone, self-contained concerns that involve meaningful complexity (multiple classes, configuration, or a non-trivial API surface), create a dedicated module under `:core` (e.g., `:core:location`, `:core:analytics`). Do not create a separate module for a single class or a trivial utility — that belongs in an existing `core` module instead.

A shared Room database is a good candidate for a dedicated `:core:database` module. It contains the `@Database` class, all entity definitions, all DAOs, and migrations. Feature modules that need DB access depend on `:core:database` directly, while features that don't need it remain decoupled.
 
---
 
## Dependency Rules
 
| Layer | May depend on |
|---|---|
| `presentation` | `domain` (own feature), `core:domain`, `core:presentation`, `core:design-system` |
| `data` | `domain` (own feature), `core:domain`, `core:data` |
| `domain` | `core:domain` only — never `data` or `presentation` |
| `:app` | everything (wires all modules) |

**Every** layer and module may access `core:domain`.
 
---
 
## Convention Plugins (`:build-logic`)
 
Define a convention plugin for every non-trivial Gradle config:
 
| Plugin | Purpose |
|---|---|
| `android-application` | App module config (applicationId, versionCode, etc.) |
| `android-library` | Base Android library config |
| `android-feature` | Android library + Compose + Koin + shared feature deps bundled |
| `domain-module` | Pure Kotlin/KMP module, no Android deps |
| `compose` | Compose compiler + BOM |
| `koin` | Koin dependency block |
| `ktor` | Ktor client + serialization |
| `room` | Room + KSP config |
| `kotlinx-serialization` | KotlinX Serialization plugin + dep |
 
Use **version catalogs** (`libs.versions.toml`) for all dependency and version management. No hardcoded versions in build files.
 
---
 
## Key Libraries
 
| Concern | Library |
|---|---|
| DI | Koin |
| Networking | Ktor Client |
| Local DB | Room |
| Preferences | DataStore |
| Navigation | Compose Navigation (type-safe) |
| Serialization | KotlinX Serialization (Ktor + Nav routes) |
| Image loading | Coil |
| Logging | Kermit |
| Async | Coroutines + Flow |
| Background tasks | WorkManager |
| Secrets | `local.properties` + `BuildConfig` (Android); `BuildKonfig` (KMP) |
| Testing | JUnit5, Turbine, AssertK, `kotlinx-coroutines-test` |
| UI testing | `ComposeTestRule` |
 
---
 
## Checklist: Adding a New Feature Module
 
- [ ] Create `:feature:<name>:domain`, `:feature:<name>:data`, `:feature:<name>:presentation` modules
- [ ] Apply appropriate convention plugins to each module (`domain-module`, `android-library`/`android-feature`)
- [ ] Verify no cross-feature dependencies are introduced
- [ ] If logic is shared across 2+ features, extract to the appropriate `core` submodule