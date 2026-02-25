package org.uedalab.clijplugin;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Plugin(type = Command.class, menuPath = "Plugins>Geometry Points>geometry points>show point table overlay...")
public class ShowPointTableOverlayCommand implements Command {

    @Parameter(label = "Image")
    private ImagePlus image;

    @Parameter(label = "Points table", required = false)
    private ResultsTable pointsTable;

    @Parameter(label = "Clear existing overlay")
    private boolean clearExistingOverlay = true;

    @Parameter(label = "Point radius (px)", min = "0.0001")
    private double pointRadiusPx = 3.0;

    @Parameter(label = "Show ID labels")
    private boolean showIdLabels = true;

    @Parameter(label = "Show role in label")
    private boolean showRoleInLabel = false;

    @Parameter(label = "Z is one-based in table")
    private boolean zOneBasedInTable = true;

    @Parameter(label = "Filter by role")
    private boolean filterByRole = false;

    @Parameter(label = "Allowed roles (CSV)")
    private String allowedRolesCsv = PointTableSchema.ROLE_AXIS_START + ","
            + PointTableSchema.ROLE_AXIS_END + "," + PointTableSchema.ROLE_PLANE_FIT;

    @Parameter(label = "Skip invalid rows")
    private boolean skipInvalidRows = true;

    @Override
    public void run() {
        if (image == null) {
            IJ.error("Show Point Table Overlay", "No active image. Please open/select an image first.");
            return;
        }
        if (pointRadiusPx <= 0) {
            IJ.error("Show Point Table Overlay", "Point radius must be > 0.");
            return;
        }

        final ResultsTable rt = pointsTable != null ? pointsTable : ResultsTable.getResultsTable();
        if (rt == null || !PointTableSchema.looksLikePointTable(rt)) {
            IJ.error("Show Point Table Overlay",
                    "Point table is missing required columns: id,x,y,z (role optional). "
                            + "Create or append a standardized point table first.");
            return;
        }

        final Set<String> allowedRoles = filterByRole ? parseAllowedRoles(allowedRolesCsv) : Collections.emptySet();
        final Overlay overlay = clearExistingOverlay || image.getOverlay() == null ? new Overlay() : image.getOverlay();
        final int totalRows = rt.getCounter();
        final int nSlices = Math.max(1, image.getNSlices());
        int drawnCount = 0;
        int skippedCount = 0;

        for (int row = 0; row < totalRows; row++) {
            final String id = safeString(rt, PointTableSchema.COL_ID, row);
            final String role = safeString(rt, PointTableSchema.COL_ROLE, row);
            if (filterByRole && !allowedRoles.contains(role.toLowerCase(Locale.ROOT))) {
                skippedCount++;
                continue;
            }

            final double x = rt.getValue(PointTableSchema.COL_X, row);
            final double y = rt.getValue(PointTableSchema.COL_Y, row);
            final double z = rt.getValue(PointTableSchema.COL_Z, row);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                if (!skipInvalidRows) {
                    IJ.error("Show Point Table Overlay", "Invalid numeric value at row " + row + ".");
                    return;
                }
                skippedCount++;
                continue;
            }

            int zSlice = (int) Math.round(z);
            if (!zOneBasedInTable) {
                zSlice = zSlice + 1;
            }
            if (zSlice < 1 || zSlice > nSlices) {
                if (!skipInvalidRows) {
                    IJ.error("Show Point Table Overlay", "Z slice out of range at row " + row + ": " + zSlice);
                    return;
                }
                skippedCount++;
                continue;
            }

            final double diameter = pointRadiusPx * 2.0;
            final OvalRoi roi = new OvalRoi(x - pointRadiusPx, y - pointRadiusPx, diameter, diameter);
            assignSlicePosition(roi, zSlice);

            if (showIdLabels) {
                String label = id == null ? "" : id.trim();
                final String roleText = role == null ? "" : role.trim();
                if (showRoleInLabel && !roleText.isEmpty()) {
                    label = label + " (" + roleText + ")";
                }
                roi.setName(label);
                roi.setStrokeWidth(1.0f);
            }
            overlay.add(roi);
            drawnCount++;
        }

        image.setOverlay(overlay);
        image.updateAndDraw();
        IJ.log("Point overlay: totalRows=" + totalRows + ", drawn=" + drawnCount + ", skipped=" + skippedCount
                + ", zOneBasedInTable=" + zOneBasedInTable + ", filterByRole=" + filterByRole + ".");
    }

    private static Set<String> parseAllowedRoles(final String csv) {
        final Set<String> roles = new HashSet<>();
        if (csv == null || csv.trim().isEmpty()) {
            return roles;
        }
        final String[] tokens = csv.split(",");
        for (String token : tokens) {
            final String value = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                roles.add(value);
            }
        }
        return roles;
    }

    private static String safeString(final ResultsTable rt, final String column, final int row) {
        final int col = rt.getColumnIndex(column);
        if (col == ResultsTable.COLUMN_NOT_FOUND) {
            return "";
        }
        return rt.getStringValue(column, row);
    }

    private static void assignSlicePosition(final Roi roi, final int zSlice) {
        try {
            Roi.class.getMethod("setPosition", int.class, int.class, int.class).invoke(roi, 0, zSlice, 0);
        } catch (Throwable t) {
            roi.setPosition(zSlice);
        }
    }
}
