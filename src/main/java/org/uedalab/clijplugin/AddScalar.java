package org.uedalab.clijplugin;

import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.macro.CLIJMacroPlugin;
import net.haesleinhuepf.clij.macro.CLIJOpenCLProcessor;
import net.haesleinhuepf.clij.macro.documentation.OffersDocumentation;
import net.haesleinhuepf.clij2.AbstractCLIJ2Plugin;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij2.utilities.HasAuthor;
import net.haesleinhuepf.clij2.utilities.HasClassifiedInputOutput;
import net.haesleinhuepf.clij2.utilities.HasLicense;
import net.haesleinhuepf.clij2.utilities.IsCategorized;
import org.scijava.plugin.Plugin;

import java.util.HashMap;

/**
 * Adds a scalar value to every pixel in the source image.
 */
@Plugin(type = CLIJMacroPlugin.class, name = "CLIJ2_addScalar")
public class AddScalar extends AbstractCLIJ2Plugin implements CLIJMacroPlugin, CLIJOpenCLProcessor, OffersDocumentation, HasAuthor, HasLicense, HasClassifiedInputOutput, IsCategorized {

    @Override
    public boolean executeCL() {
        return addScalar(getCLIJ2(), (ClearCLBuffer) args[0], (ClearCLBuffer) args[1], asFloat(args[2]));
    }

    private boolean addScalar(CLIJ2 clij2, ClearCLBuffer src, ClearCLBuffer dst, Float scalar) {
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("src", src);
        parameters.put("scalar", scalar);
        parameters.put("dst", dst);

        clij2.execute(AddScalar.class, "add_scalar.cl", "add_scalar", src.getDimensions(), src.getDimensions(), parameters);
        return true;
    }

    @Override
    public String getParameterHelpText() {
        return "Image source, ByRef Image destination, Number scalar";
    }

    @Override
    public String getDescription() {
        return "Adds the given scalar to each source pixel and writes the result to destination.";
    }

    @Override
    public String getAvailableForDimensions() {
        return "2D, 3D";
    }

    @Override
    public String getCategories() {
        return "Filter";
    }

    @Override
    public String getInputType() {
        return "Image";
    }

    @Override
    public String getOutputType() {
        return "Image";
    }
    @Override
    public String getAuthorName() {
        return "Ueda Lab";
    }
    
    @Override
    public String getLicense() {
        return "BSD 3-Clause";
    }
}
