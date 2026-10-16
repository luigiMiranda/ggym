package com.ggym.app.data.backup

/**
 * Why a backup could not be restored.
 *
 * Each case is something the user can be told in one sentence, and [detail] is for the log or a
 * bug report rather than the screen: the screens localize their own message per case, so a
 * human-readable detail string never has to be translated.
 */
internal sealed interface ImportError {

    /** The file is not a GGym backup: not JSON, or JSON that does not match the format. */
    data class Malformed(val detail: String) : ImportError

    /**
     * Written by an app version whose format this build cannot read — either newer than
     * `supported`, or older than the oldest format still supported.
     */
    data class UnsupportedVersion(val found: Int, val supported: Int) : ImportError

    /** Structurally valid, but the rows inside contradict each other. */
    data class Inconsistent(val detail: String) : ImportError

    /** The file could not be read at all — the document was moved, or access was refused. */
    data class Unreadable(val detail: String) : ImportError

    /**
     * The file was fine, but the database refused or failed the restore. Nothing was changed:
     * the whole restore is one transaction.
     */
    data class RestoreFailed(val detail: String) : ImportError
}

/** Outcome of a restore. */
internal sealed interface ImportResult {

    /** Every table was replaced; [counts] says with how much. */
    data class Restored(val counts: BackupCounts) : ImportResult

    /** Nothing was written. See [error]. */
    data class Failed(val error: ImportError) : ImportResult
}

/** How many rows a restore wrote, per top-level table. */
internal data class BackupCounts(
    val exercises: Int,
    val routines: Int,
    val sessions: Int,
    val bodyWeights: Int,
)

/** Outcome of writing a backup file. */
internal sealed interface ExportResult {

    /** The file was written in full. */
    data object Written : ExportResult

    /** The document could not be written — no space, or the provider refused. */
    data class Failed(val detail: String) : ExportResult
}
