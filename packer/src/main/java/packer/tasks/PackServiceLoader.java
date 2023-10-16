package packer.tasks;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import packer.HelperTask;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PackServiceLoader {
    public static void process(String input, String output) throws Throwable {
        System.out.println(input);

        try (var zipOutput = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
            zipOutput.putNextEntry(new ZipEntry("kfc/vivo50/code45/ServiceSelector.class"));

            var cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "kfc/vivo50/code45/ServiceSelector", null, "java/lang/Object", null);

            HelperTask.classInit(cw);

            {
                var mv = cw.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "mainClass", "()Ljava/lang/String;", null, null);
                mv.visitLdcInsn(Type.getObjectType("libvtx/VGenerated"));
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(0, 0);
            }

            {
                var mv = cw.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "resourceSha1", "()Ljava/lang/String;", null, null);

                mv.visitLdcInsn(HexFormat.of().formatHex(
                        MessageDigest.getInstance("sha1").digest(
                                Files.readAllBytes(Path.of(input))
                        )
                ));

                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(0, 0);
            }

            {
                var mv = cw.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "commitHash", "()Ljava/lang/String;", null, null);

                mv.visitLdcInsn(HelperTask.gitCommitHash());

                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(0, 0);
            }

            {
                var mv = cw.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "buildTime", "()J", null, null);

                mv.visitLdcInsn(System.currentTimeMillis());

                mv.visitInsn(Opcodes.LRETURN);
                mv.visitMaxs(0, 0);
            }

            {
                var mv = cw.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "isLocal", "()Z", null, null);

                mv.visitInsn(Opcodes.ICONST_0);

                mv.visitInsn(Opcodes.IRETURN);
                mv.visitMaxs(0, 0);
            }
            {
                var mv = cw.visitMethod(Opcodes.ACC_STATIC, "doRpc", "()Z", null, null);

                mv.visitInsn(Opcodes.ICONST_0);

                mv.visitInsn(Opcodes.IRETURN);
                mv.visitMaxs(0, 0);
            }

            {
                var mv = cw.visitMethod(Opcodes.ACC_STATIC, "get", "(Ljava/io/File;Ljava/io/File;)Lkfc/vivo50/code45/ConsoleServiceFactory;", null, null);
                mv.visitTypeInsn(Opcodes.NEW, "kfc/vivo50/code45/ConsoleServiceFactory$Packed");
                mv.visitInsn(Opcodes.DUP);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "kfc/vivo50/code45/ConsoleServiceFactory$Packed", "<init>", "(Ljava/io/File;Ljava/io/File;)V", false);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(0, 0);
            }

            zipOutput.write(cw.toByteArray());
        }
    }
}
