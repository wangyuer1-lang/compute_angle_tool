package org.uedalab.clijplugin;

import ij.measure.ResultsTable;

public final class PointTableSchema {

    public static final String COL_ID = "id";
    public static final String COL_X = "x";
    public static final String COL_Y = "y";
    public static final String COL_Z = "z";
    public static final String COL_ROLE = "role";

    public static final String ROLE_AXIS_START = "axis_start";
    public static final String ROLE_AXIS_END = "axis_end";
    public static final String ROLE_PLANE_FIT = "plane_fit";
    public static final String ROLE_IGNORE = "ignore";

    private PointTableSchema() {
    }

    public static boolean looksLikePointTable(final ResultsTable rt) {
        if (rt == null) {
            return false;
        }
        return rt.getColumnIndex(COL_ID) != ResultsTable.COLUMN_NOT_FOUND
                && rt.getColumnIndex(COL_X) != ResultsTable.COLUMN_NOT_FOUND
                && rt.getColumnIndex(COL_Y) != ResultsTable.COLUMN_NOT_FOUND
                && rt.getColumnIndex(COL_Z) != ResultsTable.COLUMN_NOT_FOUND;
    }
}
