package io.scriptdock;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Standalone ScriptDock launcher. */
public final class ScriptDock {
  private ScriptDock() {}

  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
          try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
          } catch (Exception ex) {
            // Continue with Swing's default look and feel.
          }
          ScriptDockFrame frame =
              new ScriptDockFrame(
                  null,
                  new Jsr223ScriptRunner(),
                  ScriptDockOptions.builder()
                      .exitOnClose(true)
                      .persistPreferences("standalone")
                      .build());
          frame.setVisible(true);
        });
  }
}
