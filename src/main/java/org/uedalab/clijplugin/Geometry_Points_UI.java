package org.uedalab.clijplugin;

import ij.plugin.PlugIn;

import javax.swing.SwingUtilities;

public class Geometry_Points_UI implements PlugIn {

    @Override
    public void run(final String arg) {
        SwingUtilities.invokeLater(() -> new GeometryPointsControlFrame().setVisible(true));
    }
}
