# ScriptDock

ScriptDock is a small embeddable Swing script console for Java 17 applications. It provides
multiple editor tabs, per-tab dirty tracking and file handling, Find/Replace, line numbers,
comment/uncomment line and block actions, Run/Stop controls, output capture, error-line
highlighting, brace-based code folding, and JSR-223 engine selection.

The library has no application-specific APIs. A host integrates by implementing
`ScriptDockRunner`; standalone use is provided by `Jsr223ScriptRunner`.

## Coordinates

```xml
<dependency>
  <groupId>io.scriptdock</groupId>
  <artifactId>scriptdock</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Install the snapshot into the local Maven repository:

```powershell
mvn clean install
```

## Run standalone

Package the application, then run the executable JAR from `target` so its manifest-relative
runtime dependencies resolve:

```powershell
mvn clean package
Set-Location target
java -jar scriptdock-1.0.0-SNAPSHOT.jar
```

Groovy is included as the default JSR-223 engine.

## Embed ScriptDock

The simplest embedding uses the default runner. Bindings are supplied for every run, so hosts can
expose current state instead of a stale snapshot:

```java
import io.scriptdock.Jsr223ScriptRunner;
import io.scriptdock.ScriptDockFrame;
import io.scriptdock.ScriptDockOptions;
import java.util.Map;
import javax.swing.SwingUtilities;

public final class EmbedExample {
  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
          Jsr223ScriptRunner runner =
              new Jsr223ScriptRunner(
                  () -> Map.of(
                      "applicationName", "My Application",
                      "currentUser", System.getProperty("user.name")));

          ScriptDockFrame frame =
              new ScriptDockFrame(
                  null,
                  runner,
                  ScriptDockOptions.builder()
                      .title("Application Scripts")
                      .exitOnClose(true)
                      .build());
          frame.setVisible(true);
        });
  }
}
```

The complete sample is in
`src/main/java/io/scriptdock`.
Choose **View > Bound Variables** to inspect the current binding names and values. The view asks
the runner for a fresh snapshot whenever it opens.
Choose **View > Horizontal Split** to place the editor and output side-by-side; uncheck it to
return to the stacked layout. The orientation and each layout's divider position are remembered
when preferences are enabled.
Use **Edit > Comment Lines** / **Uncomment Lines** or the editor context menu to comment the
current line or every line in the selection. Shortcuts are `Ctrl+/` and `Ctrl+Shift+/`.
Multi-line brace-delimited blocks can be collapsed from the fold markers in the line-number
gutter. Folding ignores braces inside strings and comments, supports nested blocks, and never
changes the script text. Embedded hosts can also call `ScriptEditor.toggleFoldAtLine(...)`,
`expandAllFolds()`, or `collapseAllFolds()`.

## Persistent preferences

Standalone ScriptDock remembers user preferences between launches. Embedded hosts opt in with a
namespace unique to their application, so separate hosts do not share settings:

```java
ScriptDockOptions.builder()
    .title("Application Scripts")
    .persistPreferences("my-application")
    .build();
```

When enabled, ScriptDock stores the following with the Java Preferences API:

- editor font (View > Font..., `Ctrl+=` / `Ctrl+-` to resize, `Ctrl+0` to reset)
- last directory used to open or save a file (Open starts from the active script's folder, then
  the first classpath folder, before falling back to the remembered directory)
- selected script engine, when it is still installed
- window size, position, and maximized state
- editor/output split orientation and divider positions (View > Horizontal Split)
- open tabs for saved files, in order, and the selected tab
- script classpath (Run > Classpath...) — this one is saved even without `persistPreferences`;
  see [Script classpath](#script-classpath)

Missing or unreadable files are skipped when tabs are restored. Unsaved script contents, output,
and run status are never persisted.

With persistence enabled, `editorFont(...)` and `initialDirectory(...)` are first-run defaults;
the user's stored choices take precedence, and `Reset Font` returns to the configured font.
`directoryListener(...)` is still notified whenever ScriptDock changes directory. Without
`persistPreferences(...)`, these options behave exactly as before and nothing is stored.

## Script classpath

Run > Classpath... adds JAR files and class folders that scripts can use, searched in the listed
order and shared by all tabs. JSR-223 engines packaged in those JARs also appear in the engine
selector. Entries that no longer exist are marked as missing and skipped when scripts run.

The classpath is always remembered, so embedding applications need no changes to use it. When
`persistPreferences(...)` is set it is stored in that namespace; otherwise it is stored per window
title, so hosts with different titles keep separate classpaths.

`Jsr223ScriptRunner` creates a fresh class loader for each run and closes it afterwards, so
updated JARs are picked up on the next run and are not left locked. Custom `ScriptDockRunner`
implementations receive the list through the optional `setClasspath(List<File>)` method; the
default implementation ignores it.

Classpath entries run with the same privileges as scripts. Only add trusted code.

## Custom execution

Implement `ScriptDockRunner` when script execution needs application lifecycle behavior,
special bindings, remote services, or custom error mapping. `run` is expected to return
immediately and report completion through its listener on the Swing Event Dispatch Thread.
ScriptDock captures the selected tab before invoking the runner, so switching tabs during a run
does not change the script or error location associated with that run.

To show output while a script is still running, call the listener's optional `output(String)`
method on the Event Dispatch Thread as text is produced. In that case, pass only output that
was not already streamed to `completed`.

## Output and stopping scripts

`Jsr223ScriptRunner` runs each script on a dedicated daemon thread and streams `print`/`println`
output to the output area as it is written. **Stop** interrupts the script thread. Groovy
scripts are compiled with `ThreadInterrupt` checks, so loops and closures stop at the next
check. If a script is still running two seconds after Stop, the runner tries to force the
thread to stop. Java 20 and later do not support forced thread termination, so on those
versions a script that ignores interrupts in another engine (or blocks in uninterruptible
native I/O) may keep running in the background.

To include variables in **View > Bound Variables**, override `getBindings()` and return the
current name/value map. Existing custom runners need no changes; the default implementation
returns an empty map.

Scripts execute with the privileges of the hosting process. Only run trusted scripts.
