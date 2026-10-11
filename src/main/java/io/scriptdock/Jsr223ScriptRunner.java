package io.scriptdock;

import groovy.lang.GroovyClassLoader;
import groovy.transform.ThreadInterrupt;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.swing.SwingUtilities;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;

/** Default asynchronous JSR-223 runner used by standalone ScriptDock. */
public final class Jsr223ScriptRunner implements ScriptDockRunner {
  public static final String DEFAULT_ENGINE = "groovy";

  private final Supplier<? extends Map<String, Object>> bindingsSupplier;
  private static final long STOP_GRACE_MILLIS = 2000;

  private volatile Execution execution;
  private volatile List<File> classpath = List.of();

  public Jsr223ScriptRunner() {
    this(Collections::emptyMap);
  }

  public Jsr223ScriptRunner(Supplier<? extends Map<String, Object>> bindingsSupplier) {
    this.bindingsSupplier = Objects.requireNonNull(bindingsSupplier, "bindingsSupplier");
  }

  public static List<String> availableEngineNames() {
    return engineNames(new ScriptEngineManager());
  }

  /** Lists engines visible to scripts, including engines packaged in the given classpath entries. */
  public static List<String> availableEngineNames(List<File> classpath) {
    try (URLClassLoader loader = classLoader(classpath)) {
      return engineNames(new ScriptEngineManager(loader));
    } catch (IOException ex) {
      return availableEngineNames();
    }
  }

  private static List<String> engineNames(ScriptEngineManager manager) {
    Set<String> names = new LinkedHashSet<>();
    for (ScriptEngineFactory factory : manager.getEngineFactories()) {
      names.add(factory.getNames().isEmpty() ? factory.getEngineName() : factory.getNames().get(0));
    }
    ArrayList<String> ordered = new ArrayList<>(names);
    ordered.remove(DEFAULT_ENGINE);
    ordered.add(0, DEFAULT_ENGINE);
    return List.copyOf(ordered);
  }

  @Override
  public void setClasspath(List<File> entries) {
    classpath = entries == null ? List.of() : List.copyOf(entries);
  }

  @Override
  public Map<String, Object> getBindings() {
    Map<String, Object> bindings = bindingsSupplier.get();
    return bindings == null
        ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
  }

  @Override
  public boolean isRunning() {
    return execution != null;
  }

  /**
   * Starts the script on a dedicated thread. Printed text is streamed to {@link
   * Listener#output(String)} while the script runs; all callbacks are delivered on the Swing event
   * dispatch thread.
   */
  @Override
  public void run(String script, String engineName, Listener listener) {
    Objects.requireNonNull(listener, "listener");
    if (execution != null) {
      listener.failed("A script is already running", -1);
      return;
    }
    if (script == null || script.isBlank()) {
      listener.failed("There is nothing to run", -1);
      return;
    }
    List<File> runClasspath = classpath;
    Map<String, Object> runBindings = getBindings();
    Execution current = new Execution(listener);
    OutputWriter writer =
        new OutputWriter(
            false,
            text -> {
              if (execution == current) {
                listener.output(text);
              }
            });
    Thread thread =
        new Thread(
            () -> {
              Result result = null;
              Throwable error = null;
              try {
                result = evaluate(script, engineName, runBindings, runClasspath, writer);
              } catch (Throwable ex) {
                error = ex;
              }
              Result finalResult = result;
              Throwable finalError = error;
              SwingUtilities.invokeLater(
                  () -> {
                    if (execution != current) {
                      return;
                    }
                    execution = null;
                    if (finalError != null) {
                      listener.failed(message(finalError), findLine(finalError));
                    } else {
                      listener.completed(finalResult.printed() ? "" : finalResult.valueText());
                    }
                  });
            },
            "ScriptDock-script");
    thread.setDaemon(true);
    current.thread = thread;
    execution = current;
    thread.start();
  }

