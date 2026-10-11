package io.scriptdock;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.event.CaretEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.swing.text.IconView;
import javax.swing.text.JTextComponent;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

/** ScriptDock's public editor component, with undo/redo and source-line highlighting. */
public final class ScriptEditor extends JTextPane {
  private static final Set<String> GROOVY_KEYWORDS =
      new HashSet<>(
          Arrays.asList(
              "as", "assert", "break", "case", "catch", "class", "const", "continue", "def",
              "default", "do", "else", "enum", "extends", "false", "finally", "for", "goto",
              "if", "implements", "import", "in", "instanceof", "interface", "new", "null",
              "package", "return", "super", "switch", "this", "throw", "throws", "trait", "true",
              "try", "while"));
  private static final Set<String> JAVASCRIPT_KEYWORDS =
      new HashSet<>(
          Arrays.asList(
              "await", "break", "case", "catch", "class", "const", "continue", "debugger",
              "default", "delete", "do", "else", "export", "extends", "false", "finally", "for",
              "function", "if", "import", "in", "instanceof", "let", "new", "null", "return",
              "static", "super", "switch", "this", "throw", "true", "try", "typeof", "var",
              "void", "while", "with", "yield"));
  private static final Color CURRENT_LINE_COLOR = new Color(232, 242, 255);
  private static final Color ERROR_LINE_COLOR = new Color(255, 210, 210);

  private final UndoManager undoManager = new UndoManager();
  private final SimpleAttributeSet normal = style(new Color(35, 35, 35), false, false);
  private final SimpleAttributeSet keyword = style(new Color(0, 70, 170), true, false);
  private final SimpleAttributeSet string = style(new Color(0, 125, 65), false, false);
  private final SimpleAttributeSet comment = style(new Color(110, 115, 120), false, true);
  private final SimpleAttributeSet number = style(new Color(145, 65, 15), false, false);
  private final SimpleAttributeSet annotation = style(new Color(115, 45, 145), false, false);
  private final Highlighter.HighlightPainter currentLinePainter =
      new FullWidthHighlightPainter(CURRENT_LINE_COLOR);
  private final Highlighter.HighlightPainter errorLinePainter =
      new FullWidthHighlightPainter(ERROR_LINE_COLOR);
  private final Timer highlightTimer;
  private final LineNumberView lineNumberGutter;
  private Set<String> keywords = GROOVY_KEYWORDS;
  private final List<FoldRegion> foldRegions = new ArrayList<>();
  private final java.util.Map<Integer, Integer> lastHiddenLines = new java.util.HashMap<>();
  private final Set<Integer> foldedRegionStarts = new LinkedHashSet<>();
  private Object currentLineHighlight;
  private Object errorHighlight;
  private int errorLineNumber = -1;

