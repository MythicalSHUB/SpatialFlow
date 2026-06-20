---
name: android-testing
description: |
  Testing patterns for Android/KMP - ViewModel unit tests with JUnit5, Turbine, AssertK, UnconfinedTestDispatcher, fake repositories, SavedStateHandle, and Compose UI tests. Use this skill whenever writing or reviewing tests for ViewModels, repositories, use cases, or Compose screens. Trigger on phrases like "write a test", "unit test the ViewModel", "test a repository", "Turbine", "fake repository", "UnconfinedTestDispatcher", "runTest", "ComposeTestRule", or "JUnit5".
---
 
# Android / KMP Testing
 
## Stack
 
| Concern | Library |
|---|---|
| Test framework | JUnit5 |
| Assertions | AssertK |
| Flow / StateFlow testing | Turbine |
| Coroutine testing | `kotlinx-coroutines-test` + `UnconfinedTestDispatcher` |
| UI testing | `ComposeTestRule` |
 
---
 
## ViewModel Unit Tests
 
### Setup
 
```kotlin
class NoteListViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
 
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }
 
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }
}
```
 
### Testing State with Turbine
 
```kotlin
@Test
fun `loading notes emits notes in state`() = runTest {
    val repo = FakeNoteRepository()
    val viewModel = NoteListViewModel(repo)
 
    viewModel.state.test {
        viewModel.onAction(NoteListAction.OnRefreshClick)
        assertThat(awaitItem().isLoading).isTrue()
        assertThat(awaitItem().notes).isNotEmpty()
    }
}
```
 
### Testing Events (one-time side effects)
 
```kotlin
@Test
fun `clicking note sends NavigateToDetail event`() = runTest {
    val viewModel = NoteListViewModel(FakeNoteRepository())
 
    viewModel.events.test {
        viewModel.onAction(NoteListAction.OnNoteClick("123"))
        assertThat(awaitItem()).isEqualTo(NoteListEvent.NavigateToDetail("123"))
    }
}
```
 
---
 
## Fake Repositories
 
Prefer **fakes** (not mocks) for repository dependencies. A fake is a simple in-memory implementation:
 
```kotlin
class FakeNoteRepository : NoteRepository {
    private val notes = mutableListOf<Note>()
    var shouldReturnError = false
 
    override suspend fun getNotes(): Result<List<Note>, DataError.Local> {
        return if (shouldReturnError) Result.Error(DataError.Local.UNKNOWN)
        else Result.Success(notes.toList())
    }
 
    override suspend fun insertNote(note: Note): EmptyResult<DataError.Local> {
        notes.add(note)
        return Result.Success(Unit)
    }
}
```
 
---
 
## SavedStateHandle in Tests
 
Instantiate it directly — no mocking needed:
 
```kotlin
val savedStateHandle = SavedStateHandle(mapOf("noteId" to "123"))
val viewModel = NoteEditorViewModel(savedStateHandle, FakeNoteRepository())
```
 
---
 
## When to Inject Dispatchers
 
Only inject `CoroutineDispatcher` into a class when:
1. It dispatches to a non-main dispatcher (e.g., `IO`), AND
2. That class is directly unit-tested.
 
ViewModels that only use `viewModelScope` do not need injected dispatchers. Use `Dispatchers.setMain()` in tests instead.
 
If a non-ViewModel class uses `withContext(Dispatchers.IO)` and is unit-tested, inject the dispatcher:
 
```kotlin
class ImageCompressor(private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    suspend fun compress(bytes: ByteArray): ByteArray = withContext(ioDispatcher) { ... }
}
 
// In test:
val compressor = ImageCompressor(ioDispatcher = UnconfinedTestDispatcher())
```
 
---
 
## Integration and E2E Tests

Write integration tests where database/network interactions are non-trivial. Write E2E tests for complex user flows using `ComposeTestRule`:

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun noteList_displaysNotes_afterLoad() {
    composeTestRule.setContent {
        NoteListScreen(
            state = NoteListState(notes = listOf(NoteUi("1", "Hello", "Mar 15"))),
            onAction = {}
        )
    }
    composeTestRule.onNodeWithText("Hello").assertIsDisplayed()
}
```

---

## Robot Pattern (Complex UI / E2E Tests)

For complex end-to-end or multi-step UI tests, use the **Robot Pattern** to separate test intent from Compose interactions. A robot encapsulates all `composeTestRule` interactions for a screen, keeping tests readable and DRY.

### Structure

Every robot function returns `this` so calls can be chained like a builder:

```kotlin
// Robot class — owns all UI interactions for the screen
class NoteListRobot(private val composeTestRule: ComposeContentTestRule) {

    fun setContent(
        state: NoteListState,
        onAction: (NoteListAction) -> Unit = {}
    ) = apply {
        composeTestRule.setContent {
            NoteListScreen(state = state, onAction = onAction)
        }
    }

    fun assertNoteVisible(title: String) = apply {
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
    }

    fun clickNote(title: String) = apply {
        composeTestRule.onNodeWithText(title).performClick()
    }

    fun assertEmptyState() = apply {
        composeTestRule.onNodeWithTag("empty_state").assertIsDisplayed()
    }
}
```

### Usage in Tests

```kotlin
class NoteListScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot by lazy { NoteListRobot(composeTestRule) }

    @Test
    fun displaysNotes_afterLoad() {
        robot
            .setContent(NoteListState(notes = listOf(NoteUi("1", "Hello", "Mar 15"))))
            .assertNoteVisible("Hello")
    }

    @Test
    fun showsEmptyState_whenNoNotes() {
        robot
            .setContent(NoteListState(notes = emptyList()))
            .assertEmptyState()
    }

    @Test
    fun clickingNote_triggersAction() {
        var clickedId: String? = null
        robot
            .setContent(
                state = NoteListState(notes = listOf(NoteUi("1", "Hello", "Mar 15"))),
                onAction = { if (it is NoteListAction.OnNoteClick) clickedId = it.noteId }
            )
            .assertNoteVisible("Hello")
            .clickNote("Hello")
    }
}
```

**When to use:** Apply the robot pattern when a screen has 3+ UI test cases, when multiple tests share the same setup/assertion sequences, or when testing complex multi-step user flows (e.g., fill form → submit → assert result).
 
---
 
## What to Test
 
- Unit-test every ViewModel and any non-trivial domain/data logic.
- Unit-test any logic that is likely to change.
- Use fakes over mocks where possible — fakes are simpler and catch more real bugs.
- Write integration tests where DB/network interactions are non-trivial.
- Write E2E Compose tests for critical user flows.
- Use the robot pattern for complex UI/E2E tests with multiple test cases or shared interaction sequences.