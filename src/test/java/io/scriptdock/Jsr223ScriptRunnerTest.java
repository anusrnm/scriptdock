package io.scriptdock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.script.ScriptException;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class Jsr223ScriptRunnerTest {
  @Test
  void returnsFreshReadOnlyBindingSnapshots() {
    AtomicInteger value = new AtomicInteger();
    Jsr223ScriptRunner runner =
        new Jsr223ScriptRunner(() -> Map.of("count", value.incrementAndGet()));

    Map<String, Object> first = runner.getBindings();
    Map<String, Object> second = runner.getBindings();

    assertEquals(Map.of("count", 1), first);
    assertEquals(Map.of("count", 2), second);
    assertThrows(UnsupportedOperationException.class, () -> first.put("other", "value"));
  }

  @Test
  void treatsNullSupplierResultAsNoBindings() {
    Jsr223ScriptRunner runner = new Jsr223ScriptRunner(() -> null);

    assertEquals(Map.of(), runner.getBindings());
  }

  @Test
  void customRunnerDefaultsToNoInspectableBindings() {
    ScriptDockRunner runner =
        new ScriptDockRunner() {
          @Override
          public boolean isRunning() {
            return false;
          }

          @Override
          public void run(String script, String engineName, Listener listener) {}

          @Override
          public void stop() {}
        };

    assertEquals(Map.of(), runner.getBindings());
  }

  @Test
  void evaluatesWithHostBindings() throws Exception {
    String output =
        Jsr223ScriptRunner.evaluate(
            "println \"$applicationName/$count\"",
            "groovy",
            Map.of("applicationName", "sample", "count", 3));

    assertEquals("sample/3", output);
  }

  @Test
  void returnsTheFinalExpressionWhenNothingWasPrinted() throws Exception {
    assertEquals("5", Jsr223ScriptRunner.evaluate("2 + 3", "groovy", Map.of()));
  }

  @Test
  void reportsUnknownEngines() {
    ScriptException failure =
        assertThrows(
            ScriptException.class,
            () -> Jsr223ScriptRunner.evaluate("1", "not-an-engine", Map.of()));

    assertTrue(failure.getMessage().contains("not-an-engine"));
  }

  @Test
  void streamsPrintedOutputBeforeTheScriptCompletes() throws Exception {
    CountDownLatch firstOutput = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    List<String> events = new CopyOnWriteArrayList<>();
    Jsr223ScriptRunner runner = new Jsr223ScriptRunner(() -> Map.of("release", release));

    SwingUtilities.invokeAndWait(
        () ->
            runner.run(
                "println 'first'; release.await(); println 'second'",
                "groovy",
                new ScriptDockRunner.Listener() {
                  @Override
                  public void output(String text) {
                    events.add("output:" + text);
                    firstOutput.countDown();
                  }

                  @Override
                  public void completed(String output) {
                    events.add("completed:" + output);
                    done.countDown();
                  }

                  @Override
                  public void failed(String message, int lineNumber) {
                    events.add("failed:" + message);
                    done.countDown();
                  }
                }));

    assertTrue(firstOutput.await(10, TimeUnit.SECONDS), "first line was not streamed");
    assertTrue(runner.isRunning());
    assertEquals(List.of("output:first" + System.lineSeparator()), List.copyOf(events));

    release.countDown();
    assertTrue(done.await(10, TimeUnit.SECONDS));
    assertEquals(
        String.join("", "first", System.lineSeparator(), "second", System.lineSeparator()),
        events.stream()
            .filter(event -> event.startsWith("output:"))
            .map(event -> event.substring("output:".length()))
            .collect(Collectors.joining()));
    assertEquals("completed:", events.get(events.size() - 1));
    assertFalse(runner.isRunning());
  }

  @Test
  void stopInterruptsATightGroovyLoop() throws Exception {
    Jsr223ScriptRunner runner = new Jsr223ScriptRunner(Map::of);
    CountDownLatch started = new CountDownLatch(1);
    List<String> events = new CopyOnWriteArrayList<>();
    ScriptDockRunner.Listener listener =
        new ScriptDockRunner.Listener() {
          @Override
          public void output(String text) {
            started.countDown();
          }

          @Override
          public void completed(String output) {
            events.add("completed");
          }

          @Override
          public void failed(String message, int lineNumber) {
            events.add("failed:" + message);
          }
        };

    SwingUtilities.invokeAndWait(
        () -> runner.run("println 'go'; long i = 0; while (true) { i++ }", "groovy", listener));
    assertTrue(started.await(10, TimeUnit.SECONDS));
    assertTrue(runner.isRunning());

    SwingUtilities.invokeAndWait(runner::stop);

    assertFalse(runner.isRunning());
    assertEquals(List.of("failed:Script execution was stopped"), List.copyOf(events));

    // The interrupt check injected into Groovy loops should end the thread well before the
    // forced-stop grace period.
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500);
    while (scriptThreadAlive() && System.nanoTime() < deadline) {
      Thread.sleep(20);
    }
    assertFalse(scriptThreadAlive(), "script thread did not stop after interrupt");

    SwingUtilities.invokeAndWait(() -> {});
    assertEquals(List.of("failed:Script execution was stopped"), List.copyOf(events));
  }

  private static boolean scriptThreadAlive() {
    return Thread.getAllStackTraces().keySet().stream()
        .anyMatch(thread -> thread.isAlive() && "ScriptDock-script".equals(thread.getName()));
  }

  @Test
  void groovyIsAvailable() {
    assertTrue(Jsr223ScriptRunner.availableEngineNames().contains("groovy"));
  }
}
