package io.scriptdock;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

/** Embeddable, host-neutral tabbed script console. */
public class ScriptDockFrame extends JFrame {
  private static final Color READY = Color.DARK_GRAY;
  private static final Color RUNNING = new Color(0, 128, 0);
  private static final Color FAILED = Color.RED.darker();
  private static final Color FILE_ICON = new Color(55, 90, 140);

  private final ScriptDockRunner runner;
  private final ScriptDockOptions options;
  private final ScriptDockPreferences preferences;
  private final ScriptDockPreferences classpathStore;
  private final JComboBox<String> engine = new JComboBox<>();
  private final JTabbedPane editorTabs = new JTabbedPane();
  private final List<EditorTab> tabs = new ArrayList<>();
  private final JTextArea output = new JTextArea();
  private final JButton runStop = new JButton();
  private final JButton newButton = new JButton(SwingIcons.newDocument(FILE_ICON));
  private final JButton openButton = new JButton(SwingIcons.open(FILE_ICON));
  private final JButton saveButton = new JButton(SwingIcons.save(FILE_ICON));
  private final JButton clearButton =
      new JButton(SwingIcons.clear(new Color(120, 70, 40)));
  private final JMenuItem newItem = new JMenuItem("New Tab");
  private final JMenuItem openItem = new JMenuItem("Open...");
  private final JMenuItem saveItem = new JMenuItem("Save");
  private final JMenuItem saveAsItem = new JMenuItem("Save As...");
  private final JMenuItem closeTabItem = new JMenuItem("Close Tab");
  private final JMenuItem closeItem = new JMenuItem("Close Window");
  private final JMenuItem classpathItem = new JMenuItem("Classpath...");
  private final JLabel stateLabel = new JLabel("Ready");
  private final JLabel durationLabel = new JLabel("Time: -");
  private final FindReplaceDialog findDialog;
  private final Timer durationTimer = new Timer(1000, event -> updateDuration());

