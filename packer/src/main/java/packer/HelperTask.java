package packer;

import io.github.karlatemp.unsafeaccessor.Root;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.*;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class HelperTask {
    public static void classInit(ClassWriter cw) {
        var init = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(3, 1);
    }

    public static void main(String[] args) throws Throwable {
        var taskName = args[0];
        var input = args[1];
        var output = args[2];


        var taskClass = Class.forName("packer.tasks." + taskName);

        Root.getTrusted(taskClass).findStatic(
                taskClass, "process", MethodType.methodType(void.class, String.class, String.class)
        ).invoke(input, output);
    }

    public static Map<String, byte[]> loadZip(File file) throws IOException {
        var rsp = new HashMap<String, byte[]>();
        try (var zipInput = new ZipInputStream(new BufferedInputStream(new FileInputStream(file), 20480))) {
            while (true) {
                var entry = zipInput.getNextEntry();
                if (entry == null) break;
                if (entry.isDirectory()) continue;
                rsp.put(entry.getName(), zipInput.readAllBytes());
            }
        }
        return rsp;
    }

    public static void writeZip(OutputStream outputStream, Map<String, byte[]> zip) throws IOException {
        var zos = new ZipOutputStream(new BufferedOutputStream(outputStream, 20480));

        for (var entry : zip.entrySet()) {
            zos.putNextEntry(new ZipEntry(entry.getKey()));
            zos.write(entry.getValue());
        }

        zos.finish();
        zos.flush();
    }

    public static String gitCommitHash() {
        try {
            var process = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            process.getOutputStream().close();
            try (var is = process.getInputStream()) {
                return new String(is.readAllBytes()).trim();
            } finally {
                process.waitFor();
                process.destroy();
            }
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}
