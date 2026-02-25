## Geometry Points Tools

This repository provides Fiji tools for standardized geometry point workflows.
It includes point table creation, ROI Manager import, point overlay display, line/plane fitting, and line-plane angle computation.
The existing `AddScalar` example remains unchanged.

Included files:

- [AddScalar.java](src/main/java/org/uedalab/clijplugin/AddScalar.java)
- [add_scalar.cl](src/main/java/org/uedalab/clijplugin/add_scalar.cl)
- `Plugins > Geometry Points > geometry points > create point table...` creates a standardized point table (`id,x,y,z,role`).
- `Plugins > Geometry Points > geometry points > append points from roi manager...` appends points from ROI Manager into the standardized table; recommended workflow: Multi-point tool -> ROI Manager -> append to table.
- `Plugins > Geometry Points > geometry points > show point table overlay...` visualizes the standardized point table on the active image.
- `Plugins > Geometry Points > geometry fit > fit line from point table...` outputs line centroid/direction/RMS and can draw a projected overlay line.
- `Plugins > Geometry Points > geometry fit > fit plane from point table...` outputs centroid/normal/RMS/max and can draw a projected normal overlay line.
- `Plugins > Geometry Points > geometry fit > compute line-plane angle...` reads fit_line and fit_plane tables and outputs the angle.

## UI

- `Plugins > Geometry Points > geometry ui > open geometry points ui...` opens a DM3D-style control window with embedded image view, point list, fitting panel, and model list.
- Workflow: bind image -> left-click image to add points -> run fitting (line/plane) -> select models/points to highlight -> compute line-plane angle.

## How to rename for a new plugin

1. Update `groupId` (in `pom.xml`).
2. Rename Java package path and `package` declaration.
3. Rename plugin class.
4. Rename plugin function name in `@Plugin(..., name = "CLIJ2_...")`.
5. Rename OpenCL `.cl` file and update filename in `clij2.execute(...)`.

## Build

```bash
mvn -DskipTests package
```

## Optional Fiji deployment

```bash
mvn -Dscijava.app.directory="C:/path/to/Fiji.app" install
```

## Minimal macro test

```java
run("CLIJ2 Macro Extensions", "cl_device=");
Ext.CLIJ2_help("addScalar");
```

Expected signature includes:

```java
Ext.CLIJ2_addScalar(Image source, Image destination, Number scalar);
```

<details>
<summary>Attribution</summary>

This template derives from upstream CLIJ template work.
See [NOTICE](NOTICE) and [LICENSE](LICENSE).

</details>
