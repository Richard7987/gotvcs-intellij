# Got — IntelliJ VCS Integration for Game of Trees

Native version control support for [Game of Trees](https://gameoftrees.org/)
(`got`) in IntelliJ-based IDEs: the same file status, diff, commit, history,
and update/push workflow you get with Git, backed by the `got` CLI.

## Features

- Detects `.got/` work trees as a `got` VCS root automatically
- File status in the Commit tool window (`got status`)
- Native diff and modified-lines gutter (`got cat` against the base commit)
- Commit and rollback from the Commit tool window (`got commit`, `got revert`)
- File history (`got log`) and Update Project (`got update` / `got fetch`)
- A **Send** action (`got send`), bound to `Ctrl+Shift+K` by default
- A Settings panel (*Settings > Version Control > got*) to override the
  `got` binary path and `SSH_AUTH_SOCK`, for setups where auto-detection
  doesn't apply

## How it works

There's no daemon and no cached repository model: every action shells out to
the `got` binary for the relevant work tree and adapts its output to the
corresponding IntelliJ Platform VCS API.

| IntelliJ feature   | got command                       |
|--------------------|-----------------------------------|
| VCS root detection | presence of a `.got/` directory   |
| File status        | `got status`                      |
| Diff / gutter      | `got cat -c :base`                |
| Commit             | `got commit -m <message> <paths>` |
| Rollback           | `got revert -R <paths>`           |
| History            | `got log`                         |
| Update Project     | `got fetch` + `got update`        |
| Send               | `got send`                        |

Source is organized by responsibility under `dev.nezzontli.gotvcs`:

- `cli` — the single wrapper around `got` invocations (`GotCommandLineWrapper`)
- `changes`, `checkin`, `history`, `update`, `roots` — one package per VCS
  extension point implemented
- `settings` — the persisted configuration and its Settings panel
- `actions` — the `Send` menu action

## Requirements

- IntelliJ IDEA (or another IntelliJ Platform IDE) 2026.1 or newer
- The `got` binary, either on `PATH` or configured under
  *Settings > Version Control > got*

## Building

```
./gradlew buildPlugin
```

The resulting ZIP is written to `build/distributions/` and can be installed
via *Settings > Plugins > ⚙ > Install Plugin from Disk*.

By default, this downloads a matching IntelliJ IDEA build to compile against.
To use a local installation instead (faster, no download), set in
`~/.gradle/gradle.properties` (not part of this repo):

```properties
ideLocalPath=/path/to/your/IntelliJ/installation
```

## Contributing

Issues and pull requests are welcome. Before submitting a change:

```
./gradlew buildPlugin verifyPlugin
```

`verifyPlugin` checks compatibility against the IDE versions declared by
`sinceBuild` in `build.gradle.kts` and flags deprecated API usage.

## Releasing

Pushing a `v*` tag triggers `.github/workflows/release.yml`, which builds,
verifies, creates a GitHub Release with the plugin ZIP attached, and — once
a `PUBLISH_TOKEN` secret is configured — publishes to the JetBrains
Marketplace.

## License

[GPLv3](LICENSE).
