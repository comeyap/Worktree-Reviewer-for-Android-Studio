package com.example.worktree.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.example.worktree.service.WorktreeService
import com.example.worktree.service.WorktreeInfo
import com.example.worktree.service.DiffStats
import com.example.worktree.actions.DiffReviewAction
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
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

        // ----- Changed-files list + "Review all" -----
        val filesModel = DefaultListModel<String>()
        val filesList = JBList(filesModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = SimpleListCellRenderer.create { label, value, _ ->
                if (value != null) {
                    label.text = value
                    label.icon = com.intellij.icons.AllIcons.FileTypes.Any_type
                }
            }
        }
        val reviewAllButton = JButton("Review all").apply { isEnabled = false }
        val refreshButton = JButton("Refresh")
        val deleteButton = JButton("Delete").apply { isEnabled = false }

        // ----- Behaviour -----

        fun openSingleDiff(worktree: WorktreeInfo, relativePath: String) {
            val basePath = project.basePath ?: return
            DiffReviewAction(
                project,
                File(basePath, relativePath),
                File(worktree.path, relativePath),
                relativePath
            ).showDiff()
        }

        // Opens every changed file in one diff viewer that can be paged with prev/next.
        fun reviewAll(worktree: WorktreeInfo, files: List<String>) {
            val basePath = project.basePath ?: return
            val factory = DiffContentFactory.getInstance()
            val lfs = LocalFileSystem.getInstance()
            val requests = files.mapNotNull { rel ->
                val original = lfs.refreshAndFindFileByIoFile(File(basePath, rel))
                val modified = lfs.refreshAndFindFileByIoFile(File(worktree.path, rel))
                if (original == null || modified == null) {
                    null
                } else {
                    SimpleDiffRequest(
                        rel,
                        factory.create(project, original),
                        factory.create(project, modified),
                        "Main (Repository Home)",
                        "Worktree: ${worktree.name}"
                    )
                }
            }
            if (requests.isEmpty()) return
            DiffManager.getInstance().showDiff(project, SimpleDiffRequestChain(requests), DiffDialogHints.DEFAULT)
        }

        // Loads the changed files for a worktree; also refreshes its +/- badge.
        // Called on every worktree click so the view reflects the latest git state.
        fun loadFilesFor(worktree: WorktreeInfo) {
            filesModel.clear()
            reviewAllButton.isEnabled = false
            app.executeOnPooledThread {
                val files = service.getModifiedFiles(worktree.path)
                val stats = service.getDiffStats(worktree.path)
                app.invokeLater {
                    statsByPath[worktree.path] = stats
                    worktreeList.repaint()
                    filesModel.clear()
                    files.forEach { filesModel.addElement(it) }
                    reviewAllButton.isEnabled = files.isNotEmpty()
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
                    reviewAllButton.isEnabled = false
                    refreshButton.isEnabled = true
                }
            }
        }

        // Removes the selected worktree after confirmation, then reloads the list.
        fun deleteWorktree(worktree: WorktreeInfo) {
            val choice = Messages.showYesNoDialog(
                project,
                "Remove worktree \"${worktree.name}\"?\n${worktree.path}",
                "Remove Worktree",
                Messages.getQuestionIcon()
            )
            if (choice != Messages.YES) return
            app.executeOnPooledThread {
                service.removeWorktree(worktree.path)
                app.invokeLater { reloadWorktrees() }
            }
        }

        worktreeList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = worktreeList.selectedValue
                deleteButton.isEnabled = selected != null
                selected?.let { loadFilesFor(it) }
            }
        }
        filesList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val worktree = worktreeList.selectedValue
                val relativePath = filesList.selectedValue
                if (worktree != null && relativePath != null) openSingleDiff(worktree, relativePath)
            }
        }
        reviewAllButton.addActionListener {
            val worktree = worktreeList.selectedValue ?: return@addActionListener
            val files = (0 until filesModel.size()).map { filesModel.getElementAt(it) }
            reviewAll(worktree, files)
        }
        refreshButton.addActionListener { reloadWorktrees() }
        deleteButton.addActionListener {
            worktreeList.selectedValue?.let { deleteWorktree(it) }
        }

        // ----- Layout -----
        val header = JPanel(BorderLayout()).apply {
            add(JBLabel("Active Repository Worktrees").apply { font = font.deriveFont(Font.BOLD, 13f) }, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply { add(refreshButton) }, BorderLayout.EAST)
            border = JBUI.Borders.emptyBottom(5)
        }

        val worktreeActions = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            add(deleteButton)
        }
        val worktreePanel = JPanel(BorderLayout()).apply {
            add(JBScrollPane(worktreeList), BorderLayout.CENTER)
            add(worktreeActions, BorderLayout.SOUTH)
        }
        val filesPanel = JPanel(BorderLayout()).apply {
            add(JBLabel("Changed files"), BorderLayout.NORTH)
            add(JBScrollPane(filesList), BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply { add(reviewAllButton) }, BorderLayout.SOUTH)
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
