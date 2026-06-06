package com.mcextractor.ui;

import com.mcextractor.model.AssetEntry;
import com.mcextractor.model.AssetIndex;
import com.mcextractor.model.AssetIndex.TreeNode;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Left-side tree panel showing the asset path hierarchy.
 */
public class AssetTreePanel extends JPanel {

    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private Consumer<List<AssetEntry>> selectionListener;

    public AssetTreePanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("导航"));

        rootNode = new DefaultMutableTreeNode("未加载索引");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new AssetTreeRenderer());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        tree.addTreeSelectionListener(e -> {
            if (selectionListener == null) return;
            DefaultMutableTreeNode sel = (DefaultMutableTreeNode)
                    tree.getLastSelectedPathComponent();
            if (sel == null) return;
            Object userObj = sel.getUserObject();
            if (userObj instanceof TreeNode tn) {
                List<AssetEntry> entries = collectEntries(tn);
                selectionListener.accept(entries);
            }
        });

        JScrollPane scrollPane = new JScrollPane(tree);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setSelectionListener(Consumer<List<AssetEntry>> listener) {
        this.selectionListener = listener;
    }

    public void setIndex(AssetIndex index) {
        if (index == null) {
            rootNode = new DefaultMutableTreeNode("未加载索引");
            treeModel.setRoot(rootNode);
            return;
        }
        TreeNode idxRoot = index.getRootNode();
        DefaultMutableTreeNode newRoot = buildSwingTree(idxRoot);
        rootNode = newRoot;
        treeModel.setRoot(rootNode);
        // expand first two levels and select the root
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < Math.min(rootNode.getChildCount(), 2); i++) {
                tree.expandRow(i);
            }
            tree.setSelectionRow(0);
        });
    }

    private DefaultMutableTreeNode buildSwingTree(TreeNode tn) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(tn);
        for (TreeNode child : tn.children.values()) {
            node.add(buildSwingTree(child));
        }
        return node;
    }

    /** Collect AssetEntry objects from a TreeNode and its subtree */
    private List<AssetEntry> collectEntries(TreeNode tn) {
        List<AssetEntry> result = new ArrayList<>();
        collectRecursive(tn, result);
        return result;
    }

    private void collectRecursive(TreeNode tn, List<AssetEntry> out) {
        out.addAll(tn.entries);
        for (TreeNode child : tn.children.values()) {
            collectRecursive(child, out);
        }
    }

    // ---- custom renderer ----

    private static class AssetTreeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean sel, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object obj = node.getUserObject();
            if (obj instanceof TreeNode tn) {
                if (tn.isLeaf) {
                    setIcon(UIManager.getIcon("FileView.fileIcon"));
                } else {
                    setIcon(expanded
                            ? UIManager.getIcon("FileView.directoryIcon")
                            : UIManager.getIcon("Tree.closedIcon"));
                }
                int cnt = tn.totalEntryCount();
                setText(cnt > 0 ? tn.name + " (" + cnt + ")" : tn.name);
            }
            return this;
        }
    }
}
