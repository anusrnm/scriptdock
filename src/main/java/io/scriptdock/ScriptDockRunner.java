package io.scriptdock;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Execution boundary used by {@link ScriptDockFrame}. Applications can supply an implementation
 * that exposes host objects as script bindings without coupling ScriptDock to that application.
 */
public interface ScriptDockRunner {
  interface Listener {
    /**
     * Receives incremental output (for example {@code println} text) while the script is still
     * running. Called on the Swing event dispatch thread. The default implementation ignores it.
     */
    default void output(String text) {}

    /**
     * Called on the event dispatch thread when the script completes. {@code output} contains only
     * text that was not already delivered through {@link #output(String)}.
     */
    void completed(String output);

    void failed(String message, int lineNumber);
  }

  boolean isRunning();

  void run(String script, String engineName, Listener listener);

  void stop();

  /**
   * Returns the current script bindings for inspection. Custom runners may override this method;
   * the default implementation exposes no bindings.
   */
  default Map<String, Object> getBindings() {
    return Map.of();
  }

  /**
   * Sets extra JAR files and class directories available to subsequent runs. Implementations that
   * cannot extend the classpath may ignore this; the default does nothing.
   */
  default void setClasspath(List<File> entries) {}
}
