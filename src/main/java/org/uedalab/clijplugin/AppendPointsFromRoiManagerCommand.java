package org.uedalab.clijplugin;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;
import ij.text.TextWindow;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;

@Plugin(type = Command.class, menuPath = "Plugins>Geometry Points>geometry points>append points from roi manager...")
public class AppendPointsFromRoiManagerCommand implements Command {

    @Parameter(label = "Image")
    private ImagePlus image;

    @Parameter(label = "Table title")
    private String tableTitle = "points";

    @Parameter(label = "ID prefix")
    private String idPrefix = "p";

    @Parameter(label = "Start index", min = "1")
    private int startIndex = 1;

    @Parameter(label = "Default role")
    private String defaultRole = PointTableSchema.ROLE_PLANE_FIT;

    @Parameter(label = "Z one-based")
    private boolean zOneBased = true;

    @Override
    public void run() {
        if (image == null) {
            IJ.error("Append Points", "No active image. Please open/select an image first.");
            return;
        }

        final RoiManager rm = RoiManager.getInstance2();
        if (rm == null || rm.getCount() == 0) {
            IJ.error("Append Points", "ROI Manager is empty. Add multipoint ROIs to ROI Manager first.");
            return;
        }

        final int[] selectedIndexes = rm.getSelectedIndexes();
        final List<Roi> roisToExport = new ArrayList<>();
        if (selectedIndexes != null && selectedIndexes.length > 0) {
            for (int index : selectedIndexes) {
                final Roi roi = rm.getRoi(index);
                if (roi != null) {
                    roisToExport.add(roi);
                }
            }
        } else {
            final Roi[] allRois = rm.getRoisAsArray();
            for (Roi roi : allRois) {
                if (roi != null) {
                    roisToExport.add(roi);
                }
            }
        }

        if (roisToExport.isEmpty()) {
            IJ.error("Append Points", "No valid ROIs found to export.");
            return;
        }

        final String resolvedTitle = tableTitle == null || tableTitle.trim().isEmpty() ? "points" : tableTitle;
        final String resolvedPrefix = idPrefix == null || idPrefix.isEmpty() ? "p" : idPrefix;
        final String resolvedRole = defaultRole == null || defaultRole.trim().isEmpty()
                ? PointTableSchema.ROLE_PLANE_FIT : defaultRole;

        final ResultsTable rt = getOrCreateResultsTable(resolvedTitle);
        int nextId = Math.max(1, startIndex);
        int exportedPoints = 0;
        int exportedRois = 0;

        for (Roi roi : roisToExport) {
            if (!(roi instanceof PointRoi)) {
                continue;
            }
            final FloatPolygon points = roi.getFloatPolygon();
            if (points == null || points.npoints <= 0) {
                continue;
            }

            final int z = resolveZ(roi, image, zOneBased);
            boolean roiContributed = false;
            for (int i = 0; i < points.npoints; i++) {
                final double x = points.xpoints[i];
                final double y = points.ypoints[i];
                if (!Double.isFinite(x) || !Double.isFinite(y)) {
                    continue;
                }
                rt.incrementCounter();
                rt.addValue(PointTableSchema.COL_ID, formatId(resolvedPrefix, nextId++));
                rt.addValue(PointTableSchema.COL_X, x);
                rt.addValue(PointTableSchema.COL_Y, y);
                rt.addValue(PointTableSchema.COL_Z, z);
                rt.addValue(PointTableSchema.COL_ROLE, resolvedRole);
                exportedPoints++;
                roiContributed = true;
            }
            if (roiContributed) {
                exportedRois++;
            }
        }

        rt.show(resolvedTitle);
        IJ.log("Exported " + exportedPoints + " points from " + exportedRois + " ROIs to table '"
                + resolvedTitle + "' (pixel coordinates).");
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

    private static int resolveZ(final Roi roi, final ImagePlus image, final boolean zOneBased) {
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
        return zOneBased ? z : Math.max(0, z - 1);
    }

    private static String formatId(final String prefix, final int index) {
        return String.format("%s%03d", prefix, Math.max(1, index));
    }
}
