package io.scriptdock;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;

final class LineNumberView extends JComponent implements DocumentListener, CaretListener {
  private static final int MARGIN = 6;
  private final ScriptEditor editor;

  LineNumberView(ScriptEditor editor) {
    this.editor = editor;
    setFont(editor.getFont());
    setBackground(Color.LIGHT_GRAY);
    setForeground(Color.DARK_GRAY);
    editor.getDocument().addDocumentListener(this);
    editor.addCaretListener(this);
    editor.addPropertyChangeListener(
        "font",
        event -> {
          setFont(editor.getFont());
          revalidate();
          repaint();
        });
    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            if (event.getButton() != MouseEvent.BUTTON1 || event.getX() >= foldColumnWidth()) {
              return;
            }
            int offset = editor.viewToModel2D(new java.awt.Point(0, event.getY()));
            if (offset < 0) {
              return;
            }
            int lineIndex = editor.getDocument().getDefaultRootElement().getElementIndex(offset);
            if (editor.isFoldableLine(lineIndex)) {
              editor.toggleFoldAtLine(lineIndex + 1);
            }
          }
        });
  }

  @Override
  public Dimension getPreferredSize() {
    int lines = editor.getDocument().getDefaultRootElement().getElementCount();
    int digits = Math.max(2, Integer.toString(lines).length());
    FontMetrics metrics = getFontMetrics(getFont());
    return new Dimension(
        foldColumnWidth() + metrics.charWidth('0') * digits + MARGIN * 2, editor.getHeight());
  }

  private int foldMarkerSize() {
    return Math.max(8, Math.round(getFontMetrics(getFont()).getAscent() * 0.6f));
  }

  private int foldColumnWidth() {
    return foldMarkerSize() + MARGIN * 2;
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    graphics.setColor(getBackground());
    graphics.fillRect(0, 0, getWidth(), getHeight());
    graphics.setColor(getForeground());
    graphics.setFont(getFont());
    Rectangle clip = graphics.getClipBounds();
    Element root = editor.getDocument().getDefaultRootElement();
    try {
      int firstOffset = editor.viewToModel2D(new java.awt.Point(0, clip.y));
      if (firstOffset < 0) {
        return;
      }
      int first = root.getElementIndex(firstOffset);
      for (int index = first; index < root.getElementCount(); index++) {
        java.awt.geom.Rectangle2D shape =
            editor.modelToView2D(root.getElement(index).getStartOffset());
        if (shape == null) {
          break;
        }
        Rectangle location = shape.getBounds();
        if (location.y > clip.y + clip.height) {
          break;
        }
        if (editor.isFoldedLine(index)) {
          continue;
        }
        if (editor.isFoldableLine(index)) {
          paintFoldMarker(graphics, location, editor.isFoldCollapsedAtLine(index));
          graphics.setColor(getForeground());
        }
        String label = Integer.toString(index + 1);
        int x = getWidth() - graphics.getFontMetrics().stringWidth(label) - MARGIN;
        graphics.drawString(label, x, location.y + graphics.getFontMetrics().getAscent());
      }
    } catch (BadLocationException ex) {
      // The document changed between layout and paint; the next repaint will use valid offsets.
    }
  }

  private void paintFoldMarker(Graphics graphics, Rectangle line, boolean collapsed) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(getForeground());
      float size = foldMarkerSize();
      float centerX = MARGIN + size / 2f;
      float centerY = line.y + line.height / 2f;
      float half = size / 2f;
      Path2D.Float triangle = new Path2D.Float();
      if (collapsed) {
        triangle.moveTo(centerX - half * 0.6f, centerY - half);
        triangle.lineTo(centerX - half * 0.6f, centerY + half);
        triangle.lineTo(centerX + half * 0.8f, centerY);
      } else {
        triangle.moveTo(centerX - half, centerY - half * 0.6f);
        triangle.lineTo(centerX + half, centerY - half * 0.6f);
        triangle.lineTo(centerX, centerY + half * 0.8f);
      }
      triangle.closePath();
      g.fill(triangle);
    } finally {
      g.dispose();
    }
  }

  private void changed() {
    revalidate();
    repaint();
  }

  @Override
  public void insertUpdate(DocumentEvent event) {
    changed();
  }

  @Override
  public void removeUpdate(DocumentEvent event) {
    changed();
  }

  @Override
  public void changedUpdate(DocumentEvent event) {
    repaint();
  }

  @Override
  public void caretUpdate(CaretEvent event) {
    repaint();
  }
}