  private File currentDirectory;
  private Font editorFont;
  private JSplitPane split;
  private boolean horizontalSplit;
  private int verticalDividerLocation = -1;
  private int horizontalDividerLocation = -1;
  private Rectangle normalBounds;
  private boolean restoringSession;
  private boolean refreshingEngines;
  private List<File> classpath = List.of();
  private long startedAt;
  private long runSequence;
  private boolean executionActive;
  private int untitledSequence = 1;
  private final MouseAdapter newTabMouseListener =
      new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event) {
          if (event.getButton() == MouseEvent.BUTTON1
              && event.getClickCount() == 2
              && !executionActive
              && isInTabStrip(event)) {
            addTab();
          }
        }
      };

  private final class EditorTab {
    private final int untitledNumber = untitledSequence++;
    private final ScriptEditor editor = new ScriptEditor();
    private final JScrollPane scrollPane = new JScrollPane(editor);
    private final JPanel header = new JPanel(new BorderLayout(4, 0));
    private final JLabel label = new JLabel();
    private final JButton close = new JButton("x");
    private File file;
    private boolean dirty;

    private EditorTab() {
      editor.setFont(editorFont);
      editor.setEngineName(selectedEngine());
      scrollPane.setRowHeaderView(editor.getLineNumberGutter());
      scrollPane.setPreferredSize(new Dimension(680, 360));
      header.setOpaque(false);
      close.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));
      close.setContentAreaFilled(false);
      close.setFocusable(false);
      close.setToolTipText("Close this tab");
      close.addActionListener(event -> closeTab(this));
      header.add(label, BorderLayout.CENTER);
      header.add(close, BorderLayout.EAST);
      editor
          .getDocument()
          .addDocumentListener(
              new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                  markDirty(EditorTab.this);
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                  markDirty(EditorTab.this);
                }

                @Override
                public void changedUpdate(DocumentEvent event) {}
              });
    }
  }

  public ScriptDockFrame(Frame owner, ScriptDockRunner runner, ScriptDockOptions options) {
    this.runner = Objects.requireNonNull(runner, "runner");
    this.options = Objects.requireNonNull(options, "options");
    preferences = options.preferences();
    classpathStore = options.classpathPreferences();
    horizontalSplit = preferences != null && preferences.horizontalSplit();
    verticalDividerLocation = preferences == null ? -1 : preferences.dividerLocation();
    horizontalDividerLocation =
        preferences == null ? -1 : preferences.horizontalDividerLocation();
    currentDirectory =
        preferences == null
            ? options.initialDirectory()
            : preferences.directory(options.initialDirectory());
    editorFont =
        preferences == null ? options.editorFont() : preferences.font(options.editorFont());
    classpath = classpathStore.classpath();
    runner.setClasspath(classpath);
    List<String> engines = Jsr223ScriptRunner.availableEngineNames(classpath);
    refreshEngines(
        engines,
        preferences == null
            ? Jsr223ScriptRunner.DEFAULT_ENGINE
            : preferences.engine(engines, Jsr223ScriptRunner.DEFAULT_ENGINE));
    findDialog = new FindReplaceDialog(this, this::getSelectedEditor);
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent event) {
            closeWindow();
          }

          @Override
          public void windowOpened(WindowEvent event) {
            restoreDividerLocation();
          }
        });
    buildMenu();
    updateClasspathTooltip();
    buildContent();
    wireActions();
    restoreTabs();
    setState("Ready", READY);
    updateControls();
    pack();
    setMinimumSize(new Dimension(800, 520));
    restoreWindow(owner);
  }

  private void buildMenu() {
    JMenu file = new JMenu("File");
    newItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
    openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
    saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
    saveAsItem.setAccelerator(
        KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    closeTabItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK));
    closeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.ALT_DOWN_MASK));
    file.add(newItem);
    file.add(openItem);
    file.add(saveItem);
    file.add(saveAsItem);
    file.addSeparator();
    file.add(closeTabItem);
    file.add(closeItem);

    JMenu edit = new JMenu("Edit");
    JMenuItem find = new JMenuItem("Find/Replace...");
    JMenuItem next = new JMenuItem("Find Next");
    JMenuItem previous = new JMenuItem("Find Previous");
    JMenuItem commentLines = new JMenuItem("Comment Lines");
    JMenuItem uncommentLines = new JMenuItem("Uncomment Lines");
    find.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
    next.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
    previous.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK));
    commentLines.setAccelerator(
        KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, InputEvent.CTRL_DOWN_MASK));
    uncommentLines.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_SLASH, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    find.addActionListener(event -> findDialog.showFor(getSelectedEditor().getSelectedText()));
    next.addActionListener(event -> findDialog.next());
    previous.addActionListener(event -> findDialog.previous());
    commentLines.addActionListener(event -> getSelectedEditor().commentLines());
    uncommentLines.addActionListener(event -> getSelectedEditor().uncommentLines());
    edit.add(find);
    edit.add(next);
    edit.add(previous);
    edit.addSeparator();
    edit.add(commentLines);
    edit.add(uncommentLines);

    JMenu view = new JMenu("View");
    JMenuItem font = new JMenuItem("Font...");
    JMenuItem larger = new JMenuItem("Larger Font");
    JMenuItem smaller = new JMenuItem("Smaller Font");
    JMenuItem reset = new JMenuItem("Reset Font");
    JMenuItem boundVariables = new JMenuItem("Bound Variables...");
    JCheckBoxMenuItem horizontalSplitItem = new JCheckBoxMenuItem("Horizontal Split");
    horizontalSplitItem.setSelected(horizontalSplit);
    larger.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK));
    smaller.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
    reset.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
    font.addActionListener(event -> chooseFont());
    larger.addActionListener(event -> resizeFont(1));
    smaller.addActionListener(event -> resizeFont(-1));
    reset.addActionListener(event -> applyEditorFont(options.editorFont()));
    boundVariables.addActionListener(event -> showBoundVariables());
    horizontalSplitItem.addActionListener(event -> setHorizontalSplit(horizontalSplitItem.isSelected()));
    view.add(font);
    view.addSeparator();
    view.add(larger);
    view.add(smaller);
    view.add(reset);
    view.addSeparator();
    view.add(boundVariables);
    view.addSeparator();
    view.add(horizontalSplitItem);

    JMenu run = new JMenu("Run");
    classpathItem.addActionListener(event -> editClasspath());
    run.add(classpathItem);

    JMenuBar menuBar = new JMenuBar();
    menuBar.add(file);
    menuBar.add(edit);
    menuBar.add(view);
    menuBar.add(run);
    setJMenuBar(menuBar);
  }

  private void editClasspath() {
    if (executionActive) {
      return;
    }
    List<File> edited = ClasspathDialog.edit(this, classpath, currentDirectory);
    if (edited == null || edited.equals(classpath)) {
      return;
    }
    classpath = List.copyOf(edited);
    runner.setClasspath(classpath);
    classpathStore.saveClasspath(classpath);
    classpathStore.flush();
    refreshEngines(Jsr223ScriptRunner.availableEngineNames(classpath), selectedEngine());
    updateClasspathTooltip();
    setIdleState("Classpath updated");
  }

  private void showBoundVariables() {
    Map<String, Object> bindings = runner.getBindings();
    if (bindings.isEmpty()) {
      JOptionPane.showMessageDialog(
          this, "No bound variables.", "Bound Variables", JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    Object[][] rows = new Object[bindings.size()][2];
    int row = 0;
    for (Map.Entry<String, Object> binding : bindings.entrySet()) {
      rows[row][0] = binding.getKey();
      rows[row][1] = String.valueOf(binding.getValue());
      row++;
    }
    DefaultTableModel model =
        new DefaultTableModel(rows, new Object[] {"Name", "Value"}) {
          @Override
          public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
          }
        };
    JTable table = new JTable(model);
    table.setAutoCreateRowSorter(true);
    table.setFillsViewportHeight(true);
    table.setPreferredScrollableViewportSize(
        new Dimension(600, Math.min(280, Math.max(60, bindings.size() * table.getRowHeight()))));
    JOptionPane.showMessageDialog(
        this, new JScrollPane(table), "Bound Variables", JOptionPane.PLAIN_MESSAGE);
  }

  private void refreshEngines(List<String> names, String preferred) {
    refreshingEngines = true;
    try {
      engine.removeAllItems();
      names.forEach(engine::addItem);
      engine.setSelectedItem(
          names.contains(preferred) ? preferred : Jsr223ScriptRunner.DEFAULT_ENGINE);
    } finally {
      refreshingEngines = false;
    }
    sizeEngineSelector();
    engine.revalidate();
    tabs.forEach(tab -> tab.editor.setEngineName(selectedEngine()));
  }

  private void updateClasspathTooltip() {
    classpathItem.setToolTipText(
        classpath.isEmpty()
            ? "Add JAR files or class folders for scripts"
            : classpath.size() + " classpath entr" + (classpath.size() == 1 ? "y" : "ies"));
  }

  private void buildContent() {
    configureButton(newButton, "New script (Ctrl+N)", "New");
    configureButton(openButton, "Open a script (Ctrl+O)", "Open");
    configureButton(saveButton, "Save the script (Ctrl+S)", "Save");
    configureButton(clearButton, "Clear output", "Clear Output");
    runStop.getAccessibleContext().setAccessibleName("Run/Stop");
    JToolBar toolbar = new JToolBar();
    toolbar.setFloatable(false);
    toolbar.add(newButton);
    toolbar.add(openButton);
    toolbar.add(saveButton);
    toolbar.addSeparator();
    toolbar.add(runStop);
    toolbar.add(clearButton);
    toolbar.addSeparator();
    toolbar.add(new JLabel("Engine: "));
    sizeEngineSelector();
    toolbar.add(engine);
    toolbar.add(Box.createHorizontalGlue());

    output.setEditable(false);
    output.setBackground(new Color(255, 255, 204));
    output.setFont(editorFont);
    JScrollPane outputScroll = new JScrollPane(output);
    outputScroll.setBorder(BorderFactory.createTitledBorder("Output"));
    outputScroll.setPreferredSize(
        horizontalSplit ? new Dimension(320, 360) : new Dimension(680, 160));
    split =
        new JSplitPane(
            horizontalSplit ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT,
            editorTabs,
            outputScroll);
    split.setOneTouchExpandable(true);
    split.setResizeWeight(horizontalSplit ? 0.5 : 0.7);

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(toolbar, BorderLayout.NORTH);
    getContentPane().add(split, BorderLayout.CENTER);
    getContentPane().add(statusBar(), BorderLayout.SOUTH);
  }

  private void wireActions() {
    engine.addActionListener(
        event -> {
          tabs.forEach(tab -> tab.editor.setEngineName(selectedEngine()));
          if (preferences != null && !refreshingEngines) {
            preferences.saveEngine(selectedEngine());
          }
        });
    newItem.addActionListener(event -> addTab());
    newButton.addActionListener(event -> addTab());
    openItem.addActionListener(event -> open());
    openButton.addActionListener(event -> open());
    saveItem.addActionListener(event -> saveCurrent(selectedTab().file));
    saveButton.addActionListener(event -> saveCurrent(selectedTab().file));
    saveAsItem.addActionListener(event -> saveCurrent(null));
    closeTabItem.addActionListener(event -> closeTab(selectedTab()));
    closeItem.addActionListener(event -> closeWindow());
    clearButton.addActionListener(event -> output.setText(""));
    runStop.addActionListener(event -> toggleRun());
    editorTabs.addChangeListener(
        event -> {
          updateSelectedTab();
          persistSession();
        });
    editorTabs.addMouseListener(newTabMouseListener);
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentMoved(ComponentEvent event) {
            rememberNormalBounds();
          }

          @Override
          public void componentResized(ComponentEvent event) {
            rememberNormalBounds();
          }
        });
    getRootPane()
        .registerKeyboardAction(
            event -> toggleRun(),
            KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    for (int key : new int[] {KeyEvent.VK_ADD, KeyEvent.VK_PLUS}) {
      getRootPane()
          .registerKeyboardAction(
              event -> resizeFont(1),
              KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK),
              JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
    getRootPane()
        .registerKeyboardAction(
            event -> resizeFont(-1),
            KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  private void setHorizontalSplit(boolean horizontal) {
    if (horizontalSplit == horizontal) {
      return;
    }
    saveDividerLocation();
    horizontalSplit = horizontal;
    split.setOrientation(horizontal ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT);
    split.setResizeWeight(horizontal ? 0.5 : 0.7);
    int location = horizontal ? horizontalDividerLocation : verticalDividerLocation;
    SwingUtilities.invokeLater(
        () -> {
          if (location > 0) {
            split.setDividerLocation(location);
          } else {
            split.setDividerLocation(horizontal ? 0.5 : 0.7);
          }
        });
    if (preferences != null) {
      preferences.saveHorizontalSplit(horizontalSplit);
      preferences.flush();
    }
  }

  private void saveDividerLocation() {
    if (split == null || split.getDividerLocation() <= 0) {
      return;
    }
    if (horizontalSplit) {
      horizontalDividerLocation = split.getDividerLocation();
      if (preferences != null) {
        preferences.saveHorizontalDividerLocation(horizontalDividerLocation);
      }
    } else {
      verticalDividerLocation = split.getDividerLocation();
      if (preferences != null) {
        preferences.saveDividerLocation(verticalDividerLocation);
      }
    }
  }

  public final void applyEditorFont(Font font) {
    if (font == null) {
      return;
    }
    editorFont = font;
    tabs.forEach(tab -> tab.editor.setFont(font));
    output.setFont(font);
    if (preferences != null) {
      preferences.saveFont(font);
    }
  }

  private void resizeFont(int delta) {
    int size =
        Math.max(
            ScriptDockPreferences.MIN_FONT_SIZE,
            Math.min(ScriptDockPreferences.MAX_FONT_SIZE, editorFont.getSize() + delta));
    if (size != editorFont.getSize()) {
      applyEditorFont(editorFont.deriveFont((float) size));
    }
  }

  private void chooseFont() {
    String[] families =
        GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    JComboBox<String> family = new JComboBox<>(families);
    if (Arrays.asList(families).contains(editorFont.getFamily())) {
      family.setSelectedItem(editorFont.getFamily());
    } else {
      family.insertItemAt(editorFont.getFamily(), 0);
      family.setSelectedIndex(0);
    }
    JSpinner size =
        new JSpinner(
            new SpinnerNumberModel(
                editorFont.getSize(),
                ScriptDockPreferences.MIN_FONT_SIZE,
                ScriptDockPreferences.MAX_FONT_SIZE,
                1));
    JPanel panel = new JPanel();
    panel.add(new JLabel("Family:"));
    panel.add(family);
    panel.add(new JLabel("Size:"));
    panel.add(size);
    int choice =
        JOptionPane.showConfirmDialog(
            this, panel, "Editor Font", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    if (choice == JOptionPane.OK_OPTION && family.getSelectedItem() != null) {
      applyEditorFont(
          new Font(
              family.getSelectedItem().toString(),
              editorFont.getStyle(),
              ((Number) size.getValue()).intValue()));
    }
  }

  public final ScriptEditor getSelectedEditor() {
    return selectedTab().editor;
  }

  public final void saveCurrent(File target) {
    EditorTab tab = selectedTab();
    File file = target;
    if (file == null) {
      JFileChooser chooser = chooser("Save Script");
      chooser.setSelectedFile(tab.file == null ? new File(defaultFileName()) : tab.file);
      if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
        return;
      }
      file = chooser.getSelectedFile();
    }
    try {
      Files.writeString(file.toPath(), tab.editor.getText());
      tab.file = file;
      tab.dirty = false;
      rememberDirectory(file);
      updateTab(tab);
      persistSession();
      setIdleState("Saved");
    } catch (IOException ex) {
      showError("Could not write the script file:\n" + ex.getMessage());
    }
  }

  private void open() {
    JFileChooser chooser =
        chooser(
            "Open Script",
            directoryForOpen(selectedTab().file, classpath, currentDirectory));
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    File file = chooser.getSelectedFile();
    try {
      String script = Files.readString(file.toPath());
      EditorTab tab = reusableEmptyTab();
      if (tab == null) {
        tab = addTab();
      }
      load(tab, file, script);
      rememberDirectory(file);
      editorTabs.setSelectedComponent(tab.scrollPane);
      persistSession();
      setIdleState("Opened");
    } catch (IOException ex) {
      showError("Could not read the script file:\n" + ex.getMessage());
    }
  }

  private void load(EditorTab tab, File file, String script) {
    tab.editor.setText(script);
    tab.editor.setCaretPosition(0);
    tab.editor.resetUndoHistory();
    tab.file = file;
    tab.dirty = false;
    updateTab(tab);
  }

  private void restoreTabs() {
    if (preferences == null) {
      addTab();
      return;
    }
    restoringSession = true;
    try {
      List<File> files = preferences.tabs();
      int selected = preferences.selectedTab();
      EditorTab selectedTab = null;
      for (int index = 0; index < files.size(); index++) {
        File file = files.get(index);
        if (!file.isFile() || !file.canRead()) {
          continue;
        }
        String script;
        try {
          script = Files.readString(file.toPath());
        } catch (IOException ex) {
          continue;
        }
        EditorTab tab = addTab();
        load(tab, file, script);
        if (index == selected) {
          selectedTab = tab;
        }
      }
      if (tabs.isEmpty()) {
        addTab();
      } else {
        editorTabs.setSelectedComponent(
            (selectedTab == null ? tabs.get(0) : selectedTab).scrollPane);
      }
    } finally {
      restoringSession = false;
    }
  }

  private void persistSession() {
    if (preferences == null || restoringSession) {
      return;
    }
    List<File> files = new ArrayList<>();
    int selected = -1;
    int selectedIndex = editorTabs.getSelectedIndex();
    for (int index = 0; index < tabs.size(); index++) {
      EditorTab tab = tabs.get(index);
      if (tab.file != null) {
        if (index == selectedIndex) {
          selected = files.size();
        }
        files.add(tab.file);
      }
    }
    preferences.saveTabs(files, selected);
  }

  private void restoreWindow(Frame owner) {
    Rectangle bounds = preferences == null ? null : preferences.windowBounds();
    if (bounds != null && ScriptDockPreferences.isVisibleOnScreens(bounds, screenBounds())) {
      setBounds(bounds);
    } else {
      setLocationRelativeTo(owner);
    }
    normalBounds = getBounds();
    if (preferences != null && preferences.windowMaximized()) {
      setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
    }
  }

  private void restoreDividerLocation() {
    int location = horizontalSplit ? horizontalDividerLocation : verticalDividerLocation;
    if (location > 0) {
      SwingUtilities.invokeLater(() -> split.setDividerLocation(location));
    }
  }

  private boolean isInTabStrip(MouseEvent event) {
    java.awt.Component content = editorTabs.getSelectedComponent();
    return content == null || !content.getBounds().contains(event.getPoint());
  }

  private void rememberNormalBounds() {
    if ((getExtendedState() & Frame.MAXIMIZED_BOTH) == 0 && isShowing()) {
      normalBounds = getBounds();
    }
  }

  private void persistWindow() {
    if (preferences == null) {
      return;
    }
    preferences.saveWindow(normalBounds, (getExtendedState() & Frame.MAXIMIZED_BOTH) != 0);
    saveDividerLocation();
    preferences.saveHorizontalSplit(horizontalSplit);
    persistSession();
    preferences.flush();
  }

  private static List<Rectangle> screenBounds() {
    List<Rectangle> screens = new ArrayList<>();
    for (GraphicsDevice device :
        GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
      screens.add(device.getDefaultConfiguration().getBounds());
    }
    return screens;
  }

  private JFileChooser chooser(String title) {
    return chooser(title, currentDirectory);
  }

  private JFileChooser chooser(String title, File directory) {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(title);
    chooser.setFileFilter(
        new FileNameExtensionFilter("Script files (*.groovy, *.js, *.txt)", "groovy", "js", "txt"));
    if (directory != null && directory.isDirectory()) {
      chooser.setCurrentDirectory(directory);
    }
    return chooser;
  }

  static File directoryForOpen(File activeFile, List<File> classpath, File fallback) {
    if (activeFile != null) {
      File parent = activeFile.getAbsoluteFile().getParentFile();
      if (parent != null && parent.isDirectory()) {
        return parent;
      }
    }
    for (File entry : classpath) {
      if (entry.isDirectory()) {
        return entry;
      }
    }
    return fallback;
  }

  private EditorTab addTab() {
    EditorTab tab = new EditorTab();
    tabs.add(tab);
    editorTabs.addTab("", tab.scrollPane);
    int index = editorTabs.indexOfComponent(tab.scrollPane);
    editorTabs.setTabComponentAt(index, tab.header);
    editorTabs.setSelectedIndex(index);
    updateTab(tab);
    tab.editor.requestFocusInWindow();
    return tab;
  }

  private void closeTab(EditorTab tab) {
    if (executionActive || !tabs.contains(tab) || !maybeSave(tab, "closing this tab")) {
      return;
    }
    tabs.remove(tab);
    editorTabs.remove(tab.scrollPane);
    if (tabs.isEmpty()) {
      addTab();
    } else {
      updateSelectedTab();
    }
    persistSession();
  }

  private void closeWindow() {
    for (EditorTab tab : new ArrayList<>(tabs)) {
      if (!maybeSave(tab, "closing")) {
        return;
      }
    }
    persistWindow();
    if (options.exitOnClose()) {
      dispose();
      System.exit(0);
    } else {
      setVisible(false);
    }
  }

  private boolean maybeSave(EditorTab tab, String action) {
    if (!tab.dirty) {
      return true;
    }
    editorTabs.setSelectedComponent(tab.scrollPane);
    int choice =
        JOptionPane.showConfirmDialog(
            this,
            "Save changes to " + tabName(tab) + " before " + action + "?",
            options.title(),
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE);
    if (choice == JOptionPane.YES_OPTION) {
      saveCurrent(tab.file);
      return !tab.dirty;
    }
    return choice == JOptionPane.NO_OPTION;
  }

  private void toggleRun() {
    if (executionActive) {
      executionActive = false;
      runSequence++;
      runner.stop();
      appendLineBreakIfNeeded();
      output.append("Script execution was stopped\n");
      output.setCaretPosition(output.getDocument().getLength());
      setState("Stopped", FAILED);
      durationTimer.stop();
      updateDuration();
      updateControls();
      return;
    }
    EditorTab tab = selectedTab();
    long thisRun = ++runSequence;
    executionActive = true;
    tab.editor.clearErrorHighlight();
    output.setText("");
    startedAt = System.nanoTime();
    setState("Running", RUNNING);
    updateDuration();
    durationTimer.restart();
    updateControls();
    runner.run(
        tab.editor.getText(),
        selectedEngine(),
        new ScriptDockRunner.Listener() {
          @Override
          public void output(String text) {
            if (thisRun != runSequence || text == null || text.isEmpty()) {
              return;
            }
            output.append(text);
            output.setCaretPosition(output.getDocument().getLength());
          }

          @Override
          public void completed(String text) {
            if (thisRun != runSequence) {
              return;
            }
            finish(text, "Finished", RUNNING);
          }

          @Override
          public void failed(String message, int lineNumber) {
            if (thisRun != runSequence) {
              return;
            }
            if (lineNumber > 0) {
              editorTabs.setSelectedComponent(tab.scrollPane);
              tab.editor.highlightErrorLine(lineNumber);
            }
            finish(
                message,
                lineNumber > 0 ? "Failed (line " + lineNumber + ")" : "Failed",
                FAILED);
          }
        });
    updateControls();
  }

  private void finish(String text, String state, Color color) {
    executionActive = false;
    durationTimer.stop();
    updateDuration();
    if (text != null && !text.isEmpty()) {
      appendLineBreakIfNeeded();
      output.append(text);
      if (!text.endsWith("\n")) {
        output.append("\n");
      }
      output.setCaretPosition(output.getDocument().getLength());
    }
    setState(state, color);
    updateControls();
  }

  private void appendLineBreakIfNeeded() {
    int length = output.getDocument().getLength();
    if (length == 0) {
      return;
    }
    try {
      if (!"\n".equals(output.getDocument().getText(length - 1, 1))) {
        output.append("\n");
      }
    } catch (javax.swing.text.BadLocationException ignored) {
      output.append("\n");
    }
  }

  private void updateControls() {
    boolean running = executionActive;
    runStop.setIcon(
        running
            ? SwingIcons.stop(new Color(178, 34, 34))
            : SwingIcons.play(new Color(0, 140, 0)));
    runStop.setToolTipText(running ? "Stop the script (Ctrl+R)" : "Run the script (Ctrl+R)");
    engine.setEnabled(!running);
    newItem.setEnabled(!running);
    newButton.setEnabled(!running);
    openItem.setEnabled(!running);
    openButton.setEnabled(!running);
    closeTabItem.setEnabled(!running);
    classpathItem.setEnabled(!running);
    tabs.forEach(tab -> tab.close.setEnabled(!running));
  }

  private void updateTab(EditorTab tab) {
    String suffix = tab.dirty ? " *" : "";
    String title = tabName(tab) + suffix;
    tab.label.setText(title);
    tab.close.getAccessibleContext().setAccessibleName("Close " + tabName(tab));
    int index = editorTabs.indexOfComponent(tab.scrollPane);
    if (index >= 0) {
      editorTabs.setTitleAt(index, title);
      editorTabs.setToolTipTextAt(index, tab.file == null ? null : tab.file.getAbsolutePath());
    }
    if (tab == selectedTab()) {
      setTitle((tab.file == null ? options.title() : options.title() + " - " + tab.file.getName()) + suffix);
    }
  }

  private void updateSelectedTab() {
    if (!tabs.isEmpty() && editorTabs.getSelectedIndex() >= 0) {
      updateTab(selectedTab());
    }
  }

  private void markDirty(EditorTab tab) {
    if (!tab.dirty) {
      tab.dirty = true;
      updateTab(tab);
    }
  }

  private EditorTab selectedTab() {
    int index = editorTabs.getSelectedIndex();
    if (index < 0 || index >= tabs.size()) {
      throw new IllegalStateException("ScriptDock must contain a selected editor tab");
    }
    return tabs.get(index);
  }

  private EditorTab reusableEmptyTab() {
    EditorTab tab = selectedTab();
    return tab.file == null && !tab.dirty && tab.editor.getDocument().getLength() == 0 ? tab : null;
  }

  private String tabName(EditorTab tab) {
    return tab.file == null ? "Untitled " + tab.untitledNumber : tab.file.getName();
  }

  private String selectedEngine() {
    Object selected = engine.getSelectedItem();
    return selected == null ? Jsr223ScriptRunner.DEFAULT_ENGINE : selected.toString();
  }

  private String defaultFileName() {
    return Jsr223ScriptRunner.DEFAULT_ENGINE.equalsIgnoreCase(selectedEngine())
        ? "script.groovy"
        : "script.txt";
  }

  private void rememberDirectory(File file) {
    File directory = file.getParentFile();
    if (directory != null) {
      currentDirectory = directory;
      if (preferences != null) {
        preferences.saveDirectory(directory);
      }
      options.directoryListener().accept(directory);
    }
  }

  private void setIdleState(String state) {
    setState(state, READY);
    durationLabel.setText("Time: -");
  }

  private void setState(String state, Color color) {
    stateLabel.setText(state);
    stateLabel.setForeground(color);
  }

  private void updateDuration() {
    long seconds = (System.nanoTime() - startedAt) / 1_000_000_000L;
    durationLabel.setText(String.format("Time: %d:%02d", seconds / 60, seconds % 60));
  }

  private JPanel statusBar() {
    JPanel bar = new JPanel();
    bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
    bar.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)));
    bar.add(stateLabel);
    addSeparator(bar);
    bar.add(durationLabel);
    bar.add(Box.createHorizontalGlue());
    return bar;
  }

  private static void addSeparator(JPanel bar) {
    bar.add(Box.createHorizontalStrut(12));
    JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
    separator.setMaximumSize(new Dimension(2, Short.MAX_VALUE));
    bar.add(separator);
    bar.add(Box.createHorizontalStrut(12));
  }

  private void sizeEngineSelector() {
    String longest = "";
    for (int index = 0; index < engine.getItemCount(); index++) {
      String item = engine.getItemAt(index);
      if (item != null && item.length() > longest.length()) {
        longest = item;
      }
    }
    engine.setPrototypeDisplayValue(longest + "WW");
    engine.setMaximumSize(engine.getPreferredSize());
  }

  private static void configureButton(JButton button, String tooltip, String accessibleName) {
    button.setToolTipText(tooltip);
    button.getAccessibleContext().setAccessibleName(accessibleName);
  }

  private void showError(String message) {
    JOptionPane.showMessageDialog(this, message, options.title(), JOptionPane.WARNING_MESSAGE);
  }
}
