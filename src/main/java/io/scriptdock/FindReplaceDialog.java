package io.scriptdock;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

final class FindReplaceDialog extends JDialog {
  private final Supplier<? extends JTextComponent> targetSupplier;
  private final JTextField findField = new JTextField(24);
  private final JTextField replaceField = new JTextField(24);
  private final JCheckBox matchCase = new JCheckBox("Match case");
  private final JCheckBox wrap = new JCheckBox("Wrap around", true);
  private final JLabel status = new JLabel(" ");

  FindReplaceDialog(Frame owner, Supplier<? extends JTextComponent> targetSupplier) {
    super(owner, "Find and Replace", false);
    this.targetSupplier = Objects.requireNonNull(targetSupplier, "targetSupplier");
    build();
    pack();
    setResizable(false);
    setLocationRelativeTo(owner);
  }

  private void build() {
    JPanel fields = new JPanel(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.insets = new Insets(4, 4, 4, 4);
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.gridx = 0;
    constraints.gridy = 0;
    fields.add(new JLabel("Find:"), constraints);
    constraints.gridx = 1;
    constraints.weightx = 1;
    fields.add(findField, constraints);
    constraints.gridx = 0;
    constraints.gridy = 1;
    constraints.weightx = 0;
    fields.add(new JLabel("Replace with:"), constraints);
    constraints.gridx = 1;
    constraints.weightx = 1;
    fields.add(replaceField, constraints);
    JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    options.add(matchCase);
    options.add(wrap);
    constraints.gridy = 2;
    fields.add(options, constraints);

    JButton next = new JButton("Find Next");
    JButton previous = new JButton("Find Previous");
    JButton replace = new JButton("Replace");
    JButton replaceAll = new JButton("Replace All");
    JButton close = new JButton("Close");
    next.addActionListener(event -> find(true));
    previous.addActionListener(event -> find(false));
    replace.addActionListener(event -> replace());
    replaceAll.addActionListener(event -> replaceAll());
    close.addActionListener(event -> setVisible(false));
    JPanel buttons = new JPanel(new java.awt.GridLayout(0, 1, 4, 4));
    buttons.add(next);
    buttons.add(previous);
    buttons.add(replace);
    buttons.add(replaceAll);
    buttons.add(close);

    status.setForeground(Color.DARK_GRAY);
    getRootPane().setDefaultButton(next);
    getRootPane()
        .registerKeyboardAction(
            event -> setVisible(false),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    getContentPane().setLayout(new BorderLayout(8, 8));
    getContentPane().add(fields, BorderLayout.CENTER);
    getContentPane().add(buttons, BorderLayout.EAST);
    getContentPane().add(status, BorderLayout.SOUTH);
    ((JComponent) getContentPane())
        .setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
  }

  void showFor(String selection) {
    if (selection != null && !selection.isEmpty()) {
      findField.setText(selection);
    }
    status.setText(" ");
    setVisible(true);
    findField.requestFocusInWindow();
    findField.selectAll();
  }

  void next() {
    if (findField.getText().isEmpty()) {
      showFor(target().getSelectedText());
    } else {
      find(true);
    }
  }

  void previous() {
    if (findField.getText().isEmpty()) {
      showFor(target().getSelectedText());
    } else {
      find(false);
    }
  }

  private void find(boolean forward) {
    String needle = findField.getText();
    if (needle.isEmpty()) {
      return;
    }
    JTextComponent target = target();
    String text = text(target);
    String source = matchCase.isSelected() ? text : text.toLowerCase(Locale.ROOT);
    String query = matchCase.isSelected() ? needle : needle.toLowerCase(Locale.ROOT);
    int caret = forward ? target.getSelectionEnd() : target.getSelectionStart();
    int index = forward ? source.indexOf(query, caret) : source.lastIndexOf(query, caret - 1);
    boolean wrapped = false;
    if (index < 0 && wrap.isSelected()) {
      index = forward ? source.indexOf(query) : source.lastIndexOf(query);
      wrapped = index >= 0;
    }
    if (index < 0) {
      status.setText("\"" + needle + "\" not found");
      return;
    }
    target.select(index, index + needle.length());
    try {
      target.scrollRectToVisible(target.modelToView2D(index).getBounds());
    } catch (BadLocationException ex) {
      throw new IllegalStateException("Unable to reveal match", ex);
    }
    status.setText(wrapped ? "Wrapped around" : " ");
  }

  private void replace() {
    JTextComponent target = target();
    String selected = target.getSelectedText();
    String needle = findField.getText();
    if (selected != null
        && (matchCase.isSelected() ? selected.equals(needle) : selected.equalsIgnoreCase(needle))) {
      target.replaceSelection(replaceField.getText());
    }
    find(true);
  }

  private void replaceAll() {
    JTextComponent target = target();
    String needle = findField.getText();
    if (needle.isEmpty()) {
      return;
    }
    String text = text(target);
    String source = matchCase.isSelected() ? text : text.toLowerCase(Locale.ROOT);
    String query = matchCase.isSelected() ? needle : needle.toLowerCase(Locale.ROOT);
    StringBuilder result = new StringBuilder();
    int from = 0;
    int count = 0;
    for (int found = source.indexOf(query, from);
        found >= 0;
        found = source.indexOf(query, from)) {
      result.append(text, from, found).append(replaceField.getText());
      from = found + needle.length();
      count++;
    }
    result.append(text, from, text.length());
    if (count > 0) {
      target.setText(result.toString());
    }
    status.setText(count + (count == 1 ? " replacement made" : " replacements made"));
  }

  private JTextComponent target() {
    return Objects.requireNonNull(targetSupplier.get(), "Find target");
  }

  private static String text(JTextComponent target) {
    try {
      return target.getDocument().getText(0, target.getDocument().getLength());
    } catch (BadLocationException ex) {
      throw new IllegalStateException("Unable to read editor text", ex);
    }
  }
}
