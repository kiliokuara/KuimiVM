package tencentlibfekit.structloader;

import io.vertx.core.impl.NoStackTraceThrowable;
import kfc.vivo50.code45.ServiceSelector;
import kfc.vivo50.code45.StubClasses;

import java.io.IOException;
import java.util.HashMap;

public class StubTransformStructLoader extends StructLoader {
    private final ClassLoader classLoader;

    public StubTransformStructLoader() {
        var mpx = new HashMap<String, Class<?>>();
        for (var c : StubClasses.classes()) {
            mpx.put(c.getName(), c);
        }

        this.classLoader = new ClassLoader(StubTransformStructLoader.this.getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (mpx.containsKey(name)) {
                    return vload(name);
                }
                return super.loadClass(name, resolve);
            }

            Class<?> vload(String name) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    var loaded = findLoadedClass(name);
                    if (loaded != null) return loaded;

                    var redef = mpx.get(name);
                    try (var rs = redef.getResourceAsStream(redef.getSimpleName() + ".class")) {
                        @SuppressWarnings("DataFlowIssue")
                        var data = rs.readAllBytes();

                        return defineClass(name, data, 0, data.length);
                    } catch (IOException ioException) {
                        throw new ClassNotFoundException(name, ioException);
                    } catch (Throwable throwable) {
                        throwable.addSuppressed(new NoStackTraceThrowable("Class: " + name));
                        throw throwable;
                    }
                }
            }


            static {
                ClassLoader.registerAsParallelCapable();
            }
        };

    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public String getMainClass() {
        return ServiceSelector.mainClass();
    }
}
