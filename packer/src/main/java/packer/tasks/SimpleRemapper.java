package packer.tasks;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import packer.HelperTask;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

public class SimpleRemapper {
    public static void process(String input, String output) throws Throwable {
        var zip = HelperTask.loadZip(new File(input));

        var processPackages = List.of(
                "kfc/",
                "com/kiliokuara/kuimivm/"
        );

        var classes = new HashMap<String, ClassNode>();
        var remapTable = new HashMap<String, String>();
        var toolkit = new Object() {
            private final Set<String> usedNames = new HashSet<>();

            String nextName() {
                while (true) {
                    var newName = "n" + UUID.randomUUID().toString().replace("-", "");
                    if (usedNames.add(newName)) return newName;
                }
            }

            boolean needRemap(String classname) {
                for (var pkg : processPackages) {
                    if (classname.startsWith(pkg)) return true;
                }
                return false;
            }

            String getPackage(String cname) {
                var lastIdx = cname.lastIndexOf('/');
                return cname.substring(0, lastIdx);
            }
        };

        zip.entrySet().removeIf(entry -> {
            if (entry.getKey().endsWith(".class")) {
                var cnode = new ClassNode();
                new ClassReader(entry.getValue()).accept(cnode, 0);
                classes.put(cnode.name, cnode);
                return true;
            }
            return false;
        });

        var pkgRemap = new HashMap<String, String>();

        for (var klass : classes.values()) {
            if (!toolkit.needRemap(klass.name)) continue;

            var pkg = toolkit.getPackage(klass.name);
            pkgRemap.computeIfAbsent(pkg, $ -> "kfc/" + toolkit.nextName());
        }


        for (var klass : classes.values()) {
            if (!toolkit.needRemap(klass.name)) continue;
            var pkg = toolkit.getPackage(klass.name);

            var rname = pkgRemap.get(pkg) + '/' + toolkit.nextName();
            remapTable.put(klass.name, rname);
        }

        var remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                return remapTable.getOrDefault(internalName, internalName);
            }
        };

        for (var klass : classes.values()) {
            var newKlass = new ClassNode();
            klass.accept(new ClassRemapper(newKlass, remapper));

            var cw = new ClassWriter(0);
            newKlass.accept(cw);

            zip.put(newKlass.name + ".class", cw.toByteArray());
        }

        try (var out = new FileOutputStream(output)) {
            HelperTask.writeZip(out, zip);
        }
    }
}
