package io.scriptdock;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Modal editor for the extra JAR files and class directories available to scripts. */
final class ClasspathDialog extends JDialog {
  private final DefaultListModel<File> model = new DefaultListModel<>();
  private final JList<File> list = new JList<>(model);
  private final JButton remove = new JButton("Remove");
  private final JButton up = new JButton("Move Up");
  private final JButton down = new JButton("Move Down");
  private File directory;
  private List<File> result;

  private ClasspathDialog(Window owner, List<File> entries, File directory) {
    super(owner, "Script Classpath", ModalityType.APPLICATION_MODAL);
    this.directory = directory;
    entries.forEach(model::addElement);
    list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    list.setCellRenderer(new EntryRenderer());
    list.addListSelectionListener(event -> updateButtons());

    JButton addJars = new JButton("Add JARs...");
    JButton addFolder = new JButton("Add Folder...");
    addJars.addActionListener(event -> addJars());
    addFolder.addActionListener(event -> addFolder());
    remove.addActionListener(event -> removeSelected());
    up.addActionListener(event -> move(-1));
    down.addActionListener(event -> move(1));

    JPanel side = new JPanel(new GridLayout(0, 1, 0, 4));
    side.add(addJars);
    side.add(addFolder);
    side.add(remove);
    side.add(up);
    side.add(down);
    JPanel sideWrapper = new JPanel(new BorderLayout());
    sideWrapper.add(side, BorderLayout.NORTH);

    JButton ok = new JButton("OK");
    JButton cancel = new JButton("Cancel");
    ok.addActionListener(
        event -> {
          result = new ArrayList<>();
          for (int index = 0; index < model.size(); index++) {
            result.add(model.get(index));
          }
          dispose();
        });
    cancel.addActionListener(event -> dispose());
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(ok);
    buttons.add(cancel);

    JScrollPane scroll = new JScrollPane(list);
    scroll.setPreferredSize(new Dimension(520, 220));
    JPanel content = new JPanel(new BorderLayout(8, 8));
    content.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
    content.add(
        new JLabel("JAR files and class folders available to scripts, searched in order:"),
        BorderLayout.NORTH);
    content.add(scroll, BorderLayout.CENTER);
    content.add(sideWrapper, BorderLayout.EAST);
    content.add(buttons, BorderLayout.SOUTH);
    setContentPane(content);
    getRootPane().setDefaultButton(ok);
    updateButtons();
    pack();
    setLocationRelativeTo(owner);
  }

  /** Shows the dialog and returns the edited entries, or {@code null} if cancelled. */
  static List<File> edit(Window owner, List<File> entries, File directory) {
    ClasspathDialog dialog = new ClasspathDialog(owner, entries, directory);
    dialog.setVisible(true);
    return dialog.result;
  }

  private void addJars() {
    JFileChooser chooser = chooser("Add JAR Files");
    chooser.setMultiSelectionEnabled(true);
    chooser.setFileFilter(new FileNameExtensionFilter("JAR files (*.jar)", "jar"));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      for (File file : chooser.getSelectedFiles()) {
        add(file);
      }
    }
  }

  private void addFolder() {
    JFileChooser chooser = chooser("Add Class Folder");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      add(chooser.getSelectedFile());
    }
  }

  private JFileChooser chooser(String title) {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(title);
    if (directory != null && directory.isDirectory()) {
      chooser.setCurrentDirectory(directory);
    }
    return chooser;
  }

  private void add(File file) {
    if (file == null || !file.exists()) {
      return;
    }
    File entry = file.getAbsoluteFile();
    directory = entry.isDirectory() ? entry : entry.getParentFile();
    if (!model.contains(entry) && model.size() < ScriptDockPreferences.MAX_ENTRIES) {
      model.addElement(entry);
    }
    list.setSelectedValue(entry, true);
  }

  private void removeSelected() {
    int[] selected = list.getSelectedIndices();
    for (int index = selected.length - 1; index >= 0; index--) {
      model.remove(selected[index]);
    }
    updateButtons();
  }

  private void move(int delta) {
    int[] selected = list.getSelectedIndices();
    if (selected.length != 1) {
      return;
    }
    int from = selected[0];
    int to = from + delta;
    if (to < 0 || to >= model.size()) {
      return;
    }
    File entry = model.remove(from);
    model.add(to, entry);
    list.setSelectedIndex(to);
  }

  private void updateButtons() {
    int[] selected = list.getSelectedIndices();
    remove.setEnabled(selected.length > 0);
    up.setEnabled(selected.length == 1 && selected[0] > 0);
    down.setEnabled(selected.length == 1 && selected[0] < model.size() - 1);
  }

  private static final class EntryRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean selected, boolean focused) {
      super.getListCellRendererComponent(list, value, index, selected, focused);
      File file = (File) value;
      boolean missing = !file.exists();
      setText(file.getPath() + (missing ? "  (missing)" : ""));
      if (missing && !selected) {
        setForeground(Color.RED.darker());
      }
      setToolTipText(missing ? "Not found; skipped when scripts run" : null);
      return this;
    }
  }
}
