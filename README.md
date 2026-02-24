## Ueda Lab CLIJ2 Plugin Template

This repository is a clean template for Ueda Lab members to create new CLIJ2 plugins.

It ships with a working add-scalar example operation.
The example behavior is intentionally unchanged from the template baseline.
Only the `org.uedalab.clijplugin` package tree is kept in this template.

Template example files:

- [AddScalar.java](src/main/java/org/uedalab/clijplugin/AddScalar.java)
- [add_scalar.cl](src/main/java/org/uedalab/clijplugin/add_scalar.cl)

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
