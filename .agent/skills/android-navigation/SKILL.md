---
name: android-navigation
description: |
  Type-safe Compose Navigation for Android/KMP - route objects, feature nav graphs, cross-feature callbacks, and wiring in :app. Use this skill whenever setting up navigation, defining routes, adding a new screen to a nav graph, navigating between features, or wiring nav graphs in the app module. Trigger on phrases like "set up navigation", "add a route", "navigate between screens", "nav graph", "NavController", "type-safe nav", "cross-feature navigation", or "NavGraphBuilder".
---
 
# Android / KMP Navigation
 
## Principles
 
- **Type-safe navigation** with `@Serializable` route objects (KotlinX Serialization).
- **One nav graph per feature**, defined in the feature's `presentation` module.
- Feature nav graphs are assembled in `:app`.
- Navigation **within** a feature uses a `NavController` passed into the feature nav graph.
- Feature-to-feature navigation uses **callbacks**, keeping features decoupled.
 
---
 
## Route Objects
 
Define routes as `@Serializable` objects or data classes in the feature's `presentation` module:
 
```kotlin
// feature:notes:presentation
@Serializable object NoteListRoute
@Serializable data class NoteDetailRoute(val noteId: String)
```
 
Use `data object` for screens with no parameters, `data class` for screens with arguments.
 
---
 
## Feature Nav Graph
 
Each feature exposes a `NavGraphBuilder` extension function:
 
```kotlin
// feature:notes:presentation
fun NavGraphBuilder.notesGraph(
    navController: NavController,
    onNavigateToEditor: (String) -> Unit  // callback for cross-feature navigation
) {
    navigation<NoteListRoute>(startDestination = NoteListRoute) {
        composable<NoteListRoute> {
            NoteListRoot(
                onNavigateToDetail = { navController.navigate(NoteDetailRoute(it)) }
            )
        }
        composable<NoteDetailRoute> { backStackEntry ->
            val route: NoteDetailRoute = backStackEntry.toRoute()
            NoteDetailRoot(
                noteId = route.noteId,
                onNavigateToEditor = onNavigateToEditor
            )
        }
    }
}
```
 
---
 
## Wiring in `:app`
 
All feature nav graphs are assembled in one place:
 
```kotlin
// :app
NavHost(navController, startDestination = NoteListRoute) {
    notesGraph(
        navController = navController,
        onNavigateToEditor = { navController.navigate(EditorRoute(it)) }
    )
    editorGraph(navController)
}
```
 
Cross-feature navigation is always expressed as a lambda callback — never by importing a route from another feature module.
 
---
 
## Passing Arguments
 
For simple scalar arguments, use `@Serializable data class` routes:
 
```kotlin
@Serializable data class NoteDetailRoute(val noteId: String)
 
// Navigate
navController.navigate(NoteDetailRoute(noteId = "abc123"))
 
// Receive
composable<NoteDetailRoute> { backStackEntry ->
    val route: NoteDetailRoute = backStackEntry.toRoute()
    // route.noteId available here
}
```
 
Avoid passing complex objects via navigation — pass IDs and load data in the destination ViewModel.
 
---
 
## Naming Conventions
 
| Thing | Convention | Example |
|---|---|---|
| Nav route | `<Screen>Route` | `NoteListRoute`, `NoteDetailRoute` |
| Feature nav graph | `<feature>Graph(...)` on `NavGraphBuilder` | `notesGraph(...)` |
 
---
 
## Checklist: Adding Navigation to a New Feature
 
- [ ] Define `@Serializable` route objects for each screen in `feature:presentation`
- [ ] Add feature nav graph function (`NavGraphBuilder.<feature>Graph(...)`)
- [ ] Pass `NavController` for intra-feature navigation
- [ ] Expose cross-feature destinations as lambda callbacks (not direct route imports)
- [ ] Wire nav graph and cross-feature callbacks in `:app`'s `NavHost`
 