  public ScriptEditor() {
    setEditorKit(new NoWrapEditorKit());
    setMargin(new Insets(4, 6, 4, 6));
    highlightTimer =
        new Timer(
            80,
            event -> {
              ((Timer) event.getSource()).stop();
              applySyntaxHighlighting();
            });
    highlightTimer.setRepeats(false);
    getDocument()
        .addDocumentListener(
            new javax.swing.event.DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent event) {
                clearErrorHighlight();
                highlightTimer.restart();
                refreshFolding();
              }

              @Override
              public void removeUpdate(DocumentEvent event) {
                clearErrorHighlight();
                highlightTimer.restart();
                refreshFolding();
              }

              @Override
              public void changedUpdate(DocumentEvent event) {}
            });
    getDocument()
        .addUndoableEditListener(
            event -> {
              if (!(event.getEdit() instanceof DocumentEvent documentEvent)
                  || documentEvent.getType() != DocumentEvent.EventType.CHANGE) {
                undoManager.addEdit(event.getEdit());
              }
            });
    addCaretListener(this::updateCurrentLineHighlight);
    addCaretListener(event -> revealCaretLine());
    lineNumberGutter = new LineNumberView(this);
    configureActions();
    updateCurrentLineHighlight(null);
    refreshFolding();
  }

  private void configureActions() {
    var actions = getActionMap();
    var inputs = getInputMap(JComponent.WHEN_FOCUSED);
    Action undo =
        new AbstractAction("Undo") {
          @Override
          public void actionPerformed(ActionEvent event) {
            try {
              if (undoManager.canUndo()) {
                undoManager.undo();
              }
            } catch (CannotUndoException ex) {
              Toolkit.getDefaultToolkit().beep();
            }
          }
        };
    Action redo =
        new AbstractAction("Redo") {
          @Override
          public void actionPerformed(ActionEvent event) {
            try {
              if (undoManager.canRedo()) {
                undoManager.redo();
              }
            } catch (CannotRedoException ex) {
              Toolkit.getDefaultToolkit().beep();
            }
          }
        };
    actions.put("scriptdock-undo", undo);
    actions.put("scriptdock-redo", redo);
    inputs.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "scriptdock-undo");
    inputs.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "scriptdock-redo");
    inputs.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
        "scriptdock-redo");

    JPopupMenu menu = new JPopupMenu();
    JMenuItem undoItem = menu.add(undo);
    JMenuItem redoItem = menu.add(redo);
    menu.addSeparator();
    JMenuItem cutItem = menu.add(actions.get(DefaultEditorKit.cutAction));
    cutItem.setText("Cut");
    JMenuItem copyItem = menu.add(actions.get(DefaultEditorKit.copyAction));
    copyItem.setText("Copy");
    JMenuItem pasteItem = menu.add(actions.get(DefaultEditorKit.pasteAction));
    pasteItem.setText("Paste");
    menu.addSeparator();
    JMenuItem commentItem = menu.add("Comment Lines");
    commentItem.addActionListener(event -> commentLines());
    JMenuItem uncommentItem = menu.add("Uncomment Lines");
    uncommentItem.addActionListener(event -> uncommentLines());
    menu.addSeparator();
    JMenuItem selectAllItem = menu.add(actions.get(DefaultEditorKit.selectAllAction));
    selectAllItem.setText("Select All");
    menu.addPopupMenuListener(
        new PopupMenuListener() {
          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
            boolean editable = isEditable() && isEnabled();
            undoItem.setEnabled(editable && undoManager.canUndo());
            redoItem.setEnabled(editable && undoManager.canRedo());
            commentItem.setEnabled(editable);
            uncommentItem.setEnabled(editable);
            boolean selected = getSelectionStart() != getSelectionEnd();
            cutItem.setEnabled(editable && selected);
            copyItem.setEnabled(selected);
            pasteItem.setEnabled(editable);
            selectAllItem.setEnabled(getDocument().getLength() > 0);
          }

          @Override
          public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {}

          @Override
          public void popupMenuCanceled(PopupMenuEvent event) {}
        });
    setComponentPopupMenu(menu);
  }

  public void resetUndoHistory() {
    undoManager.discardAllEdits();
  }

  public JComponent getLineNumberGutter() {
    return lineNumberGutter;
  }

  /** Returns the currently recognized multi-line brace-delimited regions. */
  public List<FoldRegion> getFoldRegions() {
    return List.copyOf(foldRegions);
  }

  /** Toggles the fold region whose opening brace is on {@code lineNumber}. */
  public void toggleFoldAtLine(int lineNumber) {
    FoldRegion region = foldRegionAtLine(lineNumber);
    if (region == null) {
      return;
    }
    if (!foldedRegionStarts.add(region.startOffset())) {
      foldedRegionStarts.remove(region.startOffset());
    } else {
      Element root = getDocument().getDefaultRootElement();
      if (isFoldedLine(root.getElementIndex(getCaretPosition()))) {
        setCaretPosition(region.startOffset() + 1);
      }
    }
    refreshFoldingViews();
  }

  /** Expands every fold region. */
  public void expandAllFolds() {
    if (foldedRegionStarts.isEmpty()) {
      return;
    }
    foldedRegionStarts.clear();
    refreshFoldingViews();
  }

  /** Collapses every fold region. */
  public void collapseAllFolds() {
    for (FoldRegion region : foldRegions) {
      foldedRegionStarts.add(region.startOffset());
    }
    int caretLine = getDocument().getDefaultRootElement().getElementIndex(getCaretPosition()) + 1;
    for (FoldRegion region : foldRegions) {
      if (caretLine > region.startLine()
          && caretLine <= lastHiddenLines.getOrDefault(region.startOffset(), region.endLine())) {
        setCaretPosition(region.startOffset() + 1);
        break;
      }
    }
    refreshFoldingViews();
  }

  boolean isFoldableLine(int lineIndex) {
    return foldRegionAtLine(lineIndex + 1) != null;
  }

  boolean isFoldCollapsedAtLine(int lineIndex) {
    FoldRegion region = foldRegionAtLine(lineIndex + 1);
    return region != null && foldedRegionStarts.contains(region.startOffset());
  }

  boolean isFoldedLine(int lineIndex) {
    int lineNumber = lineIndex + 1;
    for (FoldRegion region : foldRegions) {
      if (foldedRegionStarts.contains(region.startOffset())
          && lineNumber > region.startLine()
          && lineNumber <= lastHiddenLines.getOrDefault(region.startOffset(), region.endLine())) {
        return true;
      }
    }
    return false;
  }

  /** Hides the closing line only when nothing but closing punctuation follows the brace. */
  private int lastHiddenLine(FoldRegion region) {
    Element line =
        getDocument().getDefaultRootElement().getElement(region.endLine() - 1);
    int end = Math.min(line.getEndOffset(), getDocument().getLength());
    try {
      String rest =
          getDocument().getText(region.endOffset() + 1, end - region.endOffset() - 1).strip();
      return rest.matches("[)\\];,]*") ? region.endLine() : region.endLine() - 1;
    } catch (BadLocationException ex) {
      return region.endLine() - 1;
    }
  }

  private void revealCaretLine() {
    if (foldedRegionStarts.isEmpty()) {
      return;
    }
    Element root = getDocument().getDefaultRootElement();
    int lineIndex = root.getElementIndex(getCaretPosition());
    if (!isFoldedLine(lineIndex)) {
      return;
    }
    int lineNumber = lineIndex + 1;
    for (FoldRegion region : foldRegions) {
      if (lineNumber > region.startLine()
          && lineNumber <= lastHiddenLines.getOrDefault(region.startOffset(), region.endLine())) {
        foldedRegionStarts.remove(region.startOffset());
      }
    }
    refreshFoldingViews();
  }

  private FoldRegion foldRegionAtLine(int lineNumber) {
    for (FoldRegion region : foldRegions) {
      if (region.startLine() == lineNumber) {
        return region;
      }
    }
    return null;
  }

  private void refreshFolding() {
    Set<Integer> previousStarts = new LinkedHashSet<>(foldedRegionStarts);
    foldRegions.clear();
    foldRegions.addAll(findFoldRegions());
    lastHiddenLines.clear();
    for (FoldRegion region : foldRegions) {
      lastHiddenLines.put(region.startOffset(), lastHiddenLine(region));
    }
    foldedRegionStarts.clear();
    for (FoldRegion region : foldRegions) {
      if (previousStarts.contains(region.startOffset())) {
        foldedRegionStarts.add(region.startOffset());
      }
    }
    if (previousStarts.isEmpty()) {
      lineNumberGutter.revalidate();
      lineNumberGutter.repaint();
    } else {
      refreshFoldingViews();
    }
  }

  private void refreshFoldingViews() {
    View root = getUI().getRootView(this);
    if (root.getViewCount() > 0) {
      View section = root.getView(0);
      for (int index = 0; index < section.getViewCount(); index++) {
        section.getView(index).preferenceChanged(null, true, true);
      }
    }
    revalidate();
    repaint();
    lineNumberGutter.revalidate();
    lineNumberGutter.repaint();
  }

  private List<FoldRegion> findFoldRegions() {
    String text;
    try {
      text = getDocument().getText(0, getDocument().getLength());
    } catch (BadLocationException ex) {
      throw new IllegalStateException("Unable to read editor text for folding", ex);
    }
    List<FoldRegion> regions = new ArrayList<>();
    List<Integer> openBraces = new ArrayList<>();
    int index = 0;
    while (index < text.length()) {
      char current = text.charAt(index);
      if (current == '/' && index + 1 < text.length()) {
        char next = text.charAt(index + 1);
        if (next == '/') {
          int end = text.indexOf('\n', index + 2);
          index = end < 0 ? text.length() : end;
          continue;
        }
        if (next == '*') {
          int end = text.indexOf("*/", index + 2);
          index = end < 0 ? text.length() : end + 2;
          continue;
        }
      }
      if (current == '\'' || current == '"') {
        index = foldStringEnd(text, index, current);
        continue;
      } else if (current == '{') {
        openBraces.add(index);
      } else if (current == '}' && !openBraces.isEmpty()) {
        int startOffset = openBraces.remove(openBraces.size() - 1);
        Element root = getDocument().getDefaultRootElement();
        int startLine = root.getElementIndex(startOffset) + 1;
        int endLine = root.getElementIndex(index) + 1;
        if (endLine > startLine) {
          regions.add(new FoldRegion(startOffset, index, startLine, endLine));
        }
      }
      index++;
    }
    regions.sort(Comparator.comparingInt(FoldRegion::startOffset));
    return regions;
  }

  private static int foldStringEnd(String text, int start, char quote) {
    boolean triple =
        start + 2 < text.length()
            && text.charAt(start + 1) == quote
            && text.charAt(start + 2) == quote;
    if (triple) {
      return stringEnd(text, start, quote);
    }
    int index = start + 1;
    while (index < text.length()) {
      char current = text.charAt(index);
      if (current == '\\') {
        index += 2;
      } else if (current == quote) {
        return index + 1;
      } else if (current == '\n') {
        return index;
      } else {
        index++;
      }
    }
    return text.length();
  }

  public void clearErrorHighlight() {
    if (errorHighlight != null) {
      getHighlighter().removeHighlight(errorHighlight);
      errorHighlight = null;
    }
    errorLineNumber = -1;
    if (currentLineHighlight == null) {
      updateCurrentLineHighlight(null);
    }
  }

  public void highlightErrorLine(int lineNumber) {
    clearErrorHighlight();
    Element root = getDocument().getDefaultRootElement();
    int index = lineNumber - 1;
    if (index < 0 || index >= root.getElementCount()) {
      return;
    }
    Element line = root.getElement(index);
    try {
      errorHighlight =
          getHighlighter()
              .addHighlight(line.getStartOffset(), line.getEndOffset(), errorLinePainter);
      errorLineNumber = lineNumber;
      setCaretPosition(line.getStartOffset());
      Rectangle2D location = modelToView2D(line.getStartOffset());
      if (location != null) {
        scrollRectToVisible(location.getBounds());
      }
    } catch (BadLocationException ex) {
      clearErrorHighlight();
    }
  }

  public int getErrorLineNumber() {
    return errorLineNumber;
  }

  public void setEngineName(String engineName) {
    String normalized = engineName == null ? "" : engineName.trim().toLowerCase();
    keywords =
        normalized.contains("javascript")
                || normalized.contains("nashorn")
                || normalized.contains("ecmascript")
            ? JAVASCRIPT_KEYWORDS
            : GROOVY_KEYWORDS;
    highlightTimer.restart();
  }

  /** Adds {@code //} to each selected line, or to the caret's current line. */
  public void commentLines() {
    updateLineComments(true);
  }

  /** Removes one leading {@code //} from each selected line, or from the caret's current line. */
  public void uncommentLines() {
    updateLineComments(false);
  }

  private void updateLineComments(boolean comment) {
    if (!isEditable() || !isEnabled()) {
      return;
    }
    StyledDocument document = getStyledDocument();
    Element root = document.getDefaultRootElement();
    int selectionStart = getSelectionStart();
    int selectionEnd = getSelectionEnd();
    int startLine = root.getElementIndex(selectionStart);
    int endLine = root.getElementIndex(selectionEnd);
    if (selectionEnd > selectionStart
        && endLine > startLine
        && root.getElement(endLine).getStartOffset() == selectionEnd) {
      endLine--;
    }

    String text;
    try {
      text = document.getText(0, document.getLength());
      List<CommentEdit> edits = new ArrayList<>();
      for (int lineIndex = startLine; lineIndex <= endLine; lineIndex++) {
        Element line = root.getElement(lineIndex);
        int lineStart = line.getStartOffset();
        int lineEnd = Math.min(line.getEndOffset(), text.length());
        while (lineEnd > lineStart
            && (text.charAt(lineEnd - 1) == '\n' || text.charAt(lineEnd - 1) == '\r')) {
          lineEnd--;
        }
        int contentOffset = lineStart;
        while (contentOffset < lineEnd
            && Character.isWhitespace(text.charAt(contentOffset))) {
          contentOffset++;
        }
        boolean alreadyCommented =
            contentOffset + 1 < lineEnd
                && text.charAt(contentOffset) == '/'
                && text.charAt(contentOffset + 1) == '/';
        if (comment && !alreadyCommented) {
          edits.add(new CommentEdit(lineStart, true));
        } else if (!comment && alreadyCommented) {
          edits.add(new CommentEdit(contentOffset, false));
        }
      }

      for (int index = edits.size() - 1; index >= 0; index--) {
        CommentEdit edit = edits.get(index);
        if (edit.inserting()) {
          document.insertString(edit.offset(), "//", null);
        } else {
          document.remove(edit.offset(), 2);
        }
      }

      int adjustedStart = adjustPosition(selectionStart, edits);
      int adjustedEnd = adjustPosition(selectionEnd, edits);
      select(adjustedStart, adjustedEnd);
    } catch (BadLocationException ex) {
      throw new IllegalStateException("Unable to update line comments", ex);
    }
  }

  private static int adjustPosition(int position, List<CommentEdit> edits) {
    int adjusted = position;
    for (CommentEdit edit : edits) {
      if (edit.offset() < position || (edit.offset() == position && edit.inserting())) {
        adjusted += edit.inserting() ? 2 : -2;
      }
    }
    return adjusted;
  }

  private record CommentEdit(int offset, boolean inserting) {}

  private void updateCurrentLineHighlight(CaretEvent event) {
    Highlighter highlighter = getHighlighter();
    if (currentLineHighlight != null) {
      highlighter.removeHighlight(currentLineHighlight);
      currentLineHighlight = null;
    }
    try {
      Element root = getDocument().getDefaultRootElement();
      int lineIndex = root.getElementIndex(getCaretPosition());
      if (errorLineNumber == lineIndex + 1) {
        return;
      }
      Element line = root.getElement(lineIndex);
      currentLineHighlight =
          highlighter.addHighlight(
              line.getStartOffset(), line.getEndOffset(), currentLinePainter);
    } catch (BadLocationException ex) {
      // The caret moved while the document changed; the next caret event will repaint it.
    }
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    Component parent = getParent();
    return parent == null || getUI().getPreferredSize(this).width <= parent.getSize().width;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    Component parent = getParent();
    return parent == null || getUI().getPreferredSize(this).height <= parent.getSize().height;
  }

  void applySyntaxHighlighting() {
    StyledDocument document = getStyledDocument();
    String text;
    try {
      text = document.getText(0, document.getLength());
    } catch (BadLocationException ex) {
      throw new IllegalStateException("Unable to read editor text", ex);
    }
    document.setCharacterAttributes(0, text.length(), normal, true);
    int index = 0;
    while (index < text.length()) {
      char current = text.charAt(index);
      if (current == '/' && index + 1 < text.length() && text.charAt(index + 1) == '/') {
        int end = text.indexOf('\n', index + 2);
        end = end < 0 ? text.length() : end;
        apply(document, index, end, comment);
        index = end;
      } else if (current == '/' && index + 1 < text.length() && text.charAt(index + 1) == '*') {
        int end = text.indexOf("*/", index + 2);
        end = end < 0 ? text.length() : end + 2;
        apply(document, index, end, comment);
        index = end;
      } else if (current == '\'' || current == '"') {
        int end = stringEnd(text, index, current);
        apply(document, index, end, string);
        index = end;
      } else if (current == '@'
          && index + 1 < text.length()
          && Character.isJavaIdentifierStart(text.charAt(index + 1))) {
        int end = identifierEnd(text, index + 1);
        apply(document, index, end, annotation);
        index = end;
      } else if (Character.isDigit(current)) {
        int end = index + 1;
        while (end < text.length()
            && (Character.isDigit(text.charAt(end))
                || text.charAt(end) == '.'
                || Character.toLowerCase(text.charAt(end)) == 'x'
                || (text.charAt(end) >= 'a' && text.charAt(end) <= 'f')
                || (text.charAt(end) >= 'A' && text.charAt(end) <= 'F'))) {
          end++;
        }
        apply(document, index, end, number);
        index = end;
      } else if (Character.isJavaIdentifierStart(current)) {
        int end = identifierEnd(text, index);
        if (keywords.contains(text.substring(index, end))) {
          apply(document, index, end, keyword);
        }
        index = end;
      } else {
        index++;
      }
    }
  }

  private static int stringEnd(String text, int start, char quote) {
    boolean triple =
        start + 2 < text.length()
            && text.charAt(start + 1) == quote
            && text.charAt(start + 2) == quote;
    int index = start + (triple ? 3 : 1);
    while (index < text.length()) {
      if (text.charAt(index) == '\\') {
        index = Math.min(text.length(), index + 2);
      } else if (triple
          && index + 2 < text.length()
          && text.charAt(index) == quote
          && text.charAt(index + 1) == quote
          && text.charAt(index + 2) == quote) {
        return index + 3;
      } else if (!triple && text.charAt(index) == quote) {
        return index + 1;
      } else {
        index++;
      }
    }
    return text.length();
  }

  private static int identifierEnd(String text, int start) {
    int end = start + 1;
    while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
      end++;
    }
    return end;
  }

  private static void apply(
      StyledDocument document, int start, int end, SimpleAttributeSet attributes) {
    document.setCharacterAttributes(start, Math.max(0, end - start), attributes, true);
  }

  private static SimpleAttributeSet style(Color color, boolean bold, boolean italic) {
    SimpleAttributeSet attributes = new SimpleAttributeSet();
    StyleConstants.setForeground(attributes, color);
    StyleConstants.setBold(attributes, bold);
    StyleConstants.setItalic(attributes, italic);
    return attributes;
  }

  public record FoldRegion(int startOffset, int endOffset, int startLine, int endLine) {}

  private final class NoWrapEditorKit extends StyledEditorKit {
    private final ViewFactory viewFactory =
        element -> {
          String kind = element.getName();
          if (AbstractDocument.ContentElementName.equals(kind)) {
            return new LabelView(element);
          } else if (AbstractDocument.ParagraphElementName.equals(kind)) {
            return new NoWrapParagraphView(element);
          } else if (AbstractDocument.SectionElementName.equals(kind)) {
            return new BoxView(element, View.Y_AXIS);
          } else if (StyleConstants.ComponentElementName.equals(kind)) {
            return new ComponentView(element);
          } else if (StyleConstants.IconElementName.equals(kind)) {
            return new IconView(element);
          }
          return new LabelView(element);
        };

    @Override
    public ViewFactory getViewFactory() {
      return viewFactory;
    }
  }

  private record FullWidthHighlightPainter(Color color) implements Highlighter.HighlightPainter {

    @Override
      public void paint(
            Graphics graphics, int start, int end, Shape bounds, JTextComponent component) {
        try {
          Rectangle2D location = component.modelToView2D(start);
          if (location != null) {
            graphics.setColor(color);
            graphics.fillRect(0, (int) location.getY(), component.getWidth(), (int) location.getHeight());
          }
        } catch (BadLocationException ex) {
          // The document changed before paint; the next repaint will use valid offsets.
        }
      }
    }

  private final class NoWrapParagraphView extends ParagraphView {
    private NoWrapParagraphView(Element element) {
      super(element);
    }

    private boolean folded() {
      int lineIndex = getDocument().getDefaultRootElement().getElementIndex(getStartOffset());
      return isFoldedLine(lineIndex);
    }

    @Override
    public boolean isVisible() {
      return !folded();
    }

    @Override
    public void paint(Graphics graphics, Shape allocation) {
      if (!folded()) {
        super.paint(graphics, allocation);
      }
    }

    @Override
    public float getPreferredSpan(int axis) {
      return axis == View.Y_AXIS && folded() ? 0 : super.getPreferredSpan(axis);
    }

    @Override
    public float getMaximumSpan(int axis) {
      return axis == View.Y_AXIS && folded() ? 0 : super.getMaximumSpan(axis);
    }

    @Override
    public void layout(int width, int height) {
      super.layout(Integer.MAX_VALUE / 2, height);
    }

    @Override
    public float getMinimumSpan(int axis) {
      if (axis == View.X_AXIS) {
        return getPreferredSpan(axis);
      }
      return folded() ? 0 : super.getMinimumSpan(axis);
    }
  }
}
