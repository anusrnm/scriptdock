package io.scriptdock;

import java.awt.Font;
import java.io.File;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

/** Configuration for an embedded ScriptDock window. */
public final class ScriptDockOptions {
  static final String PREFERENCES_ROOT = "com/anusrnm/scriptdock";
  private static final Pattern NAMESPACE = Pattern.compile("[A-Za-z0-9._-]{1,80}");

  private final String title;
  private final Font editorFont;
  private final File initialDirectory;
  private final Consumer<File> directoryListener;
  private final boolean exitOnClose;
  private final Preferences preferencesNode;

  private ScriptDockOptions(Builder builder) {
    title = builder.title;
    editorFont = builder.editorFont;
    initialDirectory = builder.initialDirectory;
    directoryListener = builder.directoryListener;
    exitOnClose = builder.exitOnClose;
    preferencesNode = builder.preferencesNode;
  }

  public static Builder builder() {
    return new Builder();
  }

  String title() {
    return title;
  }

  Font editorFont() {
    return editorFont;
  }

  File initialDirectory() {
    return initialDirectory;
  }

  Consumer<File> directoryListener() {
    return directoryListener;
  }

  boolean exitOnClose() {
    return exitOnClose;
  }

  /** Returns the preference store, or {@code null} when persistence is disabled. */
  ScriptDockPreferences preferences() {
    return preferencesNode == null ? null : new ScriptDockPreferences(preferencesNode);
  }

  /**
   * Returns the store for the script classpath, which is always persisted so embedding
   * applications need no configuration. Without {@code persistPreferences}, it is keyed by title.
   */
  ScriptDockPreferences classpathPreferences() {
    if (preferencesNode != null) {
      return new ScriptDockPreferences(preferencesNode);
    }
    return new ScriptDockPreferences(
        Preferences.userRoot().node(PREFERENCES_ROOT + "/classpath/" + titleNamespace(title)));
  }

  static String titleNamespace(String title) {
    String namespace = title.trim().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
    if (namespace.length() > 80) {
      namespace = namespace.substring(0, 80);
    }
    return namespace.isEmpty() ? "default" : namespace;
  }

  public static final class Builder {
    private String title = "ScriptDock";
    private Font editorFont = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    private File initialDirectory;
    private Consumer<File> directoryListener = ignored -> {};
    private boolean exitOnClose;
    private Preferences preferencesNode;

    private Builder() {}

    public Builder title(String title) {
      this.title = Objects.requireNonNull(title, "title");
      return this;
    }

    /**
     * Sets the editor font. When preferences are persisted, this is only the first-run default and
     * a font chosen by the user takes precedence.
     */
    public Builder editorFont(Font editorFont) {
      this.editorFont = Objects.requireNonNull(editorFont, "editorFont");
      return this;
    }

    /**
     * Sets the file chooser's starting directory. When preferences are persisted, this is only the
     * default used until the user opens or saves a file.
     */
    public Builder initialDirectory(File initialDirectory) {
      this.initialDirectory = initialDirectory;
      return this;
    }

    /**
     * Persists user preferences and saved-file tabs under the given namespace. Use a distinct
     * namespace per application so unrelated hosts do not share settings.
     *
     * @param namespace 1-80 characters from {@code [A-Za-z0-9._-]}
     */
    public Builder persistPreferences(String namespace) {
      Objects.requireNonNull(namespace, "namespace");
      if (!NAMESPACE.matcher(namespace).matches()) {
        throw new IllegalArgumentException(
            "Preference namespace must be 1-80 characters of letters, digits, '.', '_' or '-'");
      }
      return preferencesNode(Preferences.userRoot().node(PREFERENCES_ROOT + "/" + namespace));
    }

    Builder preferencesNode(Preferences preferencesNode) {
      this.preferencesNode = preferencesNode;
      return this;
    }

    /** Receives each directory ScriptDock switches to after opening or saving a file. */
    public Builder directoryListener(Consumer<File> directoryListener) {
      this.directoryListener = Objects.requireNonNull(directoryListener, "directoryListener");
      return this;
    }

    public Builder exitOnClose(boolean exitOnClose) {
      this.exitOnClose = exitOnClose;
      return this;
    }

    public ScriptDockOptions build() {
      return new ScriptDockOptions(this);
    }
  }
}
