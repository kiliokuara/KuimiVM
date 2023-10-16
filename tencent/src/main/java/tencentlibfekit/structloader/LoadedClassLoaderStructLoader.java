package tencentlibfekit.structloader;

public class LoadedClassLoaderStructLoader extends StructLoader {
    private final ClassLoader classLoader;
    private final String mainClass;

    public LoadedClassLoaderStructLoader(ClassLoader classLoader, String mainClass) {
        this.classLoader = classLoader;
        this.mainClass = mainClass;
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public String getMainClass() {
        return mainClass;
    }
}
