// Generates src/data/kotlinCode.ts from the real plugin/ sources.
//
// The plugin/ Gradle project is the single source of truth for the template
// files shown in the web app's "Plugin Kotlin Code" tab. Editing kotlinCode.ts
// by hand would let it drift from the actual plugin; instead run:
//
//   npm run generate:plugin-code
//
// (this also runs automatically before `npm run build` via the prebuild hook).

import { readFileSync, writeFileSync } from 'node:fs';
import { join, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const pluginDir = join(root, 'plugin');
const outFile = join(root, 'src', 'data', 'kotlinCode.ts');

// Order matters: KotlinPluginCodeExporter defaults its selection to index [1]
// (plugin.xml). `source` is relative to plugin/ and is also the export path
// shown to the user (where to paste the file in their own plugin project).
const MANIFEST = [
  {
    source: 'build.gradle.kts',
    language: 'kotlin',
    description: 'Gradle configuration file for compiling the Android Studio / IntelliJ Plugin.',
  },
  {
    source: 'src/main/resources/META-INF/plugin.xml',
    language: 'xml',
    description: 'Declares plugin actions, tool windows, extensions, and standard parameters.',
  },
  {
    source: 'settings.gradle.kts',
    language: 'kotlin',
    description: 'Gradle settings file declaring the plugin project name.',
  },
  {
    source: 'src/main/kotlin/com/example/worktree/ui/ActiveWorktreesToolWindowFactory.kt',
    language: 'kotlin',
    description: 'Binds the side panel interface, loads the worktree list off the EDT and opens a diff on selection.',
  },
  {
    source: 'src/main/kotlin/com/example/worktree/service/WorktreeService.kt',
    language: 'kotlin',
    description: 'Project service dealing directly with git shell processes to fetch active branches and stats.',
  },
  {
    source: 'src/main/kotlin/com/example/worktree/actions/DiffReviewAction.kt',
    language: 'kotlin',
    description: 'Opens Android Studio’s native side-by-side diff window for a file in a separate worktree.',
  },
  {
    source: 'src/main/kotlin/com/example/worktree/actions/ToggleWorktreesAction.kt',
    language: 'kotlin',
    description: 'Action that shows/hides the Active Worktrees tool window (Alt+Shift+W).',
  },
];

// Escape so the file content can sit inside a JS template literal without being
// interpreted: backslashes first, then backticks, then ${ interpolation starts.
function escapeForTemplateLiteral(s) {
  return s
    .replace(/\\/g, '\\\\')
    .replace(/`/g, '\\`')
    .replace(/\$\{/g, '\\${');
}

const entries = MANIFEST.map(({ source, language, description }) => {
  const content = readFileSync(join(pluginDir, source), 'utf8');
  return {
    name: basename(source),
    path: source,
    language,
    description,
    content,
  };
});

const header = `// AUTO-GENERATED FILE — DO NOT EDIT BY HAND.
// Source of truth: the plugin/ Gradle project.
// Regenerate with: npm run generate:plugin-code  (see scripts/generate-kotlin-code.mjs)

export interface KotlinFile {
  name: string;
  path: string;
  language: string;
  content: string;
  description: string;
}

`;

const body =
  'export const KOTLIN_PLUGIN_FILES: KotlinFile[] = [\n' +
  entries
    .map(
      (f) =>
        '  {\n' +
        `    name: ${JSON.stringify(f.name)},\n` +
        `    path: ${JSON.stringify(f.path)},\n` +
        `    language: ${JSON.stringify(f.language)},\n` +
        `    description: ${JSON.stringify(f.description)},\n` +
        '    content: `' +
        escapeForTemplateLiteral(f.content) +
        '`,\n' +
        '  }'
    )
    .join(',\n') +
  '\n];\n';

writeFileSync(outFile, header + body, 'utf8');
console.log(`Generated ${outFile} from ${entries.length} plugin source files.`);
