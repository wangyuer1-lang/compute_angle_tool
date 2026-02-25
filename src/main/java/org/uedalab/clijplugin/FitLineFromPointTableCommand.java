package org.uedalab.clijplugin;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.text.TextWindow;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.awt.Frame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Plugin(type = Command.class, menuPath = "Plugins>Geometry Points>geometry fit>fit line from point table...")
public class FitLineFromPointTableCommand implements Command {

    @Parameter(label = "Image", required = false)
    private ImagePlus image;

    @Parameter(label = "Points table", required = false)
    private ResultsTable pointsTable;

    @Parameter(label = "Z is one-based in table")
    private boolean zOneBasedInTable = true;

    @Parameter(label = "Filter by role")
    private boolean filterByRole = false;

    @Parameter(label = "Allowed roles (CSV)")
    private String allowedRolesCsv = PointTableSchema.ROLE_AXIS_START + ","
            + PointTableSchema.ROLE_AXIS_END + "," + PointTableSchema.ROLE_PLANE_FIT;

    @Parameter(label = "Draw overlay")
    private boolean drawOverlay = true;

    @Parameter(label = "Overlay Z slice", min = "1")
    private int overlayZSlice = 1;

    @Parameter(label = "Overlay half length (px)", min = "0.0001")
    private double overlayHalfLengthPx = 50.0;

    @Parameter(label = "Output table title")
    private String outputTableTitle = "fit_line";

    @Override
    public void run() {
        final ResultsTable rt = pointsTable != null ? pointsTable : ResultsTable.getResultsTable();
        if (rt == null || !PointTableSchema.looksLikePointTable(rt)) {
            IJ.error("Fit Line", "Point table is missing required columns: id,x,y,z (role optional).");
            return;
        }

        final Set<String> allowedRoles = filterByRole ? parseAllowedRoles(allowedRolesCsv) : Collections.emptySet();
        final List<double[]> points = new ArrayList<>();
        final Set<String> rolesUsed = new HashSet<>();
        final int totalRows = rt.getCounter();
        for (int row = 0; row < totalRows; row++) {
            final String role = safeString(rt, PointTableSchema.COL_ROLE, row).trim();
            if (filterByRole && !allowedRoles.contains(role.toLowerCase(Locale.ROOT))) {
                continue;
            }

            final double x = rt.getValue(PointTableSchema.COL_X, row);
            final double y = rt.getValue(PointTableSchema.COL_Y, row);
            final double zRaw = rt.getValue(PointTableSchema.COL_Z, row);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(zRaw)) {
                continue;
            }
            final double z = zOneBasedInTable ? zRaw - 1.0 : zRaw;
            if (!Double.isFinite(z)) {
                continue;
            }
            points.add(new double[]{x, y, z});
            if (!role.isEmpty()) {
                rolesUsed.add(role);
            }
        }

        if (points.size() < 2) {
            IJ.error("Fit Line", "Need at least 2 valid points to fit a line.");
            return;
        }

        final Pca3DUtils.LineFitResult fit = Pca3DUtils.fitLine(points);
        final double[] centroid = fit.centroid;
        final double[] direction = fit.direction;
        final double rms = fit.rmsDist;

        final String resolvedOutputTitle = outputTableTitle == null || outputTableTitle.trim().isEmpty()
                ? "fit_line" : outputTableTitle;
        final ResultsTable out = getOrCreateResultsTable(resolvedOutputTitle);
        out.incrementCounter();
        out.addValue("n_points", points.size());
        out.addValue("line_cx", centroid[0]);
        out.addValue("line_cy", centroid[1]);
        out.addValue("line_cz", centroid[2]);
        out.addValue("line_dx", direction[0]);
        out.addValue("line_dy", direction[1]);
        out.addValue("line_dz", direction[2]);
        out.addValue("rms_dist", rms);
        out.addValue("roles_used", summarizeRoles(rolesUsed, filterByRole));
        out.show(resolvedOutputTitle);

        if (drawOverlay && image != null) {
            final double x1 = centroid[0] - overlayHalfLengthPx * direction[0];
            final double y1 = centroid[1] - overlayHalfLengthPx * direction[1];
            final double x2 = centroid[0] + overlayHalfLengthPx * direction[0];
            final double y2 = centroid[1] + overlayHalfLengthPx * direction[1];

            final Overlay overlay = image.getOverlay() == null ? new Overlay() : image.getOverlay();
            final Line line = new Line(x1, y1, x2, y2);
            line.setName("fit_line_xy_projection");
            line.setStrokeWidth(1.5);

            final int zSlice = resolveOverlaySlice(image, overlayZSlice);
            assignSlicePosition(line, zSlice);
            overlay.add(line);
            image.setOverlay(overlay);
            image.updateAndDraw();
            IJ.log("Drew fit line XY projection at zSlice=" + zSlice + " on image '" + image.getTitle() + "'.");
        }

        IJ.log("Fit line from point table: n_points=" + points.size() + ", centroid=("
                + centroid[0] + "," + centroid[1] + "," + centroid[2] + "), direction=("
                + direction[0] + "," + direction[1] + "," + direction[2] + "), rms=" + rms + ".");
    }

    private static Set<String> parseAllowedRoles(final String csv) {
        final Set<String> roles = new HashSet<>();
        if (csv == null || csv.trim().isEmpty()) {
            return roles;
        }
        for (String token : csv.split(",")) {
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
        final String value = rt.getStringValue(column, row);
        return value == null ? "" : value;
    }

    private static ResultsTable getOrCreateResultsTable(final String title) {
        final Frame frame = WindowManager.getFrame(title);
        if (frame instanceof TextWindow) {
            final ResultsTable existing = ((TextWindow) frame).getTextPanel().getResultsTable();
            if (existing != null) {
                return existing;
            }
        }
        return new ResultsTable();
    }

    private static String summarizeRoles(final Set<String> roles, final boolean filtered) {
        if (roles.isEmpty()) {
            return filtered ? "(filtered; no role values)" : "(all/non-empty roles unavailable)";
        }
        final List<String> sorted = new ArrayList<>(roles);
        Collections.sort(sorted);
        return String.join(",", sorted);
    }

    private static int resolveOverlaySlice(final ImagePlus image, final int requestedSlice) {
        int zSlice = requestedSlice <= 0 ? 1 : requestedSlice;
        if (image != null && requestedSlice == 1) {
            zSlice = image.getCurrentSlice();
        }
        final int nSlices = Math.max(1, image.getNSlices());
        if (zSlice < 1) {
            return 1;
        }
        if (zSlice > nSlices) {
            return nSlices;
        }
        return zSlice;
    }

    private static void assignSlicePosition(final Roi roi, final int zSlice) {
        try {
            Roi.class.getMethod("setPosition", int.class, int.class, int.class).invoke(roi, 0, zSlice, 0);
        } catch (Throwable t) {
            roi.setPosition(zSlice);
        }
    }

}
