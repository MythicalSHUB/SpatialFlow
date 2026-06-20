---
name: android-presentation-mvi
description: |
  MVI presentation layer for Android/KMP - State, Action, Event, ViewModel, Root/Screen composable split, UI models, UiText error mapping, and process death with SavedStateHandle. Use this skill whenever creating or reviewing a ViewModel, defining screen state, actions, or events, structuring composables, mapping errors to UI strings, or handling process death. Trigger on phrases like "add a ViewModel", "create a screen", "MVI", "state", "action", "event", "screen composable", "UiText", "SavedStateHandle", "ObserveAsEvents", or "UI model".
---
 
# Android / KMP Presentation Layer (MVI)
 
## Overview
 
Every screen has:
1. **State** — a single data class holding all UI state fields.
2. **Action** (Intent) — a sealed interface of all user-triggered actions.
3. **Event** — a sealed interface of one-time side effects (navigation, snackbar).
4. **ViewModel** — holds `StateFlow<State>`, processes `Action`, emits `Event` via `Channel`.
 
---
 
## State
 
```kotlin
data class NoteListState(
    val notes: List<NoteUi> = emptyList(),
    val isLoading: Boolean = false,
    val error: UiText? = null
)
```
 
Always update state with `.update { }` — never replace the entire flow:
```kotlin
_state.update { it.copy(isLoading = true) }
```
 
---
 
## Action (Intent)
 
```kotlin
sealed interface NoteListAction {
    data object OnRefreshClick : NoteListAction
    data class OnNoteClick(val noteId: String) : NoteListAction
    data class OnDeleteNote(val noteId: String) : NoteListAction
}
```
 
---
 
## Event (one-time side effects)
 
```kotlin
sealed interface NoteListEvent {
    data class NavigateToDetail(val noteId: String) : NoteListEvent
    data class ShowSnackbar(val message: UiText) : NoteListEvent
}
```
 
---
 
## ViewModel
 
```kotlin
class NoteListViewModel(
    private val noteRepository: NoteRepository
) : ViewModel() {
 
    private val _state = MutableStateFlow(NoteListState())
    val state = _state.asStateFlow()
 
    private val _events = Channel<NoteListEvent>()
    val events = _events.receiveAsFlow()
 
    fun onAction(action: NoteListAction) {
        when (action) {
            is NoteListAction.OnRefreshClick -> loadNotes()
            is NoteListAction.OnNoteClick -> {
                viewModelScope.launch {
                    _events.send(NoteListEvent.NavigateToDetail(action.noteId))
                }
            }
        }
    }
 
    private fun loadNotes() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            noteRepository.getNotes()
                .onSuccess { notes ->
                    _state.update { it.copy(notes = notes.map { it.toNoteUi() }, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false) }
                    _events.send(NoteListEvent.ShowSnackbar(error.toUiText()))
                }
        }
    }
}
```
 
---
 
## Coroutine Dispatchers
 
**Do not inject** unless the class is unit-tested and dispatches to a non-main dispatcher. For ViewModel tests, use `Dispatchers.setMain(UnconfinedTestDispatcher())` in test setup.
 
For blocking code that doesn't support suspension, wrap it:
```kotlin
suspend fun compressImage(bytes: ByteArray): ByteArray = withContext(Dispatchers.IO) {
    // blocking compression logic
}
```
 
Only inject `CoroutineDispatcher` when:
1. The class dispatches to a non-main dispatcher (e.g., `IO`), AND
2. That class is directly unit-tested.
 
---
 
## Mapping Errors to UI Strings

`UiText` (`core:presentation`) wraps strings that originate from — or could originate from — a string resource:

```kotlin
sealed interface UiText {
    data class DynamicString(val value: String) : UiText
    class StringResource(val id: Int, val args: Array<Any> = emptyArray()) : UiText
}
```

**When to use `UiText`:** For any string that comes from a string resource, could be localized, or might be either a resource or a dynamic value depending on context (e.g., error messages that map to `R.string.*`).

