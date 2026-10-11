package io.scriptdock.sample;

import io.scriptdock.Jsr223ScriptRunner;
import io.scriptdock.ScriptDockFrame;
import io.scriptdock.ScriptDockOptions;

import java.awt.*;
import java.util.Map;
import javax.swing.*;

/** Minimal example showing ScriptDock embedded with host-provided bindings. */
public final class EmbedExample {
  private EmbedExample() {}

  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
            Font font = new Font("SansSerif", Font.PLAIN, 14);
            for (Object key : UIManager.getDefaults().keySet()) {
                Object value = UIManager.get(key);
                if (value instanceof Font) {
                    UIManager.put(key, font);
                }
            }
          Jsr223ScriptRunner runner =
              new Jsr223ScriptRunner(
                  () ->
                      Map.of(
                          "applicationName", "ScriptDock sample",
                          "currentUser", System.getProperty("user.name")));
          ScriptDockFrame frame =
              new ScriptDockFrame(
                  null,
                  runner,
                  ScriptDockOptions.builder()
                      .title("Embedded ScriptDock")
                      .persistPreferences("embed-example")
                      .exitOnClose(true)
                      .build());
          frame.setVisible(true);
        });
  }
}
