package com.example.worktree.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File

data class WorktreeInfo(
    val path: String,
    val sha: String,
    val branch: String,
    val name: String
)

data class DiffStats(
    val filesChanged: Int,
    val insertions: Int,
    val deletions: Int
)

@Service(Service.Level.PROJECT)
class WorktreeService(private val project: Project) {

    private val logger = thisLogger()

    fun getWorktrees(): List<WorktreeInfo> {
        val rootPath = project.basePath ?: return emptyList()
        val result = mutableListOf<WorktreeInfo>()

        try {
            // "git worktree list --porcelain"; stderr is merged into stdout so a
            // chatty/failing git process can never fill a separate stderr pipe and
            // deadlock. The stream is always closed via use {}.
            val process = ProcessBuilder("git", "worktree", "list", "--porcelain")
                .directory(File(rootPath))
                .redirectErrorStream(true)
                .start()

            var currentPath = ""
            var currentSha = ""
            var currentBranch = ""

            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    when {
                        line.startsWith("worktree ") -> {
                            if (currentPath.isNotEmpty()) {
                                result.add(createWorktreeObj(currentPath, currentSha, currentBranch))
                            }
                            currentPath = line.substring("worktree ".length)
                            currentSha = ""
                            currentBranch = ""
                        }
                        line.startsWith("HEAD ") -> currentSha = line.substring("HEAD ".length)
                        line.startsWith("branch ") ->
                            currentBranch = line.substring("branch ".length).removePrefix("refs/heads/")
                    }
                }
                if (currentPath.isNotEmpty()) {
                    result.add(createWorktreeObj(currentPath, currentSha, currentBranch))
                }
            }

            process.waitFor()
        } catch (e: Exception) {
            logger.warn("Failed to list git worktrees", e)
        }

        return result
    }

    private fun createWorktreeObj(path: String, sha: String, branch: String): WorktreeInfo {
        val name = File(path).name
        return WorktreeInfo(path, sha, branch, name)
    }

    fun getBranchName(worktreePath: String): String =
        runCommand(worktreePath, listOf("git", "branch", "--show-current"))

    fun getDiffShortstat(worktreePath: String): String =
        runCommand(worktreePath, listOf("git", "diff", "HEAD", "--shortstat"))

    /**
     * Returns the HEAD (committed) version of a file in the worktree, or null if
     * the file does not exist at HEAD (i.e. it was newly added in the worktree).
     */
    fun getFileAtHead(worktreePath: String, relativePath: String): String? {
        return try {
            val process = ProcessBuilder("git", "show", "HEAD:$relativePath")
                .directory(File(worktreePath))
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.errorStream.bufferedReader().use { it.readText() }
            if (process.waitFor() == 0) output else null
        } catch (e: Exception) {
            logger.warn("git show HEAD:$relativePath failed in $worktreePath", e)
            null
        }
    }

    /** Parses `git diff --shortstat` into structured insertion/deletion counts. */
    fun getDiffStats(worktreePath: String): DiffStats {
        val output = getDiffShortstat(worktreePath)
        val filesChanged = Regex("(\\d+) files? changed").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val insertions = Regex("(\\d+) insertion").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val deletions = Regex("(\\d+) deletion").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return DiffStats(filesChanged, insertions, deletions)
    }

    fun getModifiedFiles(worktreePath: String): List<String> =
        runCommand(worktreePath, listOf("git", "diff", "HEAD", "--name-only"))
            .split("\n").filter { it.isNotBlank() }

    /** True if the worktree has any uncommitted or untracked changes. */
    fun hasUncommittedChanges(worktreePath: String): Boolean =
        runCommand(worktreePath, listOf("git", "status", "--porcelain")).isNotBlank()

    /**
     * Removes a worktree. Returns true on success. Without [force], git refuses
     * to remove a worktree that has uncommitted or untracked changes.
     */
    fun removeWorktree(worktreePath: String, force: Boolean = false): Boolean {
        val rootPath = project.basePath ?: return false
        return try {
            val command = mutableListOf("git", "worktree", "remove")
            if (force) command.add("--force")
            command.add(worktreePath)
            val process = ProcessBuilder(command)
                .directory(File(rootPath))
                .redirectErrorStream(true)
                .start()
            // Drain the output so the process never blocks on a full pipe.
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode != 0) logger.warn("git worktree remove failed (exit $exitCode): $output")
            exitCode == 0
        } catch (e: Exception) {
            logger.warn("Failed to remove worktree: $worktreePath", e)
            false
        }
    }

    /**
     * Runs a git command and returns its trimmed standard output.
     *
     * stdout and stderr are kept on separate streams (stderr drained on a worker
     * thread to avoid a pipe-buffer deadlock), so a chatty or failing git process
     * — e.g. a missing git-lfs filter printing "git-lfs: command not found" /
     * "fatal: the remote end hung up unexpectedly" — never leaks its diagnostics
     * into the parsed output, where they would otherwise appear as bogus
     * "changed files".
     */
    private fun runCommand(workingDir: String, command: List<String>): String {
        return try {
            val process = ProcessBuilder(command)
                .directory(File(workingDir))
                .start()
            val errText = StringBuilder()
            val errThread = Thread {
                process.errorStream.bufferedReader().use { reader ->
                    reader.forEachLine { errText.appendLine(it) }
                }
            }.apply { isDaemon = true; start() }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            errThread.join()
            if (exitCode != 0 && errText.isNotBlank()) {
                logger.warn("Command exited $exitCode: ${command.joinToString(" ")}\n${errText.toString().trim()}")
            }
            output.trim()
        } catch (e: Exception) {
            logger.warn("Command failed: ${command.joinToString(" ")}", e)
            ""
        }
    }
}
