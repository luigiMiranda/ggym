package com.ggym.app.testing

import android.net.Uri
import com.ggym.app.domain.repository.BackupExportResult
import com.ggym.app.domain.repository.BackupImportResult
import com.ggym.app.domain.repository.BackupRepository
import com.ggym.app.domain.repository.BackupSummary

/**
 * A backup that never touches a file: it records what it was asked and answers with whatever the
 * test told it to.
 *
 * The defaults are the happy path, so a test that only cares about one direction says nothing about
 * the other.
 */
class FakeBackupRepository : BackupRepository {

    /** What the next export answers. The default is the happy path. */
    var exportResult: BackupExportResult = BackupExportResult.Written

    /** What the next import answers. The default is the happy path — an empty but valid file. */
    var importResult: BackupImportResult = BackupImportResult.Restored(BackupSummary(0, 0, 0, 0))

    /** The documents exports were pointed at, in order. */
    val exported: MutableList<Uri> = mutableListOf()

    /** The documents restores were pointed at, in order. */
    val imported: MutableList<Uri> = mutableListOf()

    override suspend fun exportTo(document: Uri): BackupExportResult {
        exported += document
        return exportResult
    }

    override suspend fun importFrom(document: Uri): BackupImportResult {
        imported += document
        return importResult
    }
}
