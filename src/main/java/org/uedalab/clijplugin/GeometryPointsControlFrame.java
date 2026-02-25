package org.uedalab.clijplugin;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GeometryPointsControlFrame extends JFrame implements GeometryPointsCanvas.StateProvider {

    private final JLabel boundImageLabel = new JLabel("bound image: (none)");
    private final JLabel zInfoLabel = new JLabel("Z: - / -");
    private final JPanel canvasContainer = new JPanel(new BorderLayout());
    private final JSlider channelSlider = new JSlider(1, 1, 1);
    private final JSlider zSlider = new JSlider(1, 1, 1);

    private final PointsTableModel pointsModel = new PointsTableModel();
    private final ModelsTableModel modelsModel = new ModelsTableModel();
    private final JTable pointsTable = new JTable(pointsModel);
    private final JTable modelsTable = new JTable(modelsModel);

    private final JComboBox<String> lineSelector = new JComboBox<>();
    private final JComboBox<String> planeSelector = new JComboBox<>();
    private final JLabel angleLabel = new JLabel("--");

    private JRadioButton lineRadio;
    private JRadioButton planeRadio;

    private final Map<Long, PointRecord> allPoints = new LinkedHashMap<>();
    private final List<Long> unassignedPointKeys = new ArrayList<>();
    private final List<ModelRecord> models = new ArrayList<>();

    private ImagePlus boundImage;
    private GeometryPointsCanvas imageCanvas;
    private long nextPointKey = 1;
    private int nextPointId = 1;
    private int nextLineId = 1;
    private int nextPlaneId = 1;

    public GeometryPointsControlFrame() {
        super("Geometry Points Control");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1220, 760);
        setLocationByPlatform(true);
        buildUi();
        refreshBoundImageLabel();
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));

        final JPanel leftPane = new JPanel(new BorderLayout(4, 4));
        leftPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));
        leftPane.add(buildTopBar(), BorderLayout.NORTH);
        canvasContainer.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        canvasContainer.setPreferredSize(new Dimension(720, 620));
        leftPane.add(canvasContainer, BorderLayout.CENTER);
        leftPane.add(buildSliders(), BorderLayout.SOUTH);

        final JPanel rightPane = buildRightPane();

        final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPane, rightPane);
        splitPane.setResizeWeight(0.58);
        splitPane.setContinuousLayout(true);
        splitPane.setOneTouchExpandable(true);
        SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(0.58));
        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel buildRightPane() {
        final JPanel rightPane = new JPanel(new GridBagLayout());
        rightPane.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));
        rightPane.setPreferredSize(new Dimension(500, 640));

        final GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1.0;

        gc.gridy = 0;
        gc.weighty = 0.30;
        rightPane.add(buildPointsPanel(), gc);

        gc.gridy = 1;
        gc.weighty = 0.0;
        rightPane.add(buildFitPanel(), gc);

        gc.gridy = 2;
        gc.weighty = 0.70;
        rightPane.add(buildModelsPanel(), gc);

        gc.gridy = 3;
        gc.weighty = 0.0;
        rightPane.add(buildAnglePanel(), gc);

        return rightPane;
    }

    private JPanel buildTopBar() {
        final JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(boundImageLabel, BorderLayout.CENTER);
        final JButton bindButton = new JButton("bind active image");
        bindButton.addActionListener(e -> bindActiveImage());
        top.add(bindButton, BorderLayout.EAST);
        return top;
    }

    private JPanel buildSliders() {
        final JPanel sliderPanel = new JPanel(new GridLayout(3, 2, 6, 4));
        sliderPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        sliderPanel.add(new JLabel("Channel"));
        sliderPanel.add(channelSlider);
        sliderPanel.add(new JLabel("Z"));
        sliderPanel.add(zSlider);
        sliderPanel.add(new JLabel("Slice"));
        sliderPanel.add(zInfoLabel);

        channelSlider.addChangeListener(e -> applyChannelFromSlider());
        zSlider.addChangeListener(e -> applyZFromSlider());
        channelSlider.setEnabled(false);
        zSlider.setEnabled(false);
        return sliderPanel;
    }

    private JPanel buildPointsPanel() {
        final JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Points"));

        pointsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pointsTable.setRowSelectionAllowed(true);
        pointsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                modelsTable.clearSelection();
                rebuildOverlay();
            }
        });
        pointsTable.getColumnModel().getColumn(4).setCellRenderer(new DeleteButtonRenderer());
        pointsTable.getColumnModel().getColumn(4).setCellEditor(new DeleteButtonEditor(pointsTable, this::deletePointRow));
        pointsTable.getColumnModel().getColumn(4).setMaxWidth(52);
        pointsTable.getColumnModel().getColumn(4).setMinWidth(42);

        final JScrollPane scrollPane = new JScrollPane(pointsTable);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.setMinimumSize(new Dimension(380, 160));

        pointsTable.getInputMap().put(KeyStroke.getKeyStroke("DELETE"), "deletePointRow");
        pointsTable.getActionMap().put("deletePointRow", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                final int row = pointsTable.getSelectedRow();
                if (row >= 0) {
                    deletePointRow(row);
                }
            }
        });
        return panel;
    }

    private JPanel buildFitPanel() {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Fitting"));
        panel.setPreferredSize(new Dimension(460, 66));

        final JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        final JButton fitButton = new JButton("fitting");
        lineRadio = new JRadioButton("line", true);
        planeRadio = new JRadioButton("plane", false);
        final ButtonGroup group = new ButtonGroup();
        group.add(lineRadio);
        group.add(planeRadio);

        fitButton.addActionListener(e -> fitCurrentUnassignedPoints(lineRadio.isSelected() ? ModelType.LINE : ModelType.PLANE));

        controls.add(fitButton);
        controls.add(lineRadio);
        controls.add(planeRadio);
        panel.add(controls, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildModelsPanel() {
        final JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Fit results"));

        modelsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        modelsTable.setRowSelectionAllowed(true);
        modelsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                pointsTable.clearSelection();
                rebuildOverlay();
            }
        });
        modelsTable.getColumnModel().getColumn(3).setCellRenderer(new DeleteButtonRenderer());
        modelsTable.getColumnModel().getColumn(3).setCellEditor(new DeleteButtonEditor(modelsTable, this::deleteModelRow));
        modelsTable.getColumnModel().getColumn(3).setMaxWidth(52);
        modelsTable.getColumnModel().getColumn(3).setMinWidth(42);

        final JScrollPane scrollPane = new JScrollPane(modelsTable);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.setMinimumSize(new Dimension(380, 200));

        modelsTable.getInputMap().put(KeyStroke.getKeyStroke("DELETE"), "deleteModelRow");
        modelsTable.getActionMap().put("deleteModelRow", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                final int row = modelsTable.getSelectedRow();
                if (row >= 0) {
                    deleteModelRow(row);
                }
            }
        });
        return panel;
    }

    private JPanel buildAnglePanel() {
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Angle"));
        panel.setPreferredSize(new Dimension(460, 72));

        final JButton compute = new JButton("Compute angle");
        compute.addActionListener(e -> computeAngle());

        panel.add(compute);
        panel.add(new JLabel("Line"));
        panel.add(lineSelector);
        panel.add(new JLabel("Plane"));
        panel.add(planeSelector);
        panel.add(new JLabel("Angle"));
        panel.add(angleLabel);
        return panel;
    }

    private void bindActiveImage() {
        final ImagePlus active = WindowManager.getCurrentImage();
        if (active == null) {
            IJ.error("No active image found.");
            return;
        }
        boundImage = active;
        installEmbeddedCanvas();
        configureSlidersFromImage();
        refreshBoundImageLabel();
        rebuildOverlay();
    }

    private void installEmbeddedCanvas() {
        canvasContainer.removeAll();
        imageCanvas = new GeometryPointsCanvas(boundImage, this);
        canvasContainer.add(imageCanvas, BorderLayout.CENTER);
        canvasContainer.revalidate();
        canvasContainer.repaint();
    }

    private void configureSlidersFromImage() {
        if (boundImage == null) {
            channelSlider.setEnabled(false);
            zSlider.setEnabled(false);
            return;
        }
        final int channels = Math.max(1, boundImage.getNChannels());
        final int slices = Math.max(1, boundImage.getNSlices());

        channelSlider.setMinimum(1);
        channelSlider.setMaximum(channels);
        channelSlider.setValue(Math.max(1, boundImage.getC()));
        channelSlider.setEnabled(channels > 1);

        zSlider.setMinimum(1);
        zSlider.setMaximum(slices);
        zSlider.setValue(Math.max(1, boundImage.getZ()));
        zSlider.setEnabled(slices > 1);
        updateZInfoLabel();
    }

    private void applyChannelFromSlider() {
        if (boundImage == null) {
            return;
        }
        final int c = Math.max(1, channelSlider.getValue());
        if (boundImage.isHyperStack()) {
            final int z = Math.max(1, boundImage.getZ());
            final int t = Math.max(1, boundImage.getT());
            boundImage.setPosition(c, z, t);
        } else if (boundImage.getNChannels() > 1) {
            boundImage.setC(c);
        }
        boundImage.updateAndDraw();
        rebuildOverlay();
        if (imageCanvas != null) imageCanvas.repaint();
    }

    private void applyZFromSlider() {
        if (boundImage == null) {
            return;
        }
        final int z = Math.max(1, zSlider.getValue());
        if (boundImage.isHyperStack()) {
            boundImage.setPosition(Math.max(1, boundImage.getC()), z, Math.max(1, boundImage.getT()));
        } else {
            boundImage.setSlice(z);
        }
        boundImage.updateAndDraw();
        updateZInfoLabel();
        rebuildOverlay();
        if (imageCanvas != null) imageCanvas.repaint();
    }

    @Override
    public GeometryPointsCanvas.RenderState getRenderState() {
        final int selectedPointRow = pointsTable.getSelectedRow();
        final int selectedModelRow = modelsTable.getSelectedRow();

        Long selectedPointKey = null;
        if (selectedPointRow >= 0 && selectedPointRow < unassignedPointKeys.size()) {
            selectedPointKey = unassignedPointKeys.get(selectedPointRow);
        }

        ModelRecord selectedModel = null;
        if (selectedModelRow >= 0 && selectedModelRow < models.size()) {
            selectedModel = models.get(selectedModelRow);
        }

        final List<GeometryPointsCanvas.RenderPoint> points = new ArrayList<>();
        for (PointRecord p : allPoints.values()) {
            boolean highlighted = selectedPointKey != null && selectedPointKey == p.key;
            if (!highlighted && selectedModel != null) {
                highlighted = selectedModel.pointKeys.contains(p.key);
            }
            points.add(new GeometryPointsCanvas.RenderPoint(p.x, p.y, p.z, highlighted));
        }

        GeometryPointsCanvas.RenderModel renderModel = null;
        if (selectedModel != null) {
            renderModel = new GeometryPointsCanvas.RenderModel(
                    selectedModel.type,
                    selectedModel.cx, selectedModel.cy, selectedModel.cz,
                    selectedModel.vx, selectedModel.vy, selectedModel.vz
            );
        }
        return new GeometryPointsCanvas.RenderState(points, renderModel);
    }

    @Override
    public void handleCanvasClick(final double x, final double y, final int z) {
        if (!ensureBoundImage()) {
            return;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return;
        }
        final int zSlice = clampSlice(z > 0 ? z : boundImage.getZ());
        final long key = nextPointKey++;
        final PointRecord p = new PointRecord(key, nextPointId++, x, y, zSlice);
        allPoints.put(key, p);
        unassignedPointKeys.add(key);
        pointsModel.fireTableDataChanged();
        rebuildOverlay();
    }

    private void fitCurrentUnassignedPoints(final ModelType type) {
        if (unassignedPointKeys.isEmpty()) {
            IJ.error("No points to fit.");
            return;
        }

        final List<double[]> fitPoints = new ArrayList<>();
        for (Long key : unassignedPointKeys) {
            final PointRecord p = allPoints.get(key);
            if (p != null) {
                fitPoints.add(new double[]{p.x, p.y, p.z - 1.0});
            }
        }

        if (type == ModelType.LINE && fitPoints.size() < 2) {
            IJ.error("Need at least 2 points for line fitting.");
            return;
        }
        if (type == ModelType.PLANE && fitPoints.size() < 3) {
            IJ.error("Need at least 3 points for plane fitting.");
            return;
        }

        final List<Long> assignedKeys = new ArrayList<>(unassignedPointKeys);
        final ModelRecord model;
        if (type == ModelType.LINE) {
            final Pca3DUtils.LineFitResult fit = Pca3DUtils.fitLine(fitPoints);
            final String id = "L" + nextLineId++;
            final String eq = String.format(Locale.ROOT,
                    "c=(%.4f,%.4f,%.4f), d=(%.4f,%.4f,%.4f), rms=%.4f",
                    fit.centroid[0], fit.centroid[1], fit.centroid[2],
                    fit.direction[0], fit.direction[1], fit.direction[2], fit.rmsDist);
            model = new ModelRecord(id, ModelType.LINE, fit.centroid[0], fit.centroid[1], fit.centroid[2],
                    fit.direction[0], fit.direction[1], fit.direction[2], fit.rmsDist, 0.0,
                    clampSlice((int) Math.round(fit.centroid[2] + 1.0)), assignedKeys, eq);
        } else {
            final Pca3DUtils.PlaneFitResult fit = Pca3DUtils.fitPlane(fitPoints);
            final String id = "P" + nextPlaneId++;
            final String eq = String.format(Locale.ROOT,
                    "c=(%.4f,%.4f,%.4f), n=(%.4f,%.4f,%.4f), rms=%.4f, max=%.4f",
                    fit.centroid[0], fit.centroid[1], fit.centroid[2],
                    fit.normal[0], fit.normal[1], fit.normal[2], fit.rmsDist, fit.maxDist);
            model = new ModelRecord(id, ModelType.PLANE, fit.centroid[0], fit.centroid[1], fit.centroid[2],
                    fit.normal[0], fit.normal[1], fit.normal[2], fit.rmsDist, fit.maxDist,
                    clampSlice((int) Math.round(fit.centroid[2] + 1.0)), assignedKeys, eq);
        }

        models.add(model);
        unassignedPointKeys.clear();
        pointsModel.fireTableDataChanged();
        modelsModel.fireTableDataChanged();
        refreshAngleSelectors();
        modelsTable.getSelectionModel().setSelectionInterval(models.size() - 1, models.size() - 1);
        rebuildOverlay();
    }

    private void deletePointRow(final int row) {
        if (row < 0 || row >= unassignedPointKeys.size()) {
            return;
        }
        final Long key = unassignedPointKeys.remove(row);
        allPoints.remove(key);
        pointsTable.clearSelection();
        pointsModel.fireTableDataChanged();
        rebuildOverlay();
    }

    private void deleteModelRow(final int row) {
        if (row < 0 || row >= models.size()) {
            return;
        }
        final ModelRecord removed = models.remove(row);
        for (Long key : removed.pointKeys) {
            if (allPoints.containsKey(key)) {
                unassignedPointKeys.add(key);
            }
        }
        Collections.sort(unassignedPointKeys, (a, b) -> Integer.compare(allPoints.get(a).id, allPoints.get(b).id));
        modelsTable.clearSelection();
        pointsModel.fireTableDataChanged();
        modelsModel.fireTableDataChanged();
        refreshAngleSelectors();
        rebuildOverlay();
    }

    private void rebuildOverlay() {
        if (boundImage == null) {
            return;
        }
        boundImage.setOverlay(null);
        boundImage.updateAndDraw();
        if (imageCanvas != null) imageCanvas.repaint();
    }

    private void computeAngle() {
        final String lineId = (String) lineSelector.getSelectedItem();
        final String planeId = (String) planeSelector.getSelectedItem();
        if (lineId == null || planeId == null) {
            angleLabel.setText("--");
            IJ.error("Select one line model and one plane model.");
            return;
        }
        final ModelRecord line = findModelById(lineId);
        final ModelRecord plane = findModelById(planeId);
        if (line == null || plane == null) {
            angleLabel.setText("--");
            return;
        }

        final double[] d = Pca3DUtils.normalize(new double[]{line.vx, line.vy, line.vz}, new double[]{Double.NaN, Double.NaN, Double.NaN});
        final double[] n = Pca3DUtils.normalize(new double[]{plane.vx, plane.vy, plane.vz}, new double[]{Double.NaN, Double.NaN, Double.NaN});
        if (!Double.isFinite(d[0]) || !Double.isFinite(n[0])) {
            angleLabel.setText("--");
            IJ.error("Model vectors are invalid.");
            return;
        }

        double dot = d[0] * n[0] + d[1] * n[1] + d[2] * n[2];
        dot = Math.abs(dot);
        dot = Math.max(-1.0, Math.min(1.0, dot));
        final double thetaDeg = Math.toDegrees(Math.acos(dot));
        final double angleDeg = 90.0 - thetaDeg;
        angleLabel.setText(String.format(Locale.ROOT, "%.2f\u00B0", angleDeg));
    }

    private void refreshAngleSelectors() {
        final String selectedLine = (String) lineSelector.getSelectedItem();
        final String selectedPlane = (String) planeSelector.getSelectedItem();

        lineSelector.removeAllItems();
        planeSelector.removeAllItems();
        for (ModelRecord model : models) {
            if (model.type == ModelType.LINE) {
                lineSelector.addItem(model.id);
            } else {
                planeSelector.addItem(model.id);
            }
        }

        restoreSelection(lineSelector, selectedLine);
        restoreSelection(planeSelector, selectedPlane);
        if (lineSelector.getItemCount() == 0 || planeSelector.getItemCount() == 0) {
            angleLabel.setText("--");
        }
    }

    private static void restoreSelection(final JComboBox<String> combo, final String preferred) {
        if (preferred == null) {
            return;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (preferred.equals(combo.getItemAt(i))) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private ModelRecord findModelById(final String id) {
        for (ModelRecord model : models) {
            if (model.id.equals(id)) {
                return model;
            }
        }
        return null;
    }

    private boolean ensureBoundImage() {
        if (boundImage == null) {
            IJ.error("No image bound. Click 'bind active image' first.");
            return false;
        }
        return true;
    }

    private int clampSlice(final int z) {
        if (boundImage == null) {
            return Math.max(1, z);
        }
        final int max = Math.max(1, boundImage.getNSlices());
        if (z < 1) {
            return 1;
        }
        return Math.min(z, max);
    }

    private void refreshBoundImageLabel() {
        if (boundImage == null) {
            boundImageLabel.setText("bound image: (none)");
            zInfoLabel.setText("Z: - / -");
            return;
        }
        boundImageLabel.setText(String.format(Locale.ROOT, "bound image: %s (%dx%dx%d)",
                boundImage.getTitle(), boundImage.getWidth(), boundImage.getHeight(), Math.max(1, boundImage.getNSlices())));
        updateZInfoLabel();
    }

    private void updateZInfoLabel() {
        if (boundImage == null) {
            zInfoLabel.setText("Z: - / -");
            return;
        }
        zInfoLabel.setText(String.format(Locale.ROOT, "Z: %d / %d", Math.max(1, boundImage.getZ()), Math.max(1, boundImage.getNSlices())));
    }

    private final class PointsTableModel extends AbstractTableModel {
        private final String[] columns = {"id", "x", "y", "z", "delete"};

        @Override
        public int getRowCount() {
            return unassignedPointKeys.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(final int column) {
            return columns[column];
        }

        @Override
        public boolean isCellEditable(final int rowIndex, final int columnIndex) {
            return columnIndex == 4;
        }

        @Override
        public Object getValueAt(final int rowIndex, final int columnIndex) {
            final PointRecord p = allPoints.get(unassignedPointKeys.get(rowIndex));
            if (p == null) {
                return "";
            }
            switch (columnIndex) {
                case 0:
                    return p.id;
                case 1:
                    return String.format(Locale.ROOT, "%.2f", p.x);
                case 2:
                    return String.format(Locale.ROOT, "%.2f", p.y);
                case 3:
                    return p.z;
                case 4:
                    return "x";
                default:
                    return "";
            }
        }
    }

    private final class ModelsTableModel extends AbstractTableModel {
        private final String[] columns = {"id", "n", "equation", "delete"};

        @Override
        public int getRowCount() {
            return models.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(final int column) {
            return columns[column];
        }

        @Override
        public boolean isCellEditable(final int rowIndex, final int columnIndex) {
            return columnIndex == 3;
        }

        @Override
        public Object getValueAt(final int rowIndex, final int columnIndex) {
            final ModelRecord model = models.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return model.id;
                case 1:
                    return model.n;
                case 2:
                    return model.equation;
                case 3:
                    return "x";
                default:
                    return "";
            }
        }
    }

    private static final class DeleteButtonRenderer extends JButton implements TableCellRenderer {
        private DeleteButtonRenderer() {
            super("x");
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(final JTable table, final Object value,
                                                       final boolean isSelected, final boolean hasFocus,
                                                       final int row, final int column) {
            return this;
        }
    }

    private final class DeleteButtonEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {
        private final JButton button = new JButton("x");
        private final JTable table;
        private final RowDeleteAction action;
        private int row = -1;

        private DeleteButtonEditor(final JTable table, final RowDeleteAction action) {
            this.table = table;
            this.action = action;
            button.addActionListener(this);
        }

        @Override
        public Object getCellEditorValue() {
            return "x";
        }

        @Override
        public Component getTableCellEditorComponent(final JTable table, final Object value,
                                                     final boolean isSelected, final int row, final int column) {
            this.row = row;
            return button;
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
            fireEditingStopped();
            if (row >= 0 && row < table.getRowCount()) {
                action.deleteRow(row);
            }
        }
    }

    @FunctionalInterface
    private interface RowDeleteAction {
        void deleteRow(int row);
    }

    private static final class PointRecord {
        private final long key;
        private final int id;
        private final double x;
        private final double y;
        private final int z;

        private PointRecord(final long key, final int id, final double x, final double y, final int z) {
            this.key = key;
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    enum ModelType {
        LINE,
        PLANE
    }

    private static final class ModelRecord {
        private final String id;
        private final ModelType type;
        private final int n;
        private final String equation;
        private final double cx;
        private final double cy;
        private final double cz;
        private final double vx;
        private final double vy;
        private final double vz;
        private final double rmsDist;
        private final double maxDist;
        private final int modelZ;
        private final List<Long> pointKeys;

        private ModelRecord(final String id, final ModelType type, final double cx, final double cy, final double cz,
                            final double vx, final double vy, final double vz, final double rmsDist, final double maxDist,
                            final int modelZ, final List<Long> pointKeys, final String equation) {
            this.id = id;
            this.type = type;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.rmsDist = rmsDist;
            this.maxDist = maxDist;
            this.modelZ = modelZ;
            this.pointKeys = new ArrayList<>(pointKeys);
            this.n = this.pointKeys.size();
            this.equation = equation;
        }
    }
}
