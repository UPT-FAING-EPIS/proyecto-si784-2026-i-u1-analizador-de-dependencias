---
name: depanalyzer-audit
description: Audit, explain, prioritize, and safely update project dependencies with DepAnalyzer. Use for vulnerability scans, CVE reviews, outdated dependency checks, Maven or Gradle projects, package.json, pyproject.toml, requirements.txt, dependency remediation plans, or troubleshooting the DepAnalyzer CLI.
---

# DepAnalyzer Audit

Use the repository's DepAnalyzer CLI as the source of truth. Support Maven, Gradle Groovy,
Gradle Kotlin, npm, Poetry/PEP 621, and requirements.txt projects.

## Workflow

1. Locate the executable in this order:
   - `depanalyzer` available on `PATH`.
   - `build/install/depanalyzer/bin/depanalyzer` or its Windows `.bat` equivalent.
   - Build it with `./gradlew installDist` only when the user asks to install, build, or repair it.
2. Detect the project root from the requested path. Do not guess a different repository when the
   path contains no supported build file.
3. Run a machine-readable audit:

   ```text
   depanalyzer analyze <path> --output json --output-file - --quiet
   ```

4. Parse stdout as JSON. Treat stderr as diagnostics, never as report data.
5. Summarize critical and high vulnerabilities first, then medium/low findings and outdated
   dependencies. Include coordinate, current version, recommended version when available, CVE,
   CVSS, source, and whether the dependency is direct or transitive.
6. Explain uncertainty when tokens are absent, a provider fails, or a transitive path is incomplete.

Read [references/cli-contract.md](references/cli-contract.md) when constructing commands or parsing
the JSON contract.

## Update Workflow

Always separate planning from modification.

1. Generate a plan without changing files:

   ```text
   depanalyzer update <path> --plan --output-file -
   ```

2. Present the suggested IDs, dependency changes, reasons, and affected build file.
3. Ask for explicit approval of specific suggestion IDs.
4. Apply only approved IDs by repeating `--apply-id`:

   ```text
   depanalyzer update <path> --apply-id <id> --apply-id <id>
   ```

5. Report the backup path and resulting changes. Run relevant tests after modification when the
   project provides them.

Never infer approval from a request to inspect, audit, explain, review, plan, or simulate. Never use
an interactive select-all action on the user's behalf.

## Failure Handling

- Exit `1`: report that critical vulnerabilities were found when `--fail-on-critical` was requested.
- Exit `2`: report an analysis or environment failure and preserve stderr details.
- Invalid JSON: report a CLI compatibility problem; do not scrape human console output.
- Missing token: explain reduced provider limits and continue when the CLI can degrade gracefully.
- Stale update ID: regenerate the plan instead of forcing an update.

Do not print or persist `OSS_INDEX_TOKEN` or `NVD_API_KEY`.
