# Copilot instructions for ScriptDock

## Project snapshot

ScriptDock is a small Java 25 Swing library for embedding or running a tabbed JSR-223 script console. The main runtime entrypoints are:

- `io.scriptdock.sample.EmbedExample` – sample embedding of the library into a host app
- `io.scriptdock.ScriptDock` – standalone launcher
- `io.scriptdock.ScriptDockFrame` – main Swing window, tabs, toolbar, output panel, and lifecycle actions
- `io.scriptdock.ScriptEditor` – source editor with syntax highlighting, undo/redo, current-line highlighting, and error-line highlighting
- `io.scriptdock.ScriptDockRunner` – host abstraction for script execution
- `io.scriptdock.Jsr223ScriptRunner` – default implementation using JSR-223 engines and Groovy
- `io.scriptdock.ScriptDockOptions` – configuration object for embedded usage

## Build, test, and package commands

This repo is a Maven project (`pom.xml`). Use the Maven wrapper if it is present; otherwise use `mvn` directly.

- Run the full test suite:
  - `mvn test`
- Run a single test class:
  - `mvn -Dtest=Jsr223ScriptRunnerTest test`
  - `mvn -Dtest=ScriptEditorTest test`
- Compile only:
  - `mvn -DskipTests compile`
- Create the runnable JAR and runtime dependencies:
  - `mvn clean package`
- Run the standalone app from the built output:
  - `Set-Location target`
  - `java -jar scriptdock-1.0.0-SNAPSHOT.jar`

There is no dedicated lint task configured in `pom.xml`; the project relies on compilation and tests as the primary validation path.

## Architecture and runtime model

The key architectural pattern is a host-neutral UI layer with a pluggable execution boundary:

- `ScriptDockFrame` owns the window and all editor state. It creates tabs, saves/loads files, manages the output area, and routes run/stop requests through the configured `ScriptDockRunner`.
- `ScriptEditor` is not tied to any host app. It handles text editing, syntax coloring, line numbers, current-line highlight, and error-line highlight. It is intentionally reusable in a host Swing application.
- `ScriptDockRunner` is the integration seam. Any application that needs script execution with custom bindings or lifecycle behavior should implement this interface rather than coupling display code to application state.
- `Jsr223ScriptRunner` provides the default implementation. It uses `ScriptEngineManager`, prefers Groovy (`DEFAULT_ENGINE = "groovy"`), exposes a `bindingsSupplier`, and evaluates scripts asynchronously while reporting the result on the Swing EDT via `SwingWorker`.
- `ScriptDockOptions` centralizes window configuration like title, default font, initial directory, and exit-on-close behavior.
- `../src/main/java/io/scriptdock` is the reference host embedding example; use it as the pattern for integrating the library into another app.

## Project-specific conventions

- Java 25 is required (`maven.compiler.release` in `pom.xml`). Do not introduce newer language features unless specifically needed.
- Default engine behavior is Groovy-first. `Jsr223ScriptRunner.availableEngineNames()` deliberately reorders the engine list so Groovy appears first even when other JSR-223 engines are installed.
- Script execution is intentionally blank-safe: a blank script is rejected with `"There is nothing to run"` before evaluation starts.
- Script failures map back to an editor line number when possible (`ScriptException.getLineNumber()` or script-like stack entries), and `ScriptEditor.highlightErrorLine(...)` is used to highlight the failing line in the editor.
- The selected tab is the script context for each run. ScriptDock captures the tab before invoking the runner so a tab change during execution does not alter the script/error location associated with that run.
- Use the Swing EDT rules that already exist in the project: UI mutations happen in the frame/editor code, and asynchronous execution is handled through `SwingWorker` rather than background threads directly.
- Keep host-specific logic out of the Swing widgets. The library is intentionally app-neutral; if custom bindings or lifecycle behavior are needed, add them via a custom `ScriptDockRunner` implementation.

## Useful repository patterns

- The README is the source of truth for the public embedding and standalone usage examples. Keep changes aligned with the documented integration flow.
- Tests use JUnit 5 and are in `../src/test/java/io/scriptdock`.
- Most Swing-related behaviors are exercised through `SwingUtilities.invokeAndWait(...)` in tests to avoid EDT timing issues.
- The sample embedding code passes a binding supplier to `Jsr223ScriptRunner`, which is the recommended integration pattern for exposing app state without stale snapshots.
