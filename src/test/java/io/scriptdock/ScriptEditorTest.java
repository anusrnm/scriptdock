package io.scriptdock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ScriptEditorTest {
  @Test
  void exposesItsLineNumberGutterForDirectEmbedding() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> assertNotNull(new ScriptEditor().getLineNumberGutter()));
  }

  @Test
  void highlightsTheCurrentLineAndMovesItWithTheCaret() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ScriptEditor editor = new ScriptEditor();
          editor.setText("first\nsecond");
          assertEquals(1, editor.getHighlighter().getHighlights().length);

          editor.setCaretPosition(7);

          assertEquals(1, editor.getHighlighter().getHighlights().length);
          assertEquals(6, editor.getHighlighter().getHighlights()[0].getStartOffset());
        });
  }

  @Test
  void errorHighlightReplacesCurrentLineTintOnTheFailingLine() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ScriptEditor editor = new ScriptEditor();
          editor.setText("first\nsecond\nthird");

          editor.highlightErrorLine(2);

          assertEquals(2, editor.getErrorLineNumber());
          assertEquals(1, editor.getHighlighter().getHighlights().length);
          assertEquals(6, editor.getHighlighter().getHighlights()[0].getStartOffset());
          assertEquals(13, editor.getHighlighter().getHighlights()[0].getEndOffset());
        });
  }

  @Test
  void editingClearsTheErrorButKeepsCurrentLineHighlighting() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ScriptEditor editor = new ScriptEditor();
          editor.setText("first\nsecond");
          editor.highlightErrorLine(2);

          editor.replaceSelection("changed");

          assertEquals(-1, editor.getErrorLineNumber());
          assertEquals(1, editor.getHighlighter().getHighlights().length);
        });
  }

  @Test
  void commentsAtLineStartAndUncommentsWithoutChangingIndentation() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ScriptEditor editor = new ScriptEditor();
          editor.setText("  first\n\tsecond\nthird");
          editor.select(0, editor.getDocument().getLength());

          editor.commentLines();

          assertEquals("//  first\n//\tsecond\n//third", editor.getText());
          editor.uncommentLines();

          assertEquals("  first\n\tsecond\nthird", editor.getText());
        });
  }

  @Test
  void commentsOnlyTheCaretLineAndDoesNotDuplicateCommentMarkers() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ScriptEditor editor = new ScriptEditor();
          editor.setText("first\n  //second\nthird");
          editor.setCaretPosition(9);

          editor.commentLines();

          assertEquals("first\n  //second\nthird", editor.getText());
          editor.setCaretPosition(editor.getText().length());
          editor.commentLines();

          assertEquals("first\n  //second\n//third", editor.getText());
        });
  }

  @Test
  void excludesLineAtSelectionEndWhenSelectionStopsAtItsBeginning() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ScriptEditor editor = new ScriptEditor();
          editor.setText("first\nsecond");
          editor.select(0, "first\n".length());

          editor.commentLines();

          assertEquals("//first\nsecond", editor.getText());
        });
  }

  @Test
  void recognizesNestedBraceRegionsButIgnoresStringsAndComments() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ScriptEditor editor = new ScriptEditor();
          editor.setText(
              "def outer = \"not { a fold }\"\n"
                  + "if (true) {\n"
                  + "  // } is not a closing brace\n"
                  + "  if (true) {\n"
                  + "    println 'inside'\n"
                  + "  }\n"
                  + "}\n");

          assertEquals(2, editor.getFoldRegions().size());
          assertEquals(2, editor.getFoldRegions().get(0).startLine());
          assertEquals(7, editor.getFoldRegions().get(0).endLine());
          assertEquals(4, editor.getFoldRegions().get(1).startLine());
          assertEquals(6, editor.getFoldRegions().get(1).endLine());
        });
  }

  @Test
  void foldingDoesNotChangeDocumentTextAndCanBeExpandedAgain() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ScriptEditor editor = new ScriptEditor();
          String source = "if (true) {\n  println 'inside'\n}\n";
          editor.setText(source);

          editor.toggleFoldAtLine(1);

          assertEquals(source, editor.getText());
          assertTrue(editor.isFoldedLine(1));
          assertTrue(editor.isFoldedLine(2));
          assertFalse(editor.isFoldedLine(0));

          editor.expandAllFolds();

          assertFalse(editor.isFoldedLine(1));
          assertFalse(editor.isFoldedLine(2));
        });
  }

  @Test
  void movingTheCaretIntoACollapsedBlockExpandsIt() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ScriptEditor editor = new ScriptEditor();
          editor.setText("if (a) {\n  if (b) {\n    x()\n  }\n}\n");
          editor.collapseAllFolds();

          editor.setCaretPosition(editor.getText().indexOf("x()"));

          assertFalse(editor.isFoldedLine(2));
          assertFalse(editor.isFoldCollapsedAtLine(0));
          assertFalse(editor.isFoldCollapsedAtLine(1));
        });
  }

  @Test
  void keepsClosingLineVisibleWhenMoreCodeFollowsTheBrace() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ScriptEditor editor = new ScriptEditor();
          editor.setText("if (a) {\n  x()\n} else {\n  y()\n}");

          editor.toggleFoldAtLine(1);
          assertTrue(editor.isFoldedLine(1));
          assertFalse(editor.isFoldedLine(2));

          editor.toggleFoldAtLine(3);
          assertTrue(editor.isFoldedLine(3));
          assertTrue(editor.isFoldedLine(4));
        });
  }

  @Test
  void collapsingAFoldRemovesHiddenLinesFromTheLayout() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ScriptEditor editor = new ScriptEditor();
          editor.setText("if (ok) {\n  a()\n  b()\n  c()\n}\nafter\n");
          editor.setSize(400, 400);
          int expanded = editor.getPreferredSize().height;
          editor.setCaretPosition(editor.getText().indexOf("b()"));

          editor.toggleFoldAtLine(1);

          assertTrue(editor.getPreferredSize().height < expanded);
          assertEquals(editor.getText().indexOf('{') + 1, editor.getCaretPosition());
          editor.toggleFoldAtLine(1);
          assertEquals(expanded, editor.getPreferredSize().height);
        });
  }

  @Test
  void tripleQuotedStringsWithInnerQuotesDoNotHideLaterRegions() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ScriptEditor editor = new ScriptEditor();
          editor.setText(
              "def body = \"\"\"\\\n<a v=\"1.0\" x='y'>${it}</a>\n\"\"\"\n"
                  + "if (ok) {\n  println \"it's fine\"\n}\n");

          assertEquals(1, editor.getFoldRegions().size());
          assertEquals(4, editor.getFoldRegions().get(0).startLine());

          editor.toggleFoldAtLine(4);

          assertTrue(editor.isFoldCollapsedAtLine(3));
          assertFalse(editor.isFoldedLine(3));
          assertTrue(editor.isFoldedLine(4));
        });
  }
}
