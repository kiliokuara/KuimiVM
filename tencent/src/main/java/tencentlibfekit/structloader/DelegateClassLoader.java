package tencentlibfekit.structloader;

public class DelegateClassLoader extends ClassLoader {
    public DelegateClassLoader(String name, ClassLoader parent) {
        super(name, parent);
    }

    public DelegateClassLoader(ClassLoader parent) {
        super(parent);
    }

    public DelegateClassLoader() {
    }

    static {
        registerAsParallelCapable();
    }
}
