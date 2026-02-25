package org.uedalab.clijplugin;

import ij.IJ;
import ij.WindowManager;
import ij.measure.ResultsTable;
import ij.text.TextWindow;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.awt.Frame;

@Plugin(type = Command.class, menuPath = "Plugins>Geometry Points>geometry fit>compute line-plane angle...")
public class ComputeLinePlaneAngleCommand implements Command {

    @Parameter(label = "Line fit table", required = false)
    private ResultsTable lineFitTable;

    @Parameter(label = "Plane fit table", required = false)
    private ResultsTable planeFitTable;

    @Parameter(label = "Line row index", min = "0")
    private int lineRowIndex = 0;

    @Parameter(label = "Plane row index", min = "0")
    private int planeRowIndex = 0;

    @Parameter(label = "Output table title")
    private String outputTableTitle = "angle_line_plane";

    @Parameter(label = "Use absolute dot")
    private boolean useAbsoluteDot = true;

    @Override
    public void run() {
        final ResultsTable lineTable = resolveInputTable(lineFitTable, "fit_line");
        final ResultsTable planeTable = resolveInputTable(planeFitTable, "fit_plane");
        if (lineTable == null || planeTable == null) {
            IJ.error("Compute Line-Plane Angle",
                    "Run 'fit line from point table...' to create fit_line, and 'fit plane...' to create fit_plane.");
            return;
        }

        if (!hasColumn(lineTable, "line_dx") || !hasColumn(lineTable, "line_dy") || !hasColumn(lineTable, "line_dz")) {
            IJ.error("Compute Line-Plane Angle", "Line fit table must contain columns: line_dx, line_dy, line_dz.");
            return;
        }
        if (!hasColumn(planeTable, "plane_nx") || !hasColumn(planeTable, "plane_ny") || !hasColumn(planeTable, "plane_nz")) {
            IJ.error("Compute Line-Plane Angle", "Plane fit table must contain columns: plane_nx, plane_ny, plane_nz.");
            return;
        }
        if (lineRowIndex < 0 || lineRowIndex >= lineTable.getCounter()) {
            IJ.error("Compute Line-Plane Angle", "Line row index out of bounds: " + lineRowIndex);
            return;
        }
        if (planeRowIndex < 0 || planeRowIndex >= planeTable.getCounter()) {
            IJ.error("Compute Line-Plane Angle", "Plane row index out of bounds: " + planeRowIndex);
            return;
        }

        final double[] d = new double[]{
                lineTable.getValue("line_dx", lineRowIndex),
                lineTable.getValue("line_dy", lineRowIndex),
                lineTable.getValue("line_dz", lineRowIndex)
        };
        final double[] n = new double[]{
                planeTable.getValue("plane_nx", planeRowIndex),
                planeTable.getValue("plane_ny", planeRowIndex),
                planeTable.getValue("plane_nz", planeRowIndex)
        };
        if (!allFinite(d) || !allFinite(n)) {
            IJ.error("Compute Line-Plane Angle", "Direction/normal contains invalid numeric values.");
            return;
        }

        final double[] dn = normalize(d);
        final double[] nn = normalize(n);
        if (dn == null) {
            IJ.error("Compute Line-Plane Angle", "Line direction has zero length.");
            return;
        }
        if (nn == null) {
            IJ.error("Compute Line-Plane Angle", "Plane normal has zero length.");
            return;
        }

        double dot = dn[0] * nn[0] + dn[1] * nn[1] + dn[2] * nn[2];
        if (useAbsoluteDot) {
            dot = Math.abs(dot);
        }
        dot = Math.max(-1.0, Math.min(1.0, dot));

        final double thetaDeg = Math.toDegrees(Math.acos(dot));
        final double angleDeg = 90.0 - thetaDeg;

        final String resolvedTitle = outputTableTitle == null || outputTableTitle.trim().isEmpty()
                ? "angle_line_plane" : outputTableTitle;
        final ResultsTable out = getOrCreateResultsTable(resolvedTitle);
        out.incrementCounter();
        out.addValue("dot", dot);
        out.addValue("angle_line_normal_deg", thetaDeg);
        out.addValue("angle_line_plane_deg", angleDeg);
        out.addValue("line_row", lineRowIndex);
        out.addValue("plane_row", planeRowIndex);
        out.addValue("use_abs_dot", useAbsoluteDot ? 1 : 0);
        out.addValue("line_dx", dn[0]);
        out.addValue("line_dy", dn[1]);
        out.addValue("line_dz", dn[2]);
        out.addValue("plane_nx", nn[0]);
        out.addValue("plane_ny", nn[1]);
        out.addValue("plane_nz", nn[2]);
        out.show(resolvedTitle);

        IJ.log("Line-plane angle: dot=" + dot + ", angle(line,normal)=" + thetaDeg
                + " deg, angle(line,plane)=" + angleDeg + " deg.");
    }

    private static ResultsTable resolveInputTable(final ResultsTable injected, final String title) {
        if (injected != null) {
            return injected;
        }
        final ResultsTable byTitle = findTableByTitle(title);
        if (byTitle != null) {
            return byTitle;
        }
        return ResultsTable.getResultsTable();
    }

    private static ResultsTable findTableByTitle(final String title) {
        final Frame frame = WindowManager.getFrame(title);
        if (frame instanceof TextWindow) {
            final ResultsTable rt = ((TextWindow) frame).getTextPanel().getResultsTable();
            if (rt != null) {
                return rt;
            }
        }
        return null;
    }

    private static boolean hasColumn(final ResultsTable rt, final String name) {
        return rt.getColumnIndex(name) != ResultsTable.COLUMN_NOT_FOUND;
    }

    private static boolean allFinite(final double[] v) {
        return Double.isFinite(v[0]) && Double.isFinite(v[1]) && Double.isFinite(v[2]);
    }

    private static double[] normalize(final double[] v) {
        final double norm = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (!Double.isFinite(norm) || norm <= 1e-15) {
            return null;
        }
        return new double[]{v[0] / norm, v[1] / norm, v[2] / norm};
    }

    private static ResultsTable getOrCreateResultsTable(final String title) {
        final ResultsTable existing = findTableByTitle(title);
        return existing != null ? existing : new ResultsTable();
    }
}
