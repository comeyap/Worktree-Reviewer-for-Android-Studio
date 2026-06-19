export interface KotlinFile {
  name: string;
  path: string;
  language: string;
  content: string;
  description: string;
}

export const KOTLIN_PLUGIN_FILES: KotlinFile[] = [
  {
    name: 'build.gradle.kts',
    path: 'build.gradle.kts',
    language: 'kotlin',
    description: 'Gradle configuration file for compiling the Android Studio / IntelliJ Plugin.',
    content: `plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.16.1"
}

group = "com.github.developer"
version = "1.0.0"

repositories {
    mavenCentral()
}

// Configure IntelliJ platform plugin for Android Studio compatibility
intellij {
    // 1. Standard targeting: Match the IntelliJ platform version your Android Studio is built on.
    // e.g., Android Studio Koala (2024.1.1) is based on IntelliJ 2024.1, Ladybug (2024.2.1) is 2024.2.
    version.set("2024.1") 
    type.set("IC")        // Community Edition (fully compatible with Android Studio)
    plugins.set(listOf("git4idea", "platform-images"))

    // 2. OR development targeting: point directly to your local Android Studio directory:
    // localPath.set("/Applications/Android Studio.app") // On macOS
    // localPath.set("C:\\Program Files\\Android\\Android Studio") // On Windows
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("242.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
`
  },
  {
    name: 'plugin.xml',
    path: 'src/main/resources/META-INF/plugin.xml',
    language: 'xml',
    description: 'Declares plugin actions, tool windows, extensions, and standard parameters.',
    content: `<!-- SPDX-License-Identifier: Apache-2.0 -->
<idea-plugin>
    <id>com.github.developer.aiworktreereviewer</id>
    <name>AI Worktree Reviewer</name>
    <vendor email="support@example.com" url="https://github.com/developer/aiworktreereviewer">Git Worktree Reviewer Team</vendor>

    <description><![CDATA[
    <h3>AI Worktree Reviewer for Android Studio / IntelliJ</h3>
    <p>A lightweight review companion plugin designed strictly for Git Worktree multi-session repositories.</p>
    <ul>
        <li>Quickly toggle active worktrees from a central dock</li>
        <li>Review diff modifications with zero workspace context switches</li>
        <li>Perform seamless reviews with sequential file traversal (Next/Previous hooks)</li>
    </ul>
    ]]></description>

    <!-- Depends on platform and git plugins -->
    <depends>com.intellij.modules.platform</depends>
    <depends>Git4Idea</depends>

    <extensions defaultExtensionNs="com.intellij">
        <!-- 1. Active Worktrees Tool Window Registration -->
        <toolWindow id="Active Worktrees"
                    anchor="right"
                    secondary="false"
                    icon="AllIcons.Actions.ListFiles"
                    factoryClass="com.example.worktree.ui.ActiveWorktreesToolWindowFactory" />

        <!-- 2. Project service for process execution -->
        <projectService serviceImplementation="com.example.worktree.service.WorktreeService" />
    </extensions>

    <actions>
        <!-- Custom action to focus/toggle Active Worktrees panel -->
        <action id="com.example.worktree.actions.ToggleWorktreesAction"
                class="com.example.worktree.actions.ToggleWorktreesAction"
                text="Toggle Active Worktrees"
                description="Toggles the Git Worktrees sidebar panel">
            <!-- Alt + Shift + W Shortcut -->
            <keyboard-shortcut first-keystroke="alt shift W" keymap="$default"/>
            <add-to-group group-id="Git.Menu" anchor="last"/>
        </action>
    </actions>
</idea-plugin>
`
  },
  {
    name: 'ActiveWorktreesToolWindowFactory.kt',
    path: 'src/main/kotlin/com/example/worktree/ui/ActiveWorktreesToolWindowFactory.kt',
    language: 'kotlin',
    description: 'Binds the side panel interface, handles active events and list presentation.',
    content: `package com.example.worktree.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.example.worktree.service.WorktreeService
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLabel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.border.EmptyBorder

class ActiveWorktreesToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val worktreeService = project.getService(WorktreeService::class.java)
        
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(5)

        // Title and Refresh Control
        val headerPanel = JPanel(BorderLayout())
        val titleLabel = JBLabel("Active Repository Worktrees").apply {
            font = font.deriveFont(java.awt.Font.BOLD, 13f)
        }
        headerPanel.add(titleLabel, BorderLayout.WEST)

        val refreshButton = JButton("Refresh").apply {
            addActionListener {
                // Service-controlled reload
                SwingUtilities.invokeLater {
                    // Logic to reload JBList model
                }
            }
        }
        val rightHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0))
        rightHeader.add(refreshButton)
        headerPanel.add(rightHeader, BorderLayout.EAST)
        headerPanel.border = JBUI.Borders.emptyBottom(5)
        panel.add(headerPanel, BorderLayout.NORTH)

        // Worktree list component
        val mockModel = DefaultListModel<String>().apply {
            // Retrieve listed paths outputting from git CLI
            worktreeService.getWorktrees().forEach {
                addElement(it.name + " (" + it.branch + ")")
            }
        }

        val scrollList = JBList(mockModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = SimpleListCellRenderer.create { label, value, _ ->
                label.text = value
                label.icon = com.intellij.icons.AllIcons.Nodes.Folder
            }
            addListSelectionListener { event ->
                if (!event.valueIsAdjusting) {
                    // Call diff trigger logic on worktreeService
                }
            }
        }

        panel.add(JBScrollPane(scrollList), BorderLayout.CENTER)

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
`
  },
  {
    name: 'WorktreeService.kt',
    path: 'src/main/kotlin/com/example/worktree/service/WorktreeService.kt',
    language: 'kotlin',
    description: 'Project service dealing directly with git shell processes to fetch active branches and stats.',
    content: `package com.example.worktree.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

data class WorktreeInfo(
    val path: String,
    val sha: String,
    val branch: String,
    val name: String
)

@Service(Service.Level.PROJECT)
class WorktreeService(private val project: Project) {

    fun getWorktrees(): List<WorktreeInfo> {
        val rootPath = project.basePath ?: return emptyList()
        val result = mutableListOf<WorktreeInfo>()
        
        try {
            // Execution of "git worktree list --porcelain"
            val process = ProcessBuilder("git", "worktree", "list", "--porcelain")
                .directory(File(rootPath))
                .start()
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String? = reader.readLine()
            
            var currentPath = ""
            var currentSha = ""
            var currentBranch = ""
            
            while (line != null) {
                if (line.startsWith("worktree ")) {
                    if (currentPath.isNotEmpty()) {
                        result.add(createWorktreeObj(currentPath, currentSha, currentBranch))
                    }
                    currentPath = line.substring("worktree ".length)
                    currentSha = ""
                    currentBranch = ""
                } else if (line.startsWith("HEAD ")) {
                    currentSha = line.substring("HEAD ".length)
                } else if (line.startsWith("branch ")) {
                    currentBranch = line.substring("branch ".length).removePrefix("refs/heads/")
                }
                line = reader.readLine()
            }
            if (currentPath.isNotEmpty()) {
                result.add(createWorktreeObj(currentPath, currentSha, currentBranch))
            }
            
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return result
    }

    private fun createWorktreeObj(path: String, sha: String, branch: String): WorktreeInfo {
        val name = File(path).name
        return WorktreeInfo(path, sha, branch, name)
    }

    fun getBranchName(worktreePath: String): String {
        return runCommand(worktreePath, listOf("git", "branch", "--show-current"))
    }

    fun getDiffShortstat(worktreePath: String): String {
        return runCommand(worktreePath, listOf("git", "diff", "--shortstat"))
    }

    fun getModifiedFiles(worktreePath: String): List<String> {
        val output = runCommand(worktreePath, listOf("git", "diff", "--name-only"))
        return output.split("\\n").filter { it.isNotBlank() }
    }

    fun removeWorktree(worktreePath: String) {
        val rootPath = project.basePath ?: return
        try {
            val process = ProcessBuilder("git", "worktree", "remove", worktreePath)
                .directory(File(rootPath))
                .start()
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun runCommand(workingDir: String, command: List<String>): String {
        return try {
            val process = ProcessBuilder(command)
                .directory(File(workingDir))
                .start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText().trim()
            process.waitFor()
            output
        } catch (e: Exception) {
            ""
        }
    }
}
`
  },
  {
    name: 'DiffReviewAction.kt',
    path: 'src/main/kotlin/com/example/worktree/actions/DiffReviewAction.kt',
    language: 'kotlin',
    description: 'Action triggering Android Studio native side-by-side Diff window on any target file in a separate worktree.',
    content: `package com.example.worktree.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.contents.FileContentImpl
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class DiffReviewAction(
    private val project: Project,
    private val mainOriginalFile: File,
    private val worktreeModifiedFile: File,
    private val relativePath: String
) : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val originalVf: VirtualFile? = LocalFileSystem.getInstance()
            .refreshAndFindFileByIoFile(mainOriginalFile)
        val modifiedVf: VirtualFile? = LocalFileSystem.getInstance()
            .refreshAndFindFileByIoFile(worktreeModifiedFile)

        if (originalVf == null || modifiedVf == null) {
            // Cannot open if files do not exist or are deleted
            return
        }

        val originalContent = FileContentImpl(project, originalVf)
        val modifiedContent = FileContentImpl(project, modifiedVf)

        // Instantiates side-by-side editor with left: Main branch, right: Worktree branch
        val diffRequest = SimpleDiffRequest(
            "Review Modification - $relativePath",
            originalContent,
            modifiedContent,
            "Main (Repository Home)",
            "Worktree: " + worktreeModifiedFile.parentFile.name
        )

        DiffManager.getInstance().showDiff(project, diffRequest)
    }
}
`
  }
];
