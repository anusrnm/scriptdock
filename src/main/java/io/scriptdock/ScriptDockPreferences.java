package io.scriptdock;

import java.awt.Font;
import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Validated persistent user preferences and saved-tab session for ScriptDock. */
final class ScriptDockPreferences {
  static final int MIN_FONT_SIZE = 6;
  static final int MAX_FONT_SIZE = 72;
  static final int MAX_ENTRIES = 50;
  private static final int MIN_WINDOW_SIZE = 200;

  private static final String FONT_FAMILY = "font.family";
  private static final String FONT_STYLE = "font.style";
  private static final String FONT_SIZE = "font.size";
  private static final String DIRECTORY = "directory";
  private static final String ENGINE = "engine";
  private static final String WINDOW_X = "window.x";
  private static final String WINDOW_Y = "window.y";
  private static final String WINDOW_WIDTH = "window.width";
  private static final String WINDOW_HEIGHT = "window.height";
  private static final String WINDOW_MAXIMIZED = "window.maximized";
  private static final String DIVIDER = "divider";
  private static final String HORIZONTAL_DIVIDER = "divider.horizontal";
  private static final String HORIZONTAL_SPLIT = "split.horizontal";
  private static final String TAB_COUNT = "tabs.count";
  private static final String TAB_PREFIX = "tabs.";
  private static final String TAB_SELECTED = "tabs.selected";
  private static final String CLASSPATH_COUNT = "classpath.count";
  private static final String CLASSPATH_PREFIX = "classpath.";

  private final Preferences node;

  ScriptDockPreferences(Preferences node) {
    this.node = Objects.requireNonNull(node, "node");
  }

  Font font(Font fallback) {
    String family = get(FONT_FAMILY);
    int style = getInt(FONT_STYLE, -1);
    int size = getInt(FONT_SIZE, -1);
    if (family == null
        || family.isBlank()
        || style < Font.PLAIN
        || style > (Font.BOLD | Font.ITALIC)
        || size < MIN_FONT_SIZE
        || size > MAX_FONT_SIZE) {
      return fallback;
    }
    return new Font(family, style, size);
  }

  void saveFont(Font font) {
    if (font == null) {
      return;
    }
    put(FONT_FAMILY, font.getFamily());
    putInt(FONT_STYLE, font.getStyle());
    putInt(FONT_SIZE, font.getSize());
  }

  File directory(File fallback) {
    String path = get(DIRECTORY);
    if (path != null && !path.isBlank()) {
      File directory = new File(path);
      if (directory.isDirectory()) {
        return directory;
      }
    }
    return fallback;
  }

  void saveDirectory(File directory) {
    if (directory != null) {
      put(DIRECTORY, directory.getAbsolutePath());
    }
  }

  String engine(Collection<String> available, String fallback) {
    String stored = get(ENGINE);
    return stored != null && available.contains(stored) ? stored : fallback;
  }

  void saveEngine(String engine) {
    if (engine != null && !engine.isBlank()) {
      put(ENGINE, engine);
    }
  }

  Rectangle windowBounds() {
    int width = getInt(WINDOW_WIDTH, -1);
    int height = getInt(WINDOW_HEIGHT, -1);
    if (width < MIN_WINDOW_SIZE || height < MIN_WINDOW_SIZE) {
      return null;
    }
    return new Rectangle(getInt(WINDOW_X, 0), getInt(WINDOW_Y, 0), width, height);
  }

  boolean windowMaximized() {
    try {
      return node.getBoolean(WINDOW_MAXIMIZED, false);
    } catch (IllegalStateException | SecurityException ex) {
      return false;
    }
  }

  void saveWindow(Rectangle bounds, boolean maximized) {
    if (bounds != null && bounds.width >= MIN_WINDOW_SIZE && bounds.height >= MIN_WINDOW_SIZE) {
      putInt(WINDOW_X, bounds.x);
      putInt(WINDOW_Y, bounds.y);
      putInt(WINDOW_WIDTH, bounds.width);
      putInt(WINDOW_HEIGHT, bounds.height);
    }
    write(() -> node.putBoolean(WINDOW_MAXIMIZED, maximized));
  }

