package io.scriptdock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.script.ScriptException;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Jsr223ClasspathTest {
  private static final String SCRIPT = "new sdtest.Greeter().hello()";

  @TempDir Path temp;
  private Path classes;

  @BeforeEach
  void compileGreeter() throws Exception {
    Path source = temp.resolve("src/sdtest/Greeter.java");
    Files.createDirectories(source.getParent());
    Files.writeString(
        source,
        "package sdtest; public class Greeter { public String hello() { return \"hi\"; } }");
    classes = Files.createDirectories(temp.resolve("classes"));
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertEquals(0, compiler.run(null, null, null, "-d", classes.toString(), source.toString()));
  }

  @Test
  void loadsClassesFromAClassFolder() throws Exception {
    assertEquals("hi", Jsr223ScriptRunner.evaluate(SCRIPT, "groovy", Map.of(), List.of(classes.toFile())));
  }

  @Test
  void loadsClassesFromAJarAndReleasesIt() throws Exception {
    File jar = jar();

    assertEquals("hi", Jsr223ScriptRunner.evaluate(SCRIPT, "groovy", Map.of(), List.of(jar)));
    assertTrue(jar.delete(), "classpath JAR should not stay locked after the run");
  }

  @Test
  void skipsMissingEntries() throws Exception {
    List<File> entries = List.of(temp.resolve("missing.jar").toFile(), classes.toFile());

    assertEquals("hi", Jsr223ScriptRunner.evaluate(SCRIPT, "groovy", Map.of(), entries));
  }

  @Test
  void classesAreUnavailableWithoutTheEntry() {
    assertThrows(ScriptException.class, () -> Jsr223ScriptRunner.evaluate(SCRIPT, "groovy", Map.of()));
  }

  @Test
  void restoresTheContextClassLoader() throws Exception {
    ClassLoader before = Thread.currentThread().getContextClassLoader();
    Jsr223ScriptRunner.evaluate(SCRIPT, "groovy", Map.of(), List.of(classes.toFile()));
    assertEquals(before, Thread.currentThread().getContextClassLoader());
  }

  @Test
  void classpathAwareEngineDiscoveryKeepsGroovyFirst() {
    assertEquals("groovy", Jsr223ScriptRunner.availableEngineNames(List.of(classes.toFile())).get(0));
  }

  @Test
  void customRunnersIgnoreClasspathByDefault() {
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
    runner.setClasspath(List.of(classes.toFile()));
  }

  private File jar() throws Exception {
    Path jar = temp.resolve("greeter.jar");
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
      out.putNextEntry(new JarEntry("sdtest/Greeter.class"));
      out.write(Files.readAllBytes(classes.resolve("sdtest/Greeter.class")));
      out.closeEntry();
    }
    return jar.toFile();
  }
}
