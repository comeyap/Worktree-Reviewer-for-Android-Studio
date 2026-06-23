# AI Worktree Reviewer

![Build](https://github.com/comeyap/AI-Worktree-Reviewer-for-AndroidStudio-IntelliJ/actions/workflows/build-plugin.yml/badge.svg)

> A JetBrains IDE plugin for reviewing changes across multiple Git worktrees
> without leaving your editor — built for the era of AI coding agents that work
> several branches in parallel.

<p align="center">
  <img src="screenshots/marketplace.png"
       alt="The Active Worktrees panel beside a side-by-side HEAD ↔ working-tree diff"
       width="900">
</p>

## What it is

AI Worktree Reviewer adds an **Active Worktrees** tool window to IntelliJ IDEA
and Android Studio. When a repository has several Git worktrees, it lists them
in one place and lets you review each worktree's changes with a side-by-side
diff — without switching projects or running git by hand.

## Why

AI coding agents (Claude Code and friends) make it common to run several work
branches at once via `git worktree`. Hopping between worktrees with `cd` or
separate IDE windows just to answer "what did this branch change?" is tedious.

This plugin keeps a tight review loop — **worktree list → changed files →
diff** — in a single panel, so you can review parallel work at a glance.

## Features

| Feature | Description |
|---------|-------------|
| **Active Worktrees tool window** | Lists active worktrees (`git worktree list`) in the right dock |
| **Change badges** | Shows `+insertions -deletions` per worktree (`git diff HEAD --shortstat`) |
| **Changed-file browsing** | Selecting a worktree lists its uncommitted changes (`git diff HEAD`), refreshed on every click |
| **Single-file diff** | Double-click a file to diff its committed (`HEAD`) version against the working tree |
| **Review all** | Pages through every changed file in a single, reused diff tab |
| **Open in IDE** | Opens the selected worktree as a project in a new window |
| **Delete** | Removes the selected worktree after confirmation (`git worktree remove`) and closes its open diff |
| **Refresh** | Reloads the worktree list and stats on demand |
| **Shortcut** | `Alt+Shift+W` toggles the tool window (also added to the Git menu) |

> **What's compared:** each diff is the file's committed version (`HEAD`) versus the
> current working tree — i.e. the worktree's **uncommitted** changes only. Changes
> already committed on the branch are not shown.

> All git calls run on a background thread, so the IDE UI never freezes.

## Requirements

- **IntelliJ IDEA** or **Android Studio**, build `2024.1` or newer (since-build `241`, no upper bound)
- The bundled **Git4Idea** plugin (enabled by default)

## Installation

### Option A — Install the release ZIP (recommended)

1. Download `aiworktreereviewer-*.zip` from the
   [Releases](https://github.com/comeyap/AI-Worktree-Reviewer-for-AndroidStudio-IntelliJ/releases) page.
2. In your IDE, open **Settings/Preferences → Plugins**.
3. Click the gear icon (⚙️) → **Install Plugin from Disk…** and select the downloaded `.zip`.
4. Restart the IDE. The **Active Worktrees** tool window appears in the right dock.

### Option B — Build from source

```bash
cd plugin
./gradlew buildPlugin      # → plugin/build/distributions/aiworktreereviewer-<version>.zip
```

Then install the produced ZIP via Option A, step 3. To launch a sandbox IDE with
the plugin preinstalled:

```bash
cd plugin
./gradlew runIde
```

> The build uses the Gradle wrapper (`9.5.1`) and JDK 17, with IntelliJ Platform
> Gradle Plugin `2.16.0`, Kotlin `1.9.24`, and IntelliJ Platform `2024.2`.

## Usage

1. Open a project that has Git worktrees (e.g. `git worktree add ../feature-x -b feature-x`).
2. Open **Active Worktrees** with `Alt+Shift+W`, or from the right dock.
3. Select a worktree to see its changed files, then **double-click a file** to diff it.
4. Use **Review all** to page through every change, or **Open in IDE / Delete** to manage the worktree.

## License

Apache-2.0
