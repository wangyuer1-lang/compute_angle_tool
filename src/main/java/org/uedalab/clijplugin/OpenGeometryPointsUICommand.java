package org.uedalab.clijplugin;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import javax.swing.SwingUtilities;

@Plugin(type = Command.class, menuPath = "Plugins>Geometry Points>geometry ui>open geometry points ui...")
public class OpenGeometryPointsUICommand implements Command {

    @Override
    public void run() {
        SwingUtilities.invokeLater(() -> new GeometryPointsControlFrame().setVisible(true));
    }
}
