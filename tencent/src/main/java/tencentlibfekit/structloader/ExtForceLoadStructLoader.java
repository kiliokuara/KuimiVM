package tencentlibfekit.structloader;

import com.kiliokuara.kuimivm.transform.JarTransformer;
import io.github.karlatemp.unsafeaccessor.Unsafe;
import org.objectweb.asm.Type;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipInputStream;

public class ExtForceLoadStructLoader extends StructLoader {
    private final ClassLoader classLoader;

    public ExtForceLoadStructLoader(File stubJar, boolean debug) throws IOException {
        var classLoader = new DelegateClassLoader(getClass().getClassLoader());
        this.classLoader = classLoader;

        var transformer = new JarTransformer();
        try (var zipInputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(stubJar), 20480))) {
            transformer.loadFrom(zipInputStream);
        }


        transformer.setDebug(debug);
        transformer.generateClassStructure(Type.getObjectType("libfekit/VGen"));

        var oot = transformer.getOutput();

        for (var ootx : oot.values()) {
            var c = Unsafe.getUnsafe().defineClass(null, ootx, 0, ootx.length, classLoader, null);
            Unsafe.getUnsafe().ensureClassInitialized(c);
        }

    }


    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public String getMainClass() {
        return "libfekit.VGen";
    }
}
