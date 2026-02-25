package org.uedalab.clijplugin;

import ij.IJ;
import ij.measure.ResultsTable;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, menuPath = "Plugins>Geometry Points>geometry points>create point table...")
public class CreatePointTableCommand implements Command {

    @Parameter(label = "Table title")
    private String tableTitle = "points";

    @Parameter(label = "Initial rows", min = "1")
    private int initialRows = 1;

    @Override
    public void run() {
        final String resolvedTitle = tableTitle == null || tableTitle.trim().isEmpty() ? "points" : tableTitle;
        final int rows = Math.max(1, initialRows);

        final ResultsTable rt = new ResultsTable();
        for (int i = 0; i < rows; i++) {
            rt.incrementCounter();
            rt.addValue(PointTableSchema.COL_ID, "");
            rt.addValue(PointTableSchema.COL_X, Double.NaN);
            rt.addValue(PointTableSchema.COL_Y, Double.NaN);
            rt.addValue(PointTableSchema.COL_Z, Double.NaN);
            rt.addValue(PointTableSchema.COL_ROLE, PointTableSchema.ROLE_PLANE_FIT);
        }

        rt.show(resolvedTitle);
        IJ.log("Created point table '" + resolvedTitle + "' with columns: "
                + PointTableSchema.COL_ID + "," + PointTableSchema.COL_X + "," + PointTableSchema.COL_Y + ","
                + PointTableSchema.COL_Z + "," + PointTableSchema.COL_ROLE);
    }
}
