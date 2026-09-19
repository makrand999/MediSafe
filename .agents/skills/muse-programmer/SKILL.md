---
name: muse-programmer
description: >-
  Orchestrates Muse Code (`muse --yolo`) as an autonomous programmer. Use this skill
  whenever delegating coding, refactoring, implementation, feature development, or bug
  fixing tasks to Muse, or when the user asks to run tasks through Muse Code. Antigravity
  acts as the software architect and orchestrator: analyzing the user's intent, resolving
  errors or ambiguities, enriching it with repository context and consistency guidelines,
  dispatching to `muse --yolo`, and reviewing the output.
---

# Muse Programmer Orchestration Skill

This skill establishes a dual-agent workflow where:
1. **Antigravity** acts as the **Architect & Intent Refiner**: Understanding goals, removing bugs/ambiguities from the prompt, surveying repository context, generating clear specs, and verifying code quality.
2. **Muse Code** (`muse --yolo`) acts as the **Programmer / Implementer**: Autonomous coding engine that executes file edits, refactoring, and implementations directly in the workspace without sandbox or approval roadblocks.

---

## Workflow Steps

### Step 1: Analyze & Refine Intent
When the user submits a coding request for Muse:
- **Clarify intent**: If the user's request has logical inconsistencies, typos, syntax mistakes, or missing requirements, resolve them logically or fill in sensible engineering defaults.
- **De-risk requirements**: Ensure requirements do not unintentionally break existing contracts, APIs, or database schemas unless explicitly requested.

### Step 2: Context Gathering & Architectural Grounding
Before invoking Muse, inspect the codebase to provide concrete anchors:
- Identify relevant source files, classes, models, and interfaces (e.g. using `grep_search` or `find_by_name`).
- Note existing architectural patterns (e.g. MVVM, repository patterns, coroutines, Compose components, Gradle configurations).
- Collect exact file paths, function signatures, and data models that Muse will need.

### Step 3: Synthesize Refined Prompt
Format the instruction using the structured blueprint in [prompt_template.md](./references/prompt_template.md):
- Objective & Intent summary.
- Target files to modify and reference files to inspect.
- Exact implementation steps.
- Code style and constraints (preserving existing code/comments, error handling).
- Verification steps (e.g., Gradle build commands, tests).

### Step 4: Dispatch to Muse Code

#### Automated / Headless Execution (Default):
Execute the refined prompt through the helper script:
```bash
./.agents/skills/muse-programmer/scripts/run_muse.sh -m "<Refined Prompt Markdown>"
```
Or write the prompt to a temporary file:
```bash
./.agents/skills/muse-programmer/scripts/run_muse.sh /path/to/prompt.md
```

The script runs:
`muse exec --yolo --workspace "<workspace_root>" --prompt-file "<prompt_file>"`

#### Interactive Handoff (If requested by user):
If the user prefers to run Muse interactively in their terminal with full TUI:
Provide the user with the exact command:
```bash
muse --yolo "<Refined Prompt>"
```

### Step 5: Verification & Quality Assurance
Once Muse completes:
1. **Inspect Git Diff**: Check `git status` and `git diff` to ensure all intended changes were made and no unwanted edits occurred.
2. **Run Compiler / Tests**: Run project validation (e.g. `./gradlew compileDebugSources` or appropriate test targets).
3. **Review Consistency**: Verify that imports, code style, and naming conventions match the rest of the project.
4. **Report Back**: Give the user a concise summary of Muse's changes, any verification results, and any recommended next steps.
