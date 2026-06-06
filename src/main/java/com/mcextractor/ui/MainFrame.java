package com.mcextractor.ui;

import com.mcextractor.model.*;
import com.mcextractor.service.*;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;

/**
 * 主窗口 — 组合树形导航、资源表格和预览面板。
 */
public class MainFrame extends JFrame {

    private final AssetService assetService = new AssetService();
    private final ExportService exportService = new ExportService(assetService);

    private final AssetTreePanel treePanel = new AssetTreePanel();
    private final AssetTablePanel tablePanel = new AssetTablePanel();
    private final PreviewPanel previewPanel = new PreviewPanel();

    private final JTextField searchField = new JTextField(25);
    private final JLabel statusLabel = new JLabel("就绪");
    private final JComboBox<String> extFilterCombo = new JComboBox<>();
    private final JComboBox<String> versionCombo = new JComboBox<>();

    private File lastIndexDir;
    private File lastObjectsDir;

    public MainFrame() {
        super("McExtractor — Minecraft 资源浏览器");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(null);

        buildMenuBar();
        buildToolBar();
        buildContent();
        buildStatusBar();
        wireEvents();

        // 自动检测 .minecraft 路径
        detectMinecraftAssets();

        // 关闭时停止音频
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                previewPanel.clear();
            }
        });
    }

    // ==================== 自动检测 .minecraft ====================

    private void detectMinecraftAssets() {
        File mcDir = findMinecraftDir();
        if (mcDir == null) {
            status("未找到 .minecraft 目录，请手动打开索引文件");
            return;
        }

        File assetsDir = new File(mcDir, "assets");
        File indexesDir = new File(assetsDir, "indexes");
        File objectsDir = new File(assetsDir, "objects");

        if (objectsDir.isDirectory()) {
            lastObjectsDir = objectsDir;
            assetService.setObjectsDir(objectsDir);
            status("已检测到对象目录: " + objectsDir.getAbsolutePath());
        }

        lastIndexDir = indexesDir;
        if (indexesDir.isDirectory()) {
            // 自动加载第一个索引文件
            File[] jsonFiles = indexesDir.listFiles(
                    (dir, name) -> name.endsWith(".json"));
            if (jsonFiles != null && jsonFiles.length > 0) {
                // 优先选择最新的（按文件大小或修改时间）
                File newest = jsonFiles[0];
                for (File f : jsonFiles) {
                    if (f.lastModified() > newest.lastModified()) {
                        newest = f;
                    }
                }
                try {
                    AssetIndex index = assetService.loadIndex(newest);
                    treePanel.setIndex(index);
                    populateExtFilter(index);
                    refreshVersionList(indexesDir);
                    versionCombo.setSelectedItem(newest.getName());
                    status("已加载 " + newest.getName() + " — " + index.size() + " 个资源");
                } catch (IOException ex) {
                    status("加载索引失败: " + newest.getName());
                }
            }
        }
    }

    /**
     * 按标准路径查找 .minecraft 目录。
     */
    private File findMinecraftDir() {
        // 1) 当前工作目录下的 .minecraft
        File cwd = new File(".minecraft");
        if (cwd.isDirectory()) return cwd;

        // 2) Windows: %APPDATA%\.minecraft
        String appdata = System.getenv("APPDATA");
        if (appdata != null) {
            File win = new File(appdata, ".minecraft");
            if (win.isDirectory()) return win;
        }

        // 3) Linux / macOS: ~/.minecraft
        File home = new File(System.getProperty("user.home"), ".minecraft");
        if (home.isDirectory()) return home;

        // 4) macOS 备选路径
        File mac = new File(System.getProperty("user.home"),
                "Library/Application Support/minecraft");
        if (mac.isDirectory()) return mac;

        return null;
    }

    // ==================== 菜单栏 ====================

    private void buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        // ---- 文件 ----
        JMenu fileMenu = new JMenu("文件");
        JMenuItem openItem = new JMenuItem("打开索引...");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        openItem.addActionListener(e -> openIndex());

        JMenuItem setObjectsItem = new JMenuItem("设置对象目录...");
        setObjectsItem.addActionListener(e -> setObjectsDir());

        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        exitItem.addActionListener(e -> dispose());

        fileMenu.add(openItem);
        fileMenu.add(setObjectsItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        // ---- 导出 ----
        JMenu exportMenu = new JMenu("导出");

        JMenuItem expSel = new JMenuItem("导出选中...");
        expSel.addActionListener(e -> exportSelected());

        JMenuItem expAll = new JMenuItem("导出全部...");
        expAll.addActionListener(e -> exportAll());

        JMenuItem expVisible = new JMenuItem("导出可见...");
        expVisible.addActionListener(e -> exportVisible());

        exportMenu.add(expSel);
        exportMenu.add(expVisible);
        exportMenu.addSeparator();
        exportMenu.add(expAll);

        JMenu expByType = new JMenu("按类型导出");
        String[] types = {"png", "ogg", "json", "wav", "txt", "mcmeta", "vsh", "fsh", "lang"};
        for (String t : types) {
            JMenuItem item = new JMenuItem("." + t.toUpperCase());
            item.addActionListener(e -> exportByType(t));
            expByType.add(item);
        }
        exportMenu.add(expByType);

        // ---- 帮助 ----
        JMenu helpMenu = new JMenu("帮助");
        JMenuItem aboutItem = new JMenuItem("关于");
        aboutItem.addActionListener(e -> showAbout());
        helpMenu.add(aboutItem);

        bar.add(fileMenu);
        bar.add(exportMenu);
        bar.add(helpMenu);
        setJMenuBar(bar);
    }

    // ==================== 工具栏 ====================

    private void buildToolBar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);

        JButton openBtn = new JButton("打开索引");
        openBtn.addActionListener(e -> openIndex());

        JButton setObjBtn = new JButton("设置对象目录...");
        setObjBtn.addActionListener(e -> setObjectsDir());

        tb.add(openBtn);
        tb.add(setObjBtn);
        tb.addSeparator();

        tb.add(new JLabel(" 搜索: "));
        searchField.setMaximumSize(new Dimension(200, 28));
        tb.add(searchField);

        JButton clearSearch = new JButton("✕");
        clearSearch.setToolTipText("清除搜索");
        clearSearch.addActionListener(e -> {
            searchField.setText("");
            applyFilter();
        });
        tb.add(clearSearch);

        tb.addSeparator();
        tb.add(new JLabel(" 类型: "));
        extFilterCombo.setMaximumSize(new Dimension(100, 28));
        extFilterCombo.addItem("全部");
        tb.add(extFilterCombo);

        tb.addSeparator();
        tb.add(new JLabel(" 版本: "));
        versionCombo.setMaximumSize(new Dimension(130, 28));
        versionCombo.addItem("（自动检测）");
        versionCombo.setEnabled(false);
        versionCombo.addActionListener(e -> {
            if (versionCombo.getSelectedIndex() > 0) {
                String selected = (String) versionCombo.getSelectedItem();
                loadVersion(selected);
            }
        });
        tb.add(versionCombo);

        tb.addSeparator();
        JButton expBtn = new JButton("导出选中");
        expBtn.addActionListener(e -> exportSelected());
        tb.add(expBtn);

        add(tb, BorderLayout.NORTH);
    }

    // ==================== 内容区布局 ====================

    private void buildContent() {
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                tablePanel, previewPanel);
        rightSplit.setResizeWeight(0.55);
        rightSplit.setDividerLocation(350);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(280, 600));
        leftPanel.add(treePanel, BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                leftPanel, rightSplit);
        mainSplit.setResizeWeight(0.22);
        mainSplit.setDividerLocation(280);

        add(mainSplit, BorderLayout.CENTER);
    }

    // ==================== 状态栏 ====================

    private void buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createLoweredSoftBevelBorder());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        bar.add(statusLabel, BorderLayout.WEST);

        JLabel memLabel = new JLabel();
        memLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        bar.add(memLabel, BorderLayout.EAST);

        new javax.swing.Timer(3000, e -> {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            memLabel.setText("堆内存: " + used + " MB");
        }).start();

        add(bar, BorderLayout.SOUTH);
    }

    // ==================== 事件绑定 ====================

    private void wireEvents() {
        // 树节点选中 → 表格
        treePanel.setSelectionListener(entries -> tablePanel.setEntries(entries));

        // 表格行选中 → 预览
        tablePanel.getTable().getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = tablePanel.getTable().getSelectedRow();
            if (row < 0) { previewPanel.clear(); return; }
            AssetEntry entry = tablePanel.getEntryAtRow(row);
            if (assetService.getObjectsDir() == null) {
                previewPanel.showWarning("请先设置对象目录 (文件 → 设置对象目录)");
                return;
            }
            File file = assetService.resolveFile(entry);
            if (file == null || !file.isFile()) {
                previewPanel.showWarning("文件不存在: " + entry.getPath());
                return;
            }
            previewPanel.preview(file, entry);
        });

        // 表格双击 → 预览
        tablePanel.getTable().addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = tablePanel.getTable().rowAtPoint(e.getPoint());
                    AssetEntry entry = tablePanel.getEntryAtRow(row);
                    if (entry == null) return;
                    if (assetService.getObjectsDir() == null) {
                        previewPanel.showWarning("请先设置对象目录");
                        return;
                    }
                    File file = assetService.resolveFile(entry);
                    previewPanel.preview(file, entry);
                }
            }
        });

        // 搜索框
        searchField.addActionListener(e -> applyFilter());
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });

        // 类型筛选
        extFilterCombo.addActionListener(e -> applyFilter());

        // 右键菜单
        tablePanel.getTable().addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e)  { maybeShowContext(e); }
            public void mouseReleased(MouseEvent e) { maybeShowContext(e); }

            private void maybeShowContext(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                JTable tbl = tablePanel.getTable();
                int row = tbl.rowAtPoint(e.getPoint());
                if (row >= 0 && !tbl.isRowSelected(row)) {
                    tbl.setRowSelectionInterval(row, row);
                }
                showContextMenu(e.getComponent(), e.getX(), e.getY());
            }
        });

        // 键盘快捷键
        tablePanel.getTable().addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    AssetEntry entry = tablePanel.getEntryAtRow(
                            tablePanel.getTable().getSelectedRow());
                    if (entry != null) {
                        if (assetService.getObjectsDir() == null) {
                            previewPanel.showWarning("请先设置对象目录");
                            return;
                        }
                        File file = assetService.resolveFile(entry);
                        previewPanel.preview(file, entry);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_C
                        && (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                    copySelectedPath();
                }
            }
        });
    }

    // ==================== 右键菜单 ====================

    private void showContextMenu(Component invoker, int x, int y) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem preview = new JMenuItem("预览");
        preview.addActionListener(e -> {
            AssetEntry entry = tablePanel.getEntryAtRow(tablePanel.getTable().getSelectedRow());
            if (entry != null) {
                if (assetService.getObjectsDir() == null) {
                    previewPanel.showWarning("请先设置对象目录");
                    return;
                }
                previewPanel.preview(assetService.resolveFile(entry), entry);
            }
        });

        JMenuItem export = new JMenuItem("导出选中");
        export.addActionListener(e -> exportSelected());

        JMenuItem exportAll = new JMenuItem("导出全部可见");
        exportAll.addActionListener(e -> exportVisible());

        JMenuItem copyPath = new JMenuItem("复制资源路径");
        copyPath.addActionListener(e -> copySelectedPath());

        JMenuItem copyHash = new JMenuItem("复制 Hash");
        copyHash.addActionListener(e -> copySelectedHash());

        JMenuItem selAll = new JMenuItem("全选");
        selAll.addActionListener(e -> tablePanel.getTable().selectAll());

        menu.add(preview);
        menu.addSeparator();
        menu.add(export);
        menu.add(exportAll);
        menu.addSeparator();
        menu.add(copyPath);
        menu.add(copyHash);
        menu.addSeparator();
        menu.add(selAll);

        menu.show(invoker, x, y);
    }

    // ==================== 操作 ====================

    private void openIndex() {
        JFileChooser chooser = new JFileChooser(lastIndexDir);
        chooser.setFileFilter(new FileNameExtensionFilter("JSON 文件 (*.json)", "json"));
        chooser.setDialogTitle("打开资源索引");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        lastIndexDir = file.getParentFile();
        try {
            AssetIndex index = assetService.loadIndex(file);
            treePanel.setIndex(index);
            populateExtFilter(index);
            refreshVersionList(file.getParentFile());
            versionCombo.setSelectedItem(file.getName());
            status("已加载 " + file.getName() + " — " + index.size() + " 个资源");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "加载索引失败:\n" + ex.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void populateExtFilter(AssetIndex index) {
        extFilterCombo.removeAllItems();
        extFilterCombo.addItem("全部");
        for (String ext : index.getExtensions()) {
            if (!ext.isEmpty()) extFilterCombo.addItem(ext);
        }
    }

    private void setObjectsDir() {
        JFileChooser chooser = new JFileChooser(lastObjectsDir);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择 Minecraft objects 目录");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        lastObjectsDir = chooser.getSelectedFile();
        assetService.setObjectsDir(lastObjectsDir);
        status("对象目录: " + lastObjectsDir.getAbsolutePath());
    }

    private void applyFilter() {
        AssetIndex index = assetService.getCurrentIndex();
        if (index == null) return;

        String search = searchField.getText().trim().toLowerCase();
        String ext = (String) extFilterCombo.getSelectedItem();

        List<AssetEntry> filtered = index.getAllEntries().stream()
                .filter(e -> {
                    if (ext != null && !"全部".equals(ext) && !ext.equals(e.getExtension()))
                        return false;
                    if (!search.isEmpty() && !e.getPath().toLowerCase().contains(search))
                        return false;
                    return true;
                })
                .toList();

        tablePanel.setEntries(filtered);
        status("筛选结果: " + filtered.size() + " / " + index.size() + " 个资源");
    }

    private void exportSelected() {
        List<AssetEntry> sel = tablePanel.getSelectedEntries();
        if (sel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "未选中任何资源。",
                    "导出", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        File dir = chooseExportDir();
        if (dir == null) return;
        int ok = exportService.exportBatch(sel, dir, true, this);
        status("已导出 " + ok + " / " + sel.size() + " 个文件到 " + dir.getName());
    }

    private void exportAll() {
        File dir = chooseExportDir();
        if (dir == null) return;
        int total = assetService.getCurrentIndex() != null
                ? assetService.getCurrentIndex().size() : 0;
        int ok = exportService.exportAll(dir, true, this);
        status("已导出 " + ok + " / " + total + " 个文件到 " + dir.getName());
    }

    private void exportVisible() {
        List<AssetEntry> visible = tablePanel.getAllEntries();
        if (visible.isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有可见的资源。",
                    "导出", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        File dir = chooseExportDir();
        if (dir == null) return;
        int ok = exportService.exportBatch(visible, dir, true, this);
        status("已导出 " + ok + " / " + visible.size() + " 个文件到 " + dir.getName());
    }

    private void exportByType(String ext) {
        File dir = chooseExportDir();
        if (dir == null) return;
        List<AssetEntry> filtered = assetService.getCurrentIndex() != null
                ? assetService.getCurrentIndex().getByExtension(ext) : List.of();
        int ok = exportService.exportBatch(filtered, dir, true, this);
        status("已导出 " + ok + " 个 ." + ext + " 文件到 " + dir.getName());
    }

    private File chooseExportDir() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择导出目标目录");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        return chooser.getSelectedFile();
    }

    private void copySelectedPath() {
        AssetEntry e = tablePanel.getEntryAtRow(tablePanel.getTable().getSelectedRow());
        if (e == null) return;
        StringSelection sel = new StringSelection(e.getPath());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
        status("已复制路径: " + e.getPath());
    }

    private void copySelectedHash() {
        AssetEntry e = tablePanel.getEntryAtRow(tablePanel.getTable().getSelectedRow());
        if (e == null) return;
        StringSelection sel = new StringSelection(e.getHash());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
        status("已复制 Hash: " + e.getHash());
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                """
                McExtractor v1.0
                Minecraft 资源浏览器
                                
                浏览、预览和导出 Minecraft 游戏资源。
                支持 OGG 音频、PNG/JPEG 图片和文本文件预览。
                                
                索引文件:  .minecraft/assets/indexes/*.json
                对象文件:  .minecraft/assets/objects/
                                
                快捷键:
                  Ctrl+O  打开索引
                  Ctrl+Q  退出
                  Enter   预览选中资源
                  Ctrl+C  复制路径
                """,
                "关于 McExtractor", JOptionPane.INFORMATION_MESSAGE);
    }

    // ==================== 版本选择 ====================

    /**
     * 扫描 indexes 目录下的所有 JSON 文件并填充版本下拉框。
     */
    private void refreshVersionList(File indexesDir) {
        versionCombo.removeAllItems();
        versionCombo.addItem("（自动检测）");
        if (indexesDir == null || !indexesDir.isDirectory()) {
            versionCombo.setEnabled(false);
            return;
        }

        File[] jsonFiles = indexesDir.listFiles(
                (dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) {
            versionCombo.setEnabled(false);
            return;
        }

        // Sort by modification time (newest first)
        java.util.Arrays.sort(jsonFiles,
                (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        for (File f : jsonFiles) {
            versionCombo.addItem(f.getName());
        }
        versionCombo.setEnabled(true);
    }

    /**
     * 从 indexes 目录加载指定版本。
     */
    private void loadVersion(String fileName) {
        if (fileName == null || lastIndexDir == null) return;
        File indexFile = new File(lastIndexDir, fileName);
        if (!indexFile.isFile()) return;

        try {
            AssetIndex index = assetService.loadIndex(indexFile);
            treePanel.setIndex(index);
            populateExtFilter(index);
            applyFilter();
            status("已切换至 " + fileName + " — " + index.size() + " 个资源");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "加载版本失败:\n" + ex.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void status(String msg) {
        statusLabel.setText(msg);
    }
}