**When to use plain `String`:** For values that are always dynamic and never come from resources — e.g., a user's name, a formatted date, a currency amount. These should be exposed as `String` directly in the state or UI model.

```kotlin
// UiText — error message that maps to a string resource
data class NoteListState(
    val error: UiText? = null
)

// Plain String — always dynamic, never a resource
data class NoteUi(
    val authorName: String,
    val formattedDate: String
)
```

Define `DataError.toUiText()` extension functions in `core:presentation` (or feature `presentation`) that map error enums to `UiText.StringResource`.
 
---
 
## UI Model (Presentation Model)
 
When a domain model needs UI-specific formatting (dates, units, currency), create a dedicated UI model in the presentation layer:
 
```kotlin
data class NoteUi(
    val id: String,
    val title: String,
    val formattedDate: String  // e.g. "Mar 15, 2026"
)
 
fun Note.toNoteUi(): NoteUi = NoteUi(
    id = id,
    title = title,
    formattedDate = date.format(...)
)
```
 
UI models are always suffixed with `Ui` (e.g., `NoteUi`, `TodoItemUi`).
 
---
 
## Composable Structure

Both the Root and Screen composable live in the **same file** (e.g., `NoteListScreen.kt`).

### Root Composable (suffixed `Root`)

Receives the ViewModel (via `koinViewModel()`) and any callbacks needed for navigation. Observes events. Passes state and `onAction` down.

### Screen Composable (suffixed `Screen`)

Receives only `state` and `onAction`. No ViewModel reference. Can be previewed independently.

```kotlin
// NoteListScreen.kt — Root + Screen in a single file

@Composable
fun NoteListRoot(
    onNavigateToDetail: (String) -> Unit,
    viewModel: NoteListViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is NoteListEvent.NavigateToDetail -> onNavigateToDetail(event.noteId)
            is NoteListEvent.ShowSnackbar -> { /* show snackbar */ }
        }
    }

    NoteListScreen(
        state = state,
        onAction = viewModel::onAction
    )
}

@Composable
fun NoteListScreen(
    state: NoteListState,
    onAction: (NoteListAction) -> Unit
) { ... }

@Preview
@Composable
private fun NoteListScreenPreview() {
    NoteListScreen(state = NoteListState(), onAction = {})
}
```
 
---
 
## Process Death
 
When a screen involves complex forms or critical user input, restore essential fields using `SavedStateHandle`:
 
```kotlin
class NoteEditorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository
) : ViewModel() {
    private val _state = MutableStateFlow(
        NoteEditorState(
            title = savedStateHandle["title"] ?: "",
            body = savedStateHandle["body"] ?: ""
        )
    )
 
    fun onAction(action: NoteEditorAction) {
        when (action) {
            is NoteEditorAction.OnTitleChange -> {
                savedStateHandle["title"] = action.title
                _state.update { it.copy(title = action.title) }
            }
        }
    }
}
```
 
Only save what truly matters after process death — not the entire state.
 
---
 
## Naming Conventions
 
| Thing | Convention | Example |
|---|---|---|
| ViewModel | `<Screen>ViewModel` | `NoteListViewModel` |
| State | `<Screen>State` | `NoteListState` |
| Action | `<Screen>Action` | `NoteListAction` |
| Event | `<Screen>Event` | `NoteListEvent` |
| Root composable | `<Screen>Root` | `NoteListRoot` |
| Screen composable | `<Screen>Screen` | `NoteListScreen` |
| UI model | `<Model>Ui` | `NoteUi`, `TodoItemUi` |
 
---
 
## Checklist: Adding a New Screen
 
- [ ] Define `State`, `Action`, `Event` in `feature:presentation`
- [ ] Implement `ViewModel` in `feature:presentation`
- [ ] Create `<Screen>Root` composable (holds ViewModel, observes events)
- [ ] Create `<Screen>Screen` composable (pure state + onAction, previewable)
- [ ] Map any domain errors to `UiText` via extension functions
- [ ] Add `SavedStateHandle` for any form fields that must survive process death