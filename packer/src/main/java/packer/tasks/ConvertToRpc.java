package packer.tasks;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import packer.HelperTask;

import java.io.File;
import java.io.FileOutputStream;

public class ConvertToRpc {
    public static void process(String input, String output) throws Throwable {
        var zip = HelperTask.loadZip(new File(input));

        var cnnode = new ClassNode();
        new ClassReader(zip.get("kfc/vivo50/code45/ServiceSelector.class")).accept(cnnode, 0);

        {
            for (var met : cnnode.methods) {
                if (met.name.equals("doRpc")) {
                    met.instructions.clear();
                    met.visitInsn(Opcodes.ICONST_1);
                    met.visitInsn(Opcodes.IRETURN);
                }
                if (met.name.equals("get")) {
                    met.instructions.clear();
                    met.visitInsn(Opcodes.ACONST_NULL);
                    met.visitInsn(Opcodes.ARETURN);
                }
            }

            var cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cnnode.accept(cw);
            zip.put("kfc/vivo50/code45/ServiceSelector.class", cw.toByteArray());
        }

        try (var out = new FileOutputStream(output)) {
            HelperTask.writeZip(out, zip);
        }
    }
}
