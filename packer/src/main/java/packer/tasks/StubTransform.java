package packer.tasks;

import com.kiliokuara.kuimivm.transform.JarTransformer;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import packer.HelperTask;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class StubTransform {
    public static void process(String input, String output) throws Throwable {
        var transform = new JarTransformer();
        transform.setDebug(Boolean.parseBoolean(System.getenv("PACKER_DEBUG")));
        System.out.println("SRFC: " + input);

        try (var zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(input), 20480))) {
            transform.loadFrom(zip);
        }
        transform.generateClassStructure(Type.getObjectType("libvtx/VGenerated"));

        try (var outputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output), 20480))) {
            var oot = transform.getOutput();
            for (var entry : oot.entrySet()) {
                // System.out.println("Writing " + entry.getKey());
                outputStream.putNextEntry(new ZipEntry(entry.getKey() + ".class"));
                outputStream.write(entry.getValue());
            }

            outputStream.putNextEntry(new ZipEntry("kfc/vivo50/code45/StubClasses.class"));

            var cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "kfc/vivo50/code45/StubClasses", null, "java/lang/Object", null);

            HelperTask.classInit(cw);

            {
                var mt = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "classes", "()Ljava/util/List;", null, null);
                mt.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
                mt.visitInsn(Opcodes.DUP);
                mt.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);

                for (var name : oot.keySet()) {
                    mt.visitInsn(Opcodes.DUP);
                    mt.visitLdcInsn(Type.getObjectType(name));
                    mt.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
                    mt.visitInsn(Opcodes.POP);
                }

                mt.visitInsn(Opcodes.ARETURN);
                mt.visitMaxs(0, 0);
            }

            outputStream.write(cw.toByteArray());
        }
    }
}
