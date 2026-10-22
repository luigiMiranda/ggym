package com.ggym.app.data.backup

import com.ggym.app.domain.repository.BackupFailure

/**
 * Why a restore could not happen, as this package sees it.
 *
 * Each case carries a [detail] string taken from whatever actually refused — the JSON parser, the
 * validator, the provider or SQLite. That text is for a bug report and is never shown to a person:
 * [toDomain] turns the case into the screen's own vocabulary and the manager logs the detail on the
 * way past, so the message a user reads is a localized sentence while the log keeps the real
 * reason. Keeping the two apart is why this type exists at all instead of the domain one being used
 * throughout.
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

/** The same failure, in the vocabulary the screen localizes. */
internal fun ImportError.toDomain(): BackupFailure = when (this) {
    is ImportError.Malformed -> BackupFailure.Malformed
    is ImportError.UnsupportedVersion -> BackupFailure.UnsupportedVersion(found, supported)
    is ImportError.Inconsistent -> BackupFailure.Inconsistent
    is ImportError.Unreadable -> BackupFailure.Unreadable
    is ImportError.RestoreFailed -> BackupFailure.RestoreFailed
}
