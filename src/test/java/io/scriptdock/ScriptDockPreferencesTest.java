package io.scriptdock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.Rectangle;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScriptDockPreferencesTest {
  private Preferences node;
  private ScriptDockPreferences preferences;

  @TempDir Path temp;

  @BeforeEach
  void setUp() {
    node = Preferences.userRoot().node(ScriptDockOptions.PREFERENCES_ROOT + "/test-" + UUID.randomUUID());
    preferences = new ScriptDockPreferences(node);
  }

  @AfterEach
  void tearDown() throws Exception {
    node.removeNode();
  }

  @Test
  void fontRoundTripsAndFallsBackWhenAbsent() {
    Font fallback = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    assertSame(fallback, preferences.font(fallback));

    preferences.saveFont(new Font(Font.SERIF, Font.BOLD, 18));

    Font restored = preferences.font(fallback);
    assertEquals(Font.SERIF, restored.getFamily());
    assertEquals(Font.BOLD, restored.getStyle());
    assertEquals(18, restored.getSize());
  }

  @Test
  void invalidFontSizeUsesFallback() {
    Font fallback = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    preferences.saveFont(new Font(Font.SERIF, Font.PLAIN, 13));
    node.putInt("font.size", 500);

    assertSame(fallback, preferences.font(fallback));
  }

  @Test
  void directoryIsRestoredOnlyWhenItStillExists() throws Exception {
    File fallback = temp.toFile();
    File stored = Files.createDirectory(temp.resolve("scripts")).toFile();
    preferences.saveDirectory(stored);
    assertEquals(stored.getAbsoluteFile(), preferences.directory(fallback));

    Files.delete(stored.toPath());
    assertSame(fallback, preferences.directory(fallback));
  }

  @Test
  void engineIsRestoredOnlyWhenInstalled() {
    preferences.saveEngine("js");
    assertEquals("js", preferences.engine(List.of("groovy", "js"), "groovy"));
    assertEquals("groovy", preferences.engine(List.of("groovy"), "groovy"));
  }

  @Test
  void windowStateRoundTripsAndRejectsTinyBounds() {
    assertNull(preferences.windowBounds());
    preferences.saveWindow(new Rectangle(10, 20, 900, 600), true);

    assertEquals(new Rectangle(10, 20, 900, 600), preferences.windowBounds());
    assertTrue(preferences.windowMaximized());

    node.putInt("window.width", 5);
    assertNull(preferences.windowBounds());
  }

  @Test
  void windowMustOverlapAScreen() {
    List<Rectangle> screens = List.of(new Rectangle(0, 0, 1920, 1080));
    assertTrue(ScriptDockPreferences.isVisibleOnScreens(new Rectangle(100, 100, 800, 600), screens));
    assertFalse(
        ScriptDockPreferences.isVisibleOnScreens(new Rectangle(3000, 100, 800, 600), screens));
    assertFalse(ScriptDockPreferences.isVisibleOnScreens(null, screens));
  }

  @Test
  void dividerDefaultsToUnset() {
    assertEquals(-1, preferences.dividerLocation());
    preferences.saveDividerLocation(340);
    assertEquals(340, preferences.dividerLocation());
    assertEquals(-1, preferences.horizontalDividerLocation());
    preferences.saveHorizontalDividerLocation(520);
    assertEquals(520, preferences.horizontalDividerLocation());
  }

  @Test
  void splitOrientationDefaultsToVerticalAndCanBePersisted() {
    assertFalse(preferences.horizontalSplit());
    preferences.saveHorizontalSplit(true);
    assertTrue(preferences.horizontalSplit());
    preferences.saveHorizontalSplit(false);
    assertFalse(preferences.horizontalSplit());
  }

  @Test
  void tabsRoundTripInOrderAndShrinkCleanly() {
    File first = temp.resolve("a.groovy").toFile();
    File second = temp.resolve("b.groovy").toFile();
    File third = temp.resolve("c.groovy").toFile();
    preferences.saveTabs(List.of(first, second, third), 1);

    assertEquals(
        List.of(first.getAbsoluteFile(), second.getAbsoluteFile(), third.getAbsoluteFile()),
        preferences.tabs());
    assertEquals(1, preferences.selectedTab());

    preferences.saveTabs(List.of(third), -1);
    assertEquals(List.of(third.getAbsoluteFile()), preferences.tabs());
    assertEquals(-1, preferences.selectedTab());
    assertNull(node.get("tabs.1", null));
  }

  @Test
  void classpathRoundTripsInOrderAndShrinksCleanly() {
    File jar = temp.resolve("lib.jar").toFile();
    File folder = temp.resolve("classes").toFile();
    assertEquals(List.of(), preferences.classpath());

    preferences.saveClasspath(List.of(jar, folder));
    assertEquals(List.of(jar.getAbsoluteFile(), folder.getAbsoluteFile()), preferences.classpath());

    preferences.saveClasspath(List.of(folder));
    assertEquals(List.of(folder.getAbsoluteFile()), preferences.classpath());
    assertNull(node.get("classpath.1", null));
  }

  @Test
  void classpathIsPersistedWithoutOptingIn() {
    assertNull(ScriptDockOptions.builder().build().preferences());
    assertNotNull(ScriptDockOptions.builder().build().classpathPreferences());
  }

  @Test
  void classpathNamespaceIsDerivedSafelyFromTheTitle() {
    assertEquals("ScriptDock", ScriptDockOptions.titleNamespace("ScriptDock"));
    assertEquals("My-App-Scripts", ScriptDockOptions.titleNamespace("  My App / Scripts! "));
    assertEquals("default", ScriptDockOptions.titleNamespace("///"));
    assertEquals(80, ScriptDockOptions.titleNamespace("x".repeat(200)).length());
  }

  @Test
  void classpathUsesThePersistedNamespaceWhenConfigured() {
    preferences.saveClasspath(List.of(temp.toFile()));
    ScriptDockOptions options = ScriptDockOptions.builder().preferencesNode(node).build();
    assertEquals(List.of(temp.toFile().getAbsoluteFile()), options.classpathPreferences().classpath());
  }

  @Test
  void openDirectoryPrefersActiveScriptParentThenClasspathFolder() throws Exception {
    File activeDirectory = Files.createDirectory(temp.resolve("active")).toFile();
    File classpathDirectory = Files.createDirectory(temp.resolve("classes")).toFile();
    File activeFile = activeDirectory.toPath().resolve("script.groovy").toFile();
    File jar = temp.resolve("library.jar").toFile();
    File fallback = temp.toFile();

    assertEquals(
        activeDirectory,
        ScriptDockFrame.directoryForOpen(activeFile, List.of(classpathDirectory), fallback));
    assertEquals(
        classpathDirectory,
        ScriptDockFrame.directoryForOpen(null, List.of(jar, classpathDirectory), fallback));
    assertEquals(
        fallback, ScriptDockFrame.directoryForOpen(null, List.of(jar), fallback));
  }

  @Test
  void optionsPersistOnlyWhenRequestedAndValidateNamespaces() {
    assertNull(ScriptDockOptions.builder().build().preferences());
    assertThrows(
        IllegalArgumentException.class,
        () -> ScriptDockOptions.builder().persistPreferences("../escape"));
    assertThrows(
        IllegalArgumentException.class, () -> ScriptDockOptions.builder().persistPreferences(""));
  }

  @Test
  void namespacesAreIsolated() throws Exception {
    Preferences other =
        Preferences.userRoot().node(ScriptDockOptions.PREFERENCES_ROOT + "/test-" + UUID.randomUUID());
    try {
      preferences.saveEngine("js");
      assertEquals(
          "groovy", new ScriptDockPreferences(other).engine(List.of("groovy", "js"), "groovy"));
    } finally {
      other.removeNode();
    }
  }
}
