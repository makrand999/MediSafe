# Muse Code Prompt Template

When orchestrating Muse Code (`muse --yolo`), construct the prompt using this structured format to ensure maximum precision and zero ambiguity.

---

```markdown
# TASK: [Clear, concise title of the task]

## 1. OBJECTIVE & INTENT
[Detailed breakdown of what needs to be achieved. Clarify user's high-level goal into concrete software engineering tasks. Explicitly call out edge cases and requirements.]

## 2. RELEVANT REPOSITORY CONTEXT
- **Target Files to Modify**:
  - `path/to/file1.kt`: [Specific responsibilities / lines to change]
  - `path/to/file2.kt`: [Specific responsibilities / lines to change]
- **Reference Files to Inspect**:
  - `path/to/reference.kt`: [Context on models, contracts, or utility functions]
- **Architectural Patterns & Conventions**:
  - [e.g., MVVM, Clean Architecture, Repository Pattern, Coroutine Dispatchers, Compose State]
  - [e.g., Dependency Injection via Hilt / Koin]

## 3. IMPLEMENTATION INSTRUCTIONS
1. [Step 1: Concrete change]
2. [Step 2: Concrete change]
3. [Step 3: Concrete change]

## 4. CONSTRAINTS & CODE QUALITY
- Follow existing codebase style, naming conventions, and package structures.
- Preserve existing comments, docstrings, and unrelated functionality.
- Do not introduce breaking API changes unless explicitly requested.
- Handle error states, nullability, and exceptions gracefully.

## 5. VERIFICATION
- Verify changes by running the appropriate compile/test command (e.g. `./gradlew compileDebugKotlin` or project-specific test).
- Check git diff before finishing to ensure only intended changes were made.
```
