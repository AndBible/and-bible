package robolectric;

import net.bible.android.activity.BuildConfig;

import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.manifest.AndroidManifest;
import org.robolectric.res.FileFsFile;
import org.robolectric.util.Logger;
import org.robolectric.util.ReflectionHelpers;

/**
 * Provide teh correct settings for all tests e.g. the correct path to AndroidManifest.xml avoiding:
 * build/intermediates/bundles/debug/AndroidManifest.xml not found or not a file; it should point to your project's AndroidManifest.xml
 */
public class MyRobolectricTestRunner extends RobolectricTestRunner {

    private static final String BUILD_OUTPUT = "app/build/intermediates";

    public MyRobolectricTestRunner(Class<?> klass) throws InitializationError {
		super(klass);
    }

    @Override
    protected AndroidManifest getAppManifest(Config config) {
        Class<?> constants = config.constants();
        if (constants == Void.class) {
            constants = BuildConfig.class;
        }

        final String type = getType(constants);
        final String flavor = getFlavor(constants);
        final String packageName = getPackageName(constants);

        final FileFsFile res;
        final FileFsFile assets;
        final FileFsFile manifest;

        if (FileFsFile.from("src", "main", "res").exists()) {
            res = FileFsFile.from("src", "main", "res");
        } else if (FileFsFile.from(BUILD_OUTPUT, "res").exists()) {
            res = FileFsFile.from(BUILD_OUTPUT, "res", flavor, type);
        } else {
            res = FileFsFile.from(BUILD_OUTPUT, "bundles", flavor, type, "res");
        }

        if (FileFsFile.from(BUILD_OUTPUT, "assets").exists()) {
            assets = FileFsFile.from(BUILD_OUTPUT, "assets", flavor, type);
        } else {
            assets = FileFsFile.from(BUILD_OUTPUT, "bundles", flavor, type, "assets");
        }

        if (FileFsFile.from("src", "main", "AndroidManifest.xml").exists()) {
            manifest = FileFsFile.from("src", "main", "AndroidManifest.xml");
        } else if (FileFsFile.from(BUILD_OUTPUT, "manifests").exists()) {
            manifest = FileFsFile.from(BUILD_OUTPUT, "manifests", "full", flavor, type, "AndroidManifest.xml");
        } else {
            manifest = FileFsFile.from(BUILD_OUTPUT, "bundles", flavor, type, "AndroidManifest.xml");
        }

        Logger.debug("Robolectric assets directory: " + assets.getPath());
        Logger.debug("   Robolectric res directory: " + res.getPath());
        Logger.debug("   Robolectric manifest path: " + manifest.getFile().getAbsolutePath());
        Logger.debug("    Robolectric package name: " + packageName);
        return new AndroidManifest(manifest, res, assets, packageName);
    }

    private String getType(Class<?> constants) {
        try {
            return ReflectionHelpers.getStaticField(constants, "BUILD_TYPE");
        } catch (Throwable e) {
            return null;
        }
    }

    private String getFlavor(Class<?> constants) {
        try {
            return ReflectionHelpers.getStaticField(constants, "FLAVOR");
        } catch (Throwable e) {
            return null;
        }
    }

    private String getPackageName(Class<?> constants) {
        try {
            return ReflectionHelpers.getStaticField(constants, "APPLICATION_ID");
        } catch (Throwable e) {
            return null;
        }
    }
}