  /**
   * Stops the running script. The script thread is interrupted first; Groovy scripts are compiled
   * with interrupt checks so loops end promptly. If the thread is still alive after a grace
   * period it is forcibly stopped.
   */
  @Override
  public void stop() {
    Execution current = execution;
    if (current == null) {
      return;
    }
    execution = null;
    Thread thread = current.thread;
    thread.interrupt();
    current.listener.failed("Script execution was stopped", -1);
    Thread reaper =
        new Thread(
            () -> {
              try {
                thread.join(STOP_GRACE_MILLIS);
              } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
              }
              if (thread.isAlive()) {
                forceStop(thread);
              }
            },
            "ScriptDock-stop");
    reaper.setDaemon(true);
    reaper.start();
  }

  @SuppressWarnings({"deprecation", "removal"})
  private static void forceStop(Thread thread) {
    try {
      thread.stop();
    } catch (UnsupportedOperationException | SecurityException ex) {
      // Newer JDKs no longer support Thread.stop; the daemon thread is abandoned instead.
    }
  }

  static String evaluate(String script, String engineName, Map<String, Object> bindings)
      throws ScriptException {
    return evaluate(script, engineName, bindings, List.of());
  }

  static String evaluate(
      String script, String engineName, Map<String, Object> bindings, List<File> classpath)
      throws ScriptException {
    Result result = evaluate(script, engineName, bindings, classpath, new OutputWriter(true, null));
    return result.printed() ? result.printedText() : result.valueText();
  }

  private static Result evaluate(
      String script,
      String engineName,
      Map<String, Object> bindings,
      List<File> classpath,
      OutputWriter output)
      throws ScriptException {
    Thread thread = Thread.currentThread();
    ClassLoader previous = thread.getContextClassLoader();
    URLClassLoader loader = classLoader(classpath);
    try {
      // Groovy derives its class loader from the context loader when the engine is created.
      thread.setContextClassLoader(loader);
      return evaluate(script, engineName, bindings, loader, output);
    } finally {
      thread.setContextClassLoader(previous);
      try {
        loader.close();
      } catch (IOException ex) {
        // Releasing classpath files is best effort.
      }
    }
  }

  private static Result evaluate(
      String script,
      String engineName,
      Map<String, Object> bindings,
      ClassLoader loader,
      OutputWriter output)
      throws ScriptException {
    String name = engineName == null || engineName.isBlank() ? DEFAULT_ENGINE : engineName.trim();
    ScriptEngine engine = new ScriptEngineManager(loader).getEngineByName(name);
    if (engine == null) {
      throw new ScriptException("No script engine registered for \"" + name + "\"");
    }
    if (engine instanceof GroovyScriptEngineImpl) {
      engine = interruptibleGroovyEngine(loader);
    }
    if (bindings != null && !bindings.isEmpty()) {
      Bindings engineBindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
      engineBindings.putAll(bindings);
    }
    PrintWriter writer = new PrintWriter(output, true);
    engine.getContext().setWriter(writer);
    engine.getContext().setErrorWriter(writer);
    Object value = engine.eval(script);
    writer.flush();
    return new Result(output.hasOutput(), output.captured(), value);
  }

  /** Groovy engine whose scripts check {@link Thread#isInterrupted()} in loops and closures. */
  private static ScriptEngine interruptibleGroovyEngine(ClassLoader loader) {
    CompilerConfiguration config = new CompilerConfiguration();
    config.addCompilationCustomizers(new ASTTransformationCustomizer(ThreadInterrupt.class));
    return new GroovyScriptEngineImpl(new GroovyClassLoader(loader, config));
  }

  private static final class Execution {
    final Listener listener;
    Thread thread;

    Execution(Listener listener) {
      this.listener = listener;
    }
  }

  private record Result(boolean printed, String printedText, Object value) {
    String valueText() {
      return value == null ? "" : value.toString();
    }
  }

  /**
   * Writer that optionally captures all text and forwards chunks to the event dispatch thread,
   * coalescing writes that arrive before the previous chunk has been delivered.
   */
  private static final class OutputWriter extends Writer {
    private final StringBuilder all;
    private final Consumer<String> sink;
    private final StringBuilder pending = new StringBuilder();
    private boolean scheduled;
    private volatile boolean written;

    OutputWriter(boolean capture, Consumer<String> sink) {
      this.all = capture ? new StringBuilder() : null;
      this.sink = sink;
    }

    @Override
    public void write(char[] buffer, int offset, int length) {
      if (length <= 0) {
        return;
      }
      written = true;
      synchronized (this) {
        if (all != null) {
          all.append(buffer, offset, length);
        }
        if (sink == null) {
          return;
        }
        pending.append(buffer, offset, length);
        if (scheduled) {
          return;
        }
        scheduled = true;
      }
      SwingUtilities.invokeLater(this::deliver);
    }

    private void deliver() {
      String text;
      synchronized (this) {
        text = pending.toString();
        pending.setLength(0);
        scheduled = false;
      }
      if (!text.isEmpty()) {
        sink.accept(text);
      }
    }

    boolean hasOutput() {
      return written;
    }

    synchronized String captured() {
      return all == null ? "" : all.toString().replaceFirst("[\\r\\n]+$", "");
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }

  private static URLClassLoader classLoader(List<File> classpath) {
    List<URL> urls = new ArrayList<>();
    if (classpath != null) {
      for (File entry : classpath) {
        if (entry != null && entry.exists()) {
          try {
            urls.add(entry.toURI().toURL());
          } catch (MalformedURLException ex) {
            // Skip entries that cannot be represented as class loader URLs.
          }
        }
      }
    }
    ClassLoader parent = Jsr223ScriptRunner.class.getClassLoader();
    return new URLClassLoader(urls.toArray(URL[]::new), parent);
  }

  private static String message(Throwable error) {
    return error == null || error.getMessage() == null ? "Script execution failed" : error.getMessage();
  }

  private static int findLine(Throwable error) {
    for (Throwable current = error; current != null; current = current.getCause()) {
      if (current instanceof ScriptException scriptException && scriptException.getLineNumber() > 0) {
        return scriptException.getLineNumber();
      }
      for (StackTraceElement element : current.getStackTrace()) {
        if (element.getLineNumber() > 0
            && (element.getFileName() == null
                || element.getFileName().contains("Script")
                || element.getClassName().contains("script"))) {
          return element.getLineNumber();
        }
      }
    }
    return -1;
  }
}
