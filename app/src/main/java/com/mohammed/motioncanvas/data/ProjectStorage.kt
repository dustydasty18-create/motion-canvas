package com.mohammed.motioncanvas.data

import android.content.Context
import android.net.Uri
import com.mohammed.motioncanvas.model.Project
import java.io.File

object ProjectStorage {
    private const val AUTOSAVE_FILE = "autosave_project.json"

    fun saveAuto(context: Context, project: Project) {
        runCatching {
            File(context.filesDir, AUTOSAVE_FILE).writeText(project.toJson())
        }
    }

    fun loadAuto(context: Context): Project {
        val file = File(context.filesDir, AUTOSAVE_FILE)
        if (!file.exists()) return Project.sampleDemo()
        return runCatching { Project.fromJson(file.readText()) }
            .getOrElse { Project.sampleDemo() }
    }

    fun exportToUri(context: Context, uri: Uri, project: Project): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(project.toJson().toByteArray(Charsets.UTF_8))
            }
            true
        }.getOrDefault(false)
    }

    fun importFromUri(context: Context, uri: Uri): Project? {
        return runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            } ?: return null
            Project.fromJson(raw)
        }.getOrNull()
    }
}
