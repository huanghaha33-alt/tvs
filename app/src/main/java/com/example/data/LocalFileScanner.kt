package com.example.data

import android.content.Context
import android.os.Environment
import java.io.File

data class ScannedFile(
    val file: File,
    val name: String,
    val path: String,
    val sizeText: String
)

object LocalFileScanner {

    fun scanCommonM3uFiles(context: Context): List<ScannedFile> {
        val results = mutableListOf<ScannedFile>()
        val searchedPaths = mutableSetOf<String>()

        val searchDirs = mutableListOf<File>()

        // 1. External files dirs (USB and external SD reported by Android system)
        try {
            val extDirs = context.getExternalFilesDirs(null)
            for (dir in extDirs) {
                dir?.let {
                    // Navigate up to root of this storage volume
                    var parent: File? = it
                    while (parent != null && parent.parentFile != null && parent.parentFile?.path != "/storage" && parent.parentFile?.path != "/mnt") {
                        parent = parent.parentFile
                    }
                    parent?.let { p -> searchDirs.add(p) }
                }
            }
        } catch (_: Exception) {}

        // 2. Standard internal storage & Downloads
        try {
            Environment.getExternalStorageDirectory()?.let { searchDirs.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { searchDirs.add(it) }
        } catch (_: Exception) {}

        // 3. Known TV box mount points
        val commonMounts = listOf(
            "/sdcard",
            "/sdcard/Download",
            "/storage/emulated/0",
            "/storage/emulated/0/Download",
            "/storage",
            "/mnt/media_rw",
            "/mnt/usb",
            "/mnt/sda1",
            "/mnt/sdb1"
        )
        for (mount in commonMounts) {
            val f = File(mount)
            if (f.exists() && f.canRead()) {
                searchDirs.add(f)
            }
        }

        for (dir in searchDirs) {
            if (dir.exists() && dir.canRead() && !searchedPaths.contains(dir.absolutePath)) {
                searchedPaths.add(dir.absolutePath)
                scanDirectory(dir, results, maxDepth = 2)
            }
        }

        return results.distinctBy { it.path }
    }

    private fun scanDirectory(dir: File, results: MutableList<ScannedFile>, maxDepth: Int) {
        if (maxDepth < 0 || !dir.canRead() || results.size >= 50) return
        try {
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.isDirectory) {
                    // Skip hidden and system folders
                    if (!f.name.startsWith(".") && !f.name.equals("Android", ignoreCase = true)) {
                        scanDirectory(f, results, maxDepth - 1)
                    }
                } else if (f.isFile) {
                    val ext = f.extension.lowercase()
                    if (ext == "m3u" || ext == "m3u8" || ext == "txt") {
                        val sizeKb = (f.length() / 1024).coerceAtLeast(1)
                        results.add(
                            ScannedFile(
                                file = f,
                                name = f.name,
                                path = f.absolutePath,
                                sizeText = if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb KB"
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
