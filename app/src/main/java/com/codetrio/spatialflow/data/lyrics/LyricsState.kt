package com.codetrio.spatialflow.data.lyrics

/**
 * Lyrics fetch state machine.
 * Critical rule: once SUCCESS is reached, only REFETCHING is allowed (never
 * back to FETCHING).
 * This prevents the loading-hides-lyrics bug.
 */
enum class LyricsState {
    /** No lyrics operation in progress */
    IDLE,

    /** Initial fetch in progress — no lyrics to show yet */
    FETCHING,

    /** Lyrics successfully fetched and displayed */
    SUCCESS,

    /** All providers exhausted — no lyrics found */
    FAILED,

    /**
     * Background refetch in progress while current lyrics are displayed (e.g.,
     * upgrading unsynced → synced)
     */
    REFETCHING;

    /**
     * Returns true if the UI should show a loading indicator.
     * Only FETCHING shows a full-screen loader; REFETCHING shows a subtle
     * indicator.
     */
    val isLoading: Boolean get() = this == FETCHING

    /**
     * Checks if a transition to the target state is valid.
     * Prevents regression from SUCCESS back to FETCHING.
     */
    fun canTransitionTo(target: LyricsState): Boolean {
        return when (this) {
            IDLE -> target == FETCHING
            FETCHING -> target == SUCCESS || target == FAILED
            SUCCESS -> target == REFETCHING || target == IDLE
            FAILED -> target == FETCHING || target == IDLE
            REFETCHING -> target == SUCCESS || target == IDLE
        }
    }
}
