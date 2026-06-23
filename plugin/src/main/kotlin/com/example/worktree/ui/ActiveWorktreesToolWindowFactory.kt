package com.example.worktree.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.example.worktree.service.WorktreeService
import com.example.worktree.service.WorktreeInfo
import com.example.worktree.service.DiffStats
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.editor.ChainDiffVirtualFile
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*

class ActiveWorktreesToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = project.getService(WorktreeService::class.java)
        val app = ApplicationManager.getApplication()

        // ----- Worktree list (with +/- badges) -----
        val statsByPath = HashMap<String, DiffStats>()
        val worktreeModel = DefaultListModel<WorktreeInfo>()
        val worktreeList = JBList(worktreeModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = SimpleListCellRenderer.create { label, value, _ ->
                if (value != null) {
                    val s = statsByPath[value.path]
                    val badge = if (s != null) "   +${s.insertions} -${s.deletions}" else ""
                    label.text = "${value.name} (${value.branch})$badge"
                    label.icon = com.intellij.icons.AllIcons.Nodes.Folder
                }
            }
        }

        // ----- Changed-files list -----
        val filesModel = DefaultListModel<String>()
        val filesList = JBList(filesModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            emptyText.text = "Select a worktree to see its changed files"
            cellRenderer = SimpleListCellRenderer.create { label, value, _ ->
                if (value != null) {
                    label.text = value
                    label.icon = com.intellij.icons.AllIcons.FileTypes.Any_type
                }
            }
        }
        val reviewAllButton = JButton("Review all").apply { isEnabled = false }

        // ----- Worktree-scoped actions (placed above the list) -----
        val refreshButton = JButton("Refresh")
        val openButton = JButton("Open in IDE").apply { isEnabled = false }
        val deleteButton = JButton("Delete").apply { isEnabled = false }

        // ----- Diff helpers -----
        // Reuse a single diff editor tab: close the previous one before opening a
        // new one, so navigating files never stacks duplicate diff windows/tabs.
        // Track open diff tabs per (worktree, file) key, so re-opening the same
        // file focuses its existing tab (no duplicate), while different files open
        // in their own tabs.
        val openDiffs = HashMap<String, VirtualFile>()
        fun showDiff(key: String, requests: List<SimpleDiffRequest>, title: String) {
            if (requests.isEmpty()) return
            val editorManager = FileEditorManager.getInstance(project)
            val cached = openDiffs[key]
            if (cached != null && cached.isValid && editorManager.isFileOpen(cached)) {
                editorManager.openFile(cached, true) // already open → just focus it
                return
            }
            val diffFile = ChainDiffVirtualFile(SimpleDiffRequestChain(requests), title)
            openDiffs[key] = diffFile
            editorManager.openFile(diffFile, true)
        }
        fun closeWorktreeDiffs(worktreePath: String) {
            val editorManager = FileEditorManager.getInstance(project)
            val prefix = "$worktreePath\n"
            openDiffs.keys.filter { it.startsWith(prefix) }.toList().forEach { key ->
                openDiffs.remove(key)?.let { if (it.isValid) editorManager.closeFile(it) }
            }
        }

        fun buildRequest(worktree: WorktreeInfo, relativePath: String): SimpleDiffRequest? {
            val factory = DiffContentFactory.getInstance()
            val lfs = LocalFileSystem.getInstance()
            // Compare the worktree's own committed (HEAD) version against its current
            // working-tree file, so only the uncommitted changes show up.
            val workingVf = lfs.refreshAndFindFileByIoFile(File(worktree.path, relativePath))
            val headText = service.getFileAtHead(worktree.path, relativePath)
            // A file may exist on only one side (added or deleted since HEAD);
            // use empty content for the missing side instead of skipping it.
            if (workingVf == null && headText == null) return null
            val headContent = if (headText != null) factory.create(project, headText, workingVf?.fileType) else factory.createEmpty()
            val workingContent = if (workingVf != null) factory.create(project, workingVf) else factory.createEmpty()
            return SimpleDiffRequest(
                relativePath,
                headContent,
                workingContent,
                "HEAD (${worktree.branch})",
                "Working tree"
            )
        }

        fun openSingleDiff(worktree: WorktreeInfo, relativePath: String) {
            val request = buildRequest(worktree, relativePath) ?: return
            showDiff("${worktree.path}\n${relativePath}", listOf(request), relativePath)
        }

        // Opens every changed file in the one diff tab; page through with prev/next.
        fun reviewAll(worktree: WorktreeInfo, files: List<String>) {
            val requests = files.mapNotNull { buildRequest(worktree, it) }
            showDiff("${worktree.path}\nALL", requests, "${worktree.name} — all changes")
        }

        // Loads the changed files for a worktree; also refreshes its +/- badge.
        // Called on every worktree click so the view reflects the latest git state.
        fun loadFilesFor(worktree: WorktreeInfo) {
            filesModel.clear()
            reviewAllButton.isEnabled = false
            // Show a "Loading…" placeholder instead of the default "Nothing to show"
            // while the changed-file list is fetched off the EDT.
            filesList.emptyText.text = "Loading changed files…"
            app.executeOnPooledThread {
                val files = service.getModifiedFiles(worktree.path)
                val stats = service.getDiffStats(worktree.path)
                app.invokeLater {
                    statsByPath[worktree.path] = stats
                    worktreeList.repaint()
                    // Drop stale results if the user already moved to another worktree.
                    if (worktreeList.selectedValue?.path != worktree.path) return@invokeLater
                    filesModel.clear()
                    files.forEach { filesModel.addElement(it) }
                    reviewAllButton.isEnabled = files.isNotEmpty()
                    filesList.emptyText.text = if (files.isEmpty()) "No uncommitted changes" else "Nothing to show"
                }
            }
        }

        fun reloadWorktrees() {
            refreshButton.isEnabled = false
            app.executeOnPooledThread {
                val worktrees = service.getWorktrees()
                val stats = worktrees.associate { it.path to service.getDiffStats(it.path) }
                app.invokeLater {
                    statsByPath.clear()
                    statsByPath.putAll(stats)
                    worktreeModel.clear()
                    worktrees.forEach { worktreeModel.addElement(it) }
                    filesModel.clear()
                    filesList.emptyText.text = "Select a worktree to see its changed files"
                    reviewAllButton.isEnabled = false
                    openButton.isEnabled = false
                    deleteButton.isEnabled = false
                    refreshButton.isEnabled = true
                }
            }
        }

        // Removes the selected worktree after confirmation, then reloads the list.
        fun deleteWorktree(worktree: WorktreeInfo) {
            // Check for uncommitted/untracked work first, so a dirty worktree shows
            // the strong "permanent data loss" warning up front (not as a second step).
            app.executeOnPooledThread {
                val dirty = service.hasUncommittedChanges(worktree.path)
                app.invokeLater {
                    val confirmed = if (dirty) {
                        Messages.showYesNoDialog(
                            project,
                            "⚠ WARNING — THIS PERMANENTLY DELETES UNCOMMITTED WORK ⚠\n\n" +
                                "\"${worktree.name}\" has uncommitted or untracked changes.\n\n" +
                                "Path: ${worktree.path}\n\n" +
                                "Removing it will PERMANENTLY DISCARD all of those changes. " +
                                "There is NO undo and the work CANNOT be recovered.\n\n" +
                                "Only continue if you are absolutely sure you no longer need this work.",
                            "Force Remove Worktree — Permanent Data Loss",
                            "Force remove (discard all changes)",
                            "Cancel",
                            Messages.getWarningIcon()
                        ) == Messages.YES
                    } else {
                        Messages.showYesNoDialog(
                            project,
                            "Remove worktree \"${worktree.name}\"?\n${worktree.path}",
                            "Remove Worktree",
                            Messages.getQuestionIcon()
                        ) == Messages.YES
                    }
                    if (!confirmed) return@invokeLater
                    app.executeOnPooledThread {
                        val removed = service.removeWorktree(worktree.path, force = dirty)
                        app.invokeLater {
                            if (removed) {
                                closeWorktreeDiffs(worktree.path)
                            } else {
                                Messages.showErrorDialog(
                                    project,
                                    "Failed to remove worktree \"${worktree.name}\". See idea.log for details.",
                                    "Remove Worktree"
                                )
                            }
                            reloadWorktrees()
                        }
                    }
                }
            }
        }

        // Opens the worktree directory as a project in a new IDE window.
        fun openInIde(worktree: WorktreeInfo) {
            ProjectUtil.openOrImport(File(worktree.path).toPath(), project, true)
        }

        worktreeList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = worktreeList.selectedValue
                openButton.isEnabled = selected != null
                deleteButton.isEnabled = selected != null
                selected?.let { loadFilesFor(it) }
            }
        }
        // Open a file's diff on double-click only; single clicks just select.
        filesList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val worktree = worktreeList.selectedValue
                    val relativePath = filesList.selectedValue
                    if (worktree != null && relativePath != null) openSingleDiff(worktree, relativePath)
                }
            }
        })
        reviewAllButton.addActionListener {
            val worktree = worktreeList.selectedValue ?: return@addActionListener
            val files = (0 until filesModel.size()).map { filesModel.getElementAt(it) }
            reviewAll(worktree, files)
        }
        refreshButton.addActionListener { reloadWorktrees() }
        deleteButton.addActionListener {
            worktreeList.selectedValue?.let { deleteWorktree(it) }
        }
        openButton.addActionListener {
            worktreeList.selectedValue?.let { openInIde(it) }
        }

        // ----- Layout -----
        // Title + worktree-scoped actions sit on top of the worktree list, so
        // it is clear that Open/Delete act on the selected worktree.
        val titleLabel = JBLabel("Active Repository Worktrees").apply {
            font = font.deriveFont(Font.BOLD, 13f)
            border = JBUI.Borders.emptyBottom(3)
        }
        val actionToolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(refreshButton)
            add(openButton)
            add(deleteButton)
        }
        val header = JPanel(BorderLayout()).apply {
            add(titleLabel, BorderLayout.NORTH)
            add(actionToolbar, BorderLayout.CENTER)
            border = JBUI.Borders.emptyBottom(5)
        }

        val worktreePanel = JPanel(BorderLayout()).apply {
            add(JBScrollPane(worktreeList), BorderLayout.CENTER)
        }

        // "Changed files" label with the Review all button directly beneath it.
        val filesHeader = JPanel(BorderLayout()).apply {
            add(JBLabel("Changed files (double-click to diff)"), BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply { add(reviewAllButton) }, BorderLayout.CENTER)
        }
        val filesPanel = JPanel(BorderLayout()).apply {
            add(filesHeader, BorderLayout.NORTH)
            add(JBScrollPane(filesList), BorderLayout.CENTER)
        }
        val splitter = JBSplitter(true, 0.5f).apply {
            firstComponent = worktreePanel
            secondComponent = filesPanel
        }

        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5)
            add(header, BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
        }

        reloadWorktrees()

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