  /** Returns the stored divider location, or -1 when none is stored. */
  int dividerLocation() {
    int location = getInt(DIVIDER, -1);
    return location > 0 ? location : -1;
  }

  void saveDividerLocation(int location) {
    if (location > 0) {
      putInt(DIVIDER, location);
    }
  }

  int horizontalDividerLocation() {
    int location = getInt(HORIZONTAL_DIVIDER, -1);
    return location > 0 ? location : -1;
  }

  void saveHorizontalDividerLocation(int location) {
    if (location > 0) {
      putInt(HORIZONTAL_DIVIDER, location);
    }
  }

  boolean horizontalSplit() {
    try {
      return node.getBoolean(HORIZONTAL_SPLIT, false);
    } catch (IllegalStateException | SecurityException ex) {
      return false;
    }
  }

  void saveHorizontalSplit(boolean horizontal) {
    write(() -> node.putBoolean(HORIZONTAL_SPLIT, horizontal));
  }

  List<File> tabs() {
    return files(TAB_PREFIX, TAB_COUNT);
  }

  List<File> classpath() {
    return files(CLASSPATH_PREFIX, CLASSPATH_COUNT);
  }

  void saveClasspath(List<File> entries) {
    saveFiles(CLASSPATH_PREFIX, CLASSPATH_COUNT, entries);
  }

  private List<File> files(String prefix, String countKey) {
    int count = Math.min(getInt(countKey, 0), MAX_ENTRIES);
    List<File> files = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      String path = get(prefix + index);
      if (path != null && !path.isBlank()) {
        files.add(new File(path));
      }
    }
    return files;
  }

  private int saveFiles(String prefix, String countKey, List<File> files) {
    int previous = getInt(countKey, 0);
    int count = Math.min(files.size(), MAX_ENTRIES);
    for (int index = 0; index < count; index++) {
      put(prefix + index, files.get(index).getAbsolutePath());
    }
    for (int index = count; index < Math.min(previous, MAX_ENTRIES); index++) {
      remove(prefix + index);
    }
    putInt(countKey, count);
    return count;
  }

  /** Returns the stored selected tab index, or -1 when none is stored. */
  int selectedTab() {
    return getInt(TAB_SELECTED, -1);
  }

  void saveTabs(List<File> files, int selected) {
    int count = saveFiles(TAB_PREFIX, TAB_COUNT, files);
    putInt(TAB_SELECTED, selected >= 0 && selected < count ? selected : -1);
  }

  void flush() {
    try {
      node.flush();
    } catch (BackingStoreException | IllegalStateException | SecurityException ex) {
      // Preferences are best effort and must not block the console.
    }
  }

  static boolean isVisibleOnScreens(Rectangle bounds, Collection<Rectangle> screens) {
    if (bounds == null) {
      return false;
    }
    for (Rectangle screen : screens) {
      Rectangle visible = screen.intersection(bounds);
      if (!visible.isEmpty() && visible.width >= 100 && visible.height >= 50) {
        return true;
      }
    }
    return false;
  }

  private String get(String key) {
    try {
      return node.get(key, null);
    } catch (IllegalStateException | SecurityException ex) {
      return null;
    }
  }

  private int getInt(String key, int fallback) {
    try {
      return node.getInt(key, fallback);
    } catch (IllegalStateException | SecurityException ex) {
      return fallback;
    }
  }

  private void put(String key, String value) {
    write(() -> node.put(key, value));
  }

  private void putInt(String key, int value) {
    write(() -> node.putInt(key, value));
  }

  private void remove(String key) {
    write(() -> node.remove(key));
  }

  private static void write(Runnable action) {
    try {
      action.run();
    } catch (IllegalArgumentException | IllegalStateException | SecurityException ex) {
      // Preferences are best effort and must not block the console.
    }
  }
}
