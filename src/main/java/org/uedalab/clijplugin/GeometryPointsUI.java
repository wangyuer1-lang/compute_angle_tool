package org.uedalab.clijplugin;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Overlay;
import ij.gui.OvalRoi;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.process.FloatPolygon;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GeometryPointsUI extends JFrame {

    private static final double OVERLAY_RADIUS_PX = 3.0;

    private final JLabel boundImageLabel = new JLabel("bound image: (none)");
    private final PointTableModel model = new PointTableModel();
    private final JTable pointsTable = new JTable(model);
    private final JComboBox<String> roleCombo = new JComboBox<>(new String[]{
            PointTableSchema.ROLE_AXIS_START,
            PointTableSchema.ROLE_AXIS_END,
            PointTableSchema.ROLE_PLANE_FIT,
            PointTableSchema.ROLE_IGNORE
    });

    private ImagePlus boundImage;
    private int nextId = 1;

    public GeometryPointsUI() {
        super("Geometry Points UI");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 520);
        setLocationByPlatform(true);
        buildUi();
        refreshBoundImageLabel();
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));

        final JPanel top = new JPanel(new BorderLayout(6, 6));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        top.add(boundImageLabel, BorderLayout.CENTER);
        final JButton bindActiveImageButton = new JButton("bind active image");
        bindActiveImageButton.addActionListener(e -> bindActiveImage());
        top.add(bindActiveImageButton, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        pointsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        add(new JScrollPane(pointsTable), BorderLayout.CENTER);

        final JPanel controls = new JPanel(new GridLayout(0, 1, 6, 6));
        controls.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

        final JButton addFromSelectionButton = new JButton("add from selection");
        addFromSelectionButton.addActionListener(e -> addFromSelection());
        controls.add(addFromSelectionButton);

        final JButton deleteSelectedButton = new JButton("delete selected");
        deleteSelectedButton.addActionListener(e -> deleteSelected());
        controls.add(deleteSelectedButton);

        final JPanel rolePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rolePanel.add(roleCombo);
        final JButton setRoleForSelectedButton = new JButton("set role for selected");
        setRoleForSelectedButton.addActionListener(e -> setRoleForSelected());
        rolePanel.add(setRoleForSelectedButton);
        controls.add(rolePanel);

        final JButton showOverlayButton = new JButton("show overlay");
        showOverlayButton.addActionListener(e -> showOverlay());
        controls.add(showOverlayButton);

        final JButton clearOverlayButton = new JButton("clear overlay");
        clearOverlayButton.addActionListener(e -> clearOverlay());
        controls.add(clearOverlayButton);

        add(controls, BorderLayout.SOUTH);
    }

    private void bindActiveImage() {
        final ImagePlus active = WindowManager.getCurrentImage();
        if (active == null) {
            IJ.error("No active image found.");
            return;
        }
        boundImage = active;
        refreshBoundImageLabel();
    }

    private void refreshBoundImageLabel() {
        if (boundImage == null) {
            boundImageLabel.setText("bound image: (none)");
            return;
        }
        final String text = String.format(Locale.ROOT, "bound image: %s (%d×%d×%d)",
                boundImage.getTitle(), boundImage.getWidth(), boundImage.getHeight(), Math.max(1, boundImage.getNSlices()));
        boundImageLabel.setText(text);
    }

    private void addFromSelection() {
        if (!ensureBoundImage()) {
            return;
        }
        final Roi roi = boundImage.getRoi();
        if (roi == null) {
            IJ.error("No point selection found. Use the Point tool or Multi-point tool to click on the image, then press 'add from selection'.");
            return;
        }

        FloatPolygon points = null;
        if (roi instanceof PointRoi) {
            points = roi.getFloatPolygon();
        } else if (roi.getType() == Roi.POINT || roi.getType() == Roi.POLYGON) {
            points = roi.getFloatPolygon();
        }
        if (points == null || points.npoints <= 0) {
            IJ.error("Selection is not a point ROI.");
            return;
        }

        final int z = resolveZSlice(roi, boundImage);
        int added = 0;
        for (int i = 0; i < points.npoints; i++) {
            final double x = points.xpoints[i];
            final double y = points.ypoints[i];
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                continue;
            }
            model.addRow(new PointRow(nextId(), x, y, z, PointTableSchema.ROLE_PLANE_FIT));
            added++;
        }
        if (added > 0) {
            boundImage.killRoi();
            boundImage.updateAndDraw();
        }
    }

    private void deleteSelected() {
        final int[] selected = pointsTable.getSelectedRows();
        if (selected == null || selected.length == 0) {
            return;
        }
        model.removeRows(selected);
    }

    private void setRoleForSelected() {
        final int[] selected = pointsTable.getSelectedRows();
        if (selected == null || selected.length == 0) {
            return;
        }
        final String role = (String) roleCombo.getSelectedItem();
        model.setRoleForRows(selected, role == null ? PointTableSchema.ROLE_PLANE_FIT : role);
    }

    private void showOverlay() {
        if (!ensureBoundImage()) {
            return;
        }
        final Overlay overlay = new Overlay();
        int drawn = 0;
        for (PointRow row : model.rows()) {
            if (!Double.isFinite(row.x) || !Double.isFinite(row.y) || row.z <= 0) {
                continue;
            }
            final double diameter = OVERLAY_RADIUS_PX * 2.0;
            final OvalRoi roi = new OvalRoi(row.x - OVERLAY_RADIUS_PX, row.y - OVERLAY_RADIUS_PX, diameter, diameter);
            assignSlicePosition(roi, row.z);
            roi.setName(row.id + " (" + row.role + ")");
            roi.setStrokeWidth(1.0);
            overlay.add(roi);
            drawn++;
        }
        boundImage.setOverlay(overlay);
        boundImage.updateAndDraw();
        IJ.log("Geometry Points UI: drew " + drawn + " points as overlay.");
    }

    private void clearOverlay() {
        if (!ensureBoundImage()) {
            return;
        }
        boundImage.setOverlay(null);
        boundImage.updateAndDraw();
    }

    private boolean ensureBoundImage() {
        if (boundImage == null) {
            IJ.error("No image bound. Click 'bind active image' first.");
            return false;
        }
        return true;
    }

    private static int resolveZSlice(final Roi roi, final ImagePlus image) {
        int z = roi.getZPosition();
        if (z <= 0) {
            z = roi.getPosition();
        }
        if (z <= 0) {
            z = image.getCurrentSlice();
        }
        if (z <= 0) {
            z = 1;
        }
        return z;
    }

    private static void assignSlicePosition(final Roi roi, final int z) {
        try {
            Roi.class.getMethod("setPosition", int.class, int.class, int.class).invoke(roi, 0, z, 0);
        } catch (Throwable t) {
            roi.setPosition(z);
        }
    }

    private String nextId() {
        return String.format(Locale.ROOT, "p%03d", nextId++);
    }

    private static final class PointRow {
        private String id;
        private double x;
        private double y;
        private int z;
        private String role;

        private PointRow(final String id, final double x, final double y, final int z, final String role) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.role = role;
        }
    }

    private static final class PointTableModel extends AbstractTableModel {

        private final List<PointRow> rows = new ArrayList<>();
        private static final String[] COLUMNS = {"id", "x", "y", "z", "role"};

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(final int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(final int columnIndex) {
            if (columnIndex == 0 || columnIndex == 4) {
                return String.class;
            }
            if (columnIndex == 3) {
                return Integer.class;
            }
            return Double.class;
        }

        @Override
        public Object getValueAt(final int rowIndex, final int columnIndex) {
            final PointRow row = rows.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return row.id;
                case 1:
                    return row.x;
                case 2:
                    return row.y;
                case 3:
                    return row.z;
                case 4:
                    return row.role;
                default:
                    return null;
            }
        }

        @Override
        public boolean isCellEditable(final int rowIndex, final int columnIndex) {
            return true;
        }

        @Override
        public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
            final PointRow row = rows.get(rowIndex);
            try {
                switch (columnIndex) {
                    case 0:
                        row.id = aValue == null ? "" : String.valueOf(aValue);
                        break;
                    case 1:
                        row.x = parseDouble(aValue, row.x);
                        break;
                    case 2:
                        row.y = parseDouble(aValue, row.y);
                        break;
                    case 3:
                        row.z = Math.max(1, parseInt(aValue, row.z));
                        break;
                    case 4:
                        row.role = aValue == null ? PointTableSchema.ROLE_PLANE_FIT : String.valueOf(aValue);
                        break;
                    default:
                        break;
                }
            } finally {
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }

        private static double parseDouble(final Object value, final double fallback) {
            if (value == null) {
                return fallback;
            }
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException nfe) {
                return fallback;
            }
        }

        private static int parseInt(final Object value, final int fallback) {
            if (value == null) {
                return fallback;
            }
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException nfe) {
                return fallback;
            }
        }

        private void addRow(final PointRow row) {
            final int index = rows.size();
            rows.add(row);
            fireTableRowsInserted(index, index);
        }

        private void removeRows(final int[] selectedRows) {
            for (int i = selectedRows.length - 1; i >= 0; i--) {
                final int row = selectedRows[i];
                if (row >= 0 && row < rows.size()) {
                    rows.remove(row);
                }
            }
            fireTableDataChanged();
        }

        private void setRoleForRows(final int[] selectedRows, final String role) {
            for (int row : selectedRows) {
                if (row >= 0 && row < rows.size()) {
                    rows.get(row).role = role;
                }
            }
            fireTableDataChanged();
        }

        private List<PointRow> rows() {
            return rows;
        }
    }
}
