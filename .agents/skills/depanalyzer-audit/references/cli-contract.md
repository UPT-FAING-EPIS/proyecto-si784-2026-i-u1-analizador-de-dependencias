# CLI Contract

## Analyze

Use:

```text
depanalyzer analyze <project-path> --output json --output-file - --quiet
```

The JSON object has `schemaVersion`, `projectName`, `upToDate`, `outdated`,
`directVulnerable`, `transitiveVulnerable`, `vulnerabilityChains`, and optional
`dependencyTree`.

Direct dependencies can include `sourceLocation` with `file`, `line`, `startColumn`, and
`endColumn`. Positions are 1-based and the end column is exclusive. A missing location means the
declaration could not be mapped reliably; never invent a range.

Provider flags:

- `--oss`: require OSS Index without fallback.
- `--nvd`: require NVD without fallback; only Maven coordinates are supported.
- No provider flag: prefer OSS Index and enrich/fallback with NVD where applicable.

Environment credentials:

- `OSS_INDEX_TOKEN`
- `NVD_API_KEY`

## Update

Create a machine-readable plan:

```text
depanalyzer update <project-path> --plan --output-file -
```

Add `--only-security` to exclude ordinary outdated-version suggestions. Each suggestion has a
stable `id` derived from its complete proposed change. IDs become invalid when the plan changes.

Apply explicitly approved suggestions:

```text
depanalyzer update <project-path> --apply-id <id>
```

The CLI recalculates the plan, rejects missing/stale IDs, and creates a `.bak` file before writing.

## Exit Codes

- `0`: command completed.
- `1`: critical vulnerability policy failed.
- `2`: analysis failed.
