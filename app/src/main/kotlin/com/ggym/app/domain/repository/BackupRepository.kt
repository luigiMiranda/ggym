package com.ggym.app.domain.repository

import android.net.Uri

/**
 * Moving the whole database out to a file and back in.
 *
 * One file is the whole state: routines, the dictionary, the entire history and the body weights.
 * The two directions are deliberately asymmetric, and the asymmetry is the point:
 *
 * - **Export** is a read. It snapshots every table inside one transaction, so a workout saved while
 *   the export runs cannot land half-written in the file. The user picks the document ([Uri]) with
 *   the system's own picker, so the app never guesses a destination and needs no storage
 *   permission.
 * - **Import** is a replacement, not a merge. It parses, validates, and only then writes — and it
 *   writes everything in one transaction. A file that fails at any step leaves the device exactly
 *   as it was. "The phone ends up in the state the file describes" is what "move my data to a new
 *   phone" means; merging two divergent histories is a different feature with no obvious rule for
 *   whose workout wins.
 *
 * Failure is always a value, never an exception: a screen shows the case, and the detail a provider
 * or the database supplied stays in the log. The cases are in [BackupFailure] rather than in the
 * message strings, because the sentences the user reads are localized by the screen and the app
 * ships two languages.
 *
 * The file format has its own version, separate from the database schema version, and its reader
 * accepts every format the app has ever written — not only the newest. A backup file outlives the
 * build that made it, and "your backup is too old" is the one answer this interface must never
 * give. See `data/backup/BackupJson.kt` for the window and the per-format defaults.
 */
interface BackupRepository {

    /**
     * Writes the whole database to [document].
     *
     * A document that already holds something is replaced: the picker's answer is the destination,
     * not a new name to append to.
     */
    suspend fun exportTo(document: Uri): BackupExportResult

    /**
     * Reads [document] and, only if it is a GGym backup this build understands, replaces the
     * database contents with it.
     *
     * Confirming that a replacement is what the user wants is the caller's job, not this
     * interface's: the repository cannot ask.
     */
    suspend fun importFrom(document: Uri): BackupImportResult
}

/** Outcome of writing a backup file. */
sealed interface BackupExportResult {

    /** The file was written in full. */
    data object Written : BackupExportResult

    /**
     * Nothing was written — the document could not be opened, there was no space, or the provider
     * refused. The device's data is untouched either way.
     */
    data object Failed : BackupExportResult
}

/** Outcome of a restore. */
sealed interface BackupImportResult {

    /** Every table was replaced; [summary] says with how much. */
    data class Restored(val summary: BackupSummary) : BackupImportResult

    /** Nothing was written. See [failure]. */
    data class Failed(val failure: BackupFailure) : BackupImportResult
}

/**
 * What a restore wrote, in the words the outcome sentence uses.
 *
 * Counted per thing the user recognizes — routines, workouts, exercises, weigh-ins — rather than
 * per table, because the sentence is for a person: "sessions" and "exercise logs" are storage, and
 * a number they cannot match to a screen tells them nothing.
 */
data class BackupSummary(
    val routines: Int,
    val workouts: Int,
    val exercises: Int,
    val weighIns: Int,
)

/**
 * Why a restore did not happen.
 *
 * Each case is a sentence the user can act on, and each one is distinguishable from the others in a
 * way that changes what they would do next: pick a different file, update the app, pick a different
 * file again, check that the file still exists, or try again. A single "import failed" would hide
 * exactly that difference.
 */
sealed interface BackupFailure {

    /** The file is not a GGym backup: not JSON, or JSON that does not match the format. */
    data object Malformed : BackupFailure

    /**
     * Written by an app version whose format this build cannot read — newer than the newest
     * format this build writes, or older than the oldest it still accepts.
     */
    data class UnsupportedVersion(val found: Int, val supported: Int) : BackupFailure

    /** Structurally valid, but the rows inside contradict each other. */
    data object Inconsistent : BackupFailure

    /** The file could not be read at all: it was moved, or access to it was refused. */
    data object Unreadable : BackupFailure

    /**
     * The file was fine, but the database refused or failed the restore. Nothing was changed: the
     * whole restore is one transaction.
     */
    data object RestoreFailed : BackupFailure
}
