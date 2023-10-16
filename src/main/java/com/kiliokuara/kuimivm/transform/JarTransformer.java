package com.kiliokuara.kuimivm.transform;

import com.kiliokuara.kuimivm.*;
import com.kiliokuara.kuimivm.abstractvm.ClassPool;
import com.kiliokuara.kuimivm.execute.StackTrace;
import com.kiliokuara.kuimivm.abstractvm.KuimiAbstractVM;
import com.kiliokuara.kuimivm.objects.KuimiString;
import com.kiliokuara.kuimivm.runtime.ArrayAccess;
import com.kiliokuara.kuimivm.runtime.ClassInitEnsure;
import com.kiliokuara.kuimivm.runtime.FieldAccessBridge;
import com.kiliokuara.kuimivm.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipInputStream;


@SuppressWarnings("DuplicatedCode")
public class JarTransformer {
    private static final Type KUIMI_VM = Type.getType(KuimiVM.class);
    private static final Type STACK_TRACE = Type.getType(StackTrace.class);
    private static final Type KUIMI_OBJECT = Type.getType(KuimiObject.class);
    private static final Type KUIMI_CLASS = Type.getType(KuimiClass.class);
    private static final Type KUIMI_FIELD = Type.getType(KuimiField.class);
    private static final Type KUIMI_FIELD_TABLE = Type.getType(KuimiFieldTable.class);
    private static final Type KUIMI_METHOD = Type.getType(KuimiMethod.class);
    private static final Type KUIMI_METHOD_TABLE = Type.getType(KuimiMethodTable.class);
    private static final Type ARRAY_ACCESS_BRIDGE = Type.getType(ArrayAccess.class);
    private static final Type FIELD_ACCESS_BRIDGE = Type.getType(FieldAccessBridge.class);
    private static final Type CLASS_INIT_ENSURE = Type.getType(ClassInitEnsure.class);
    private static final Type TYPE = Type.getType(Type.class);
    private static final Type STRING = Type.getType(String.class);


    private final Map<String, ClassNode> classes;
    private final Map<String, byte[]> output;
    private boolean debug;

    public JarTransformer(Map<String, ClassNode> classes) {
        this.classes = classes;
        output = new HashMap<>();
    }

    public JarTransformer() {
        this(new HashMap<>());
    }

    public void merge(Map<String, ClassNode> classes) {
        this.classes.putAll(classes);
    }

    public void loadFrom(ZipInputStream zipInputStream) throws IOException {
        while (true) {
            var entry = zipInputStream.getNextEntry();
            if (entry == null) break;
            if (!entry.getName().endsWith(".class")) continue;

            var cr = new ClassNode();
            new ClassReader(zipInputStream.readAllBytes()).accept(cr, 0);

            classes.put(cr.name, cr);
        }
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    protected void generateClassStructure_classRegister(MethodVisitor visitor) {
        var absVM = Type.getType(KuimiAbstractVM.class).getInternalName();
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitTypeInsn(Opcodes.CHECKCAST, absVM);

        var classPool = Type.getType(ClassPool.class);
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, absVM, "getBootstrapPool", Type.getMethodDescriptor(classPool), false);

        visitor.visitVarInsn(Opcodes.ALOAD, 1);
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, classPool.getInternalName(), "put", Type.getMethodDescriptor(Type.VOID_TYPE, KUIMI_CLASS), false);

        visitor.visitInsn(Opcodes.RETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }

    private static void pushInt(MethodVisitor visitor, int value) {
        if (value == -1) {
            visitor.visitInsn(Opcodes.ICONST_M1);
        } else if (value >= 0 && value <= 5) {
            visitor.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            visitor.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            visitor.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            visitor.visitLdcInsn(value);
        }
    }

    public void generateClassStructure(Type outputName) {
        if (outputName == null) {
            outputName = Type.getObjectType("com/kiliokuara/kuimivm/transform/gen/" + UUID.randomUUID());
        }
        var outInternalName = outputName.getInternalName();


        var classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        classWriter.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, outInternalName, null, "java/lang/Object", null);

        {
            var classRegister = classWriter.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "classRegister", Type.getMethodDescriptor(Type.VOID_TYPE, KUIMI_VM, KUIMI_CLASS), null, null);
            generateClassStructure_classRegister(classRegister);
        }

        {
            // static KuimiClass loadClass(KuimiVM vm, String name, KuimiObject<?> classLoader)
            var loadClass = classWriter.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "loadClass", Type.getMethodDescriptor(KUIMI_CLASS, KUIMI_VM, STRING, KUIMI_OBJECT), null, null);

            {
                loadClass.visitVarInsn(Opcodes.ALOAD, 0);
                loadClass.visitVarInsn(Opcodes.ALOAD, 1);
                loadClass.visitMethodInsn(Opcodes.INVOKESTATIC, TYPE.getInternalName(), "getObjectType", Type.getMethodDescriptor(TYPE, STRING), false);
                loadClass.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_VM.getInternalName(), "resolveClass", Type.getMethodDescriptor(KUIMI_CLASS, TYPE), false);

                var cont = new Label();
                loadClass.visitInsn(Opcodes.DUP);
                loadClass.visitJumpInsn(Opcodes.IFNULL, cont);
                loadClass.visitInsn(Opcodes.ARETURN);

                loadClass.visitLabel(cont);
                loadClass.visitInsn(Opcodes.POP);
            }

            {
                var hashCodeToString = new HashMap<Integer, Set<String>>();
                var hashCodeToLabel = new HashMap<Integer, Label>();
                var classNameToLabel = new HashMap<String, Label>();
                var failLabel = new Label();

                for (var klass : classes.values()) {
                    var hcode = klass.name.hashCode();
                    hashCodeToString.computeIfAbsent(hcode, $ -> new HashSet<>()).add(klass.name);
                    classNameToLabel.put(klass.name, new Label());
                }
                for (var hcode : hashCodeToString.keySet()) {
                    hashCodeToLabel.put(hcode, new Label());
                }

                loadClass.visitVarInsn(Opcodes.ALOAD, 1);
                loadClass.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING.getInternalName(), "hashCode", "()I", false);
                {
                    var tableLookupKeys = new int[hashCodeToLabel.size()];
                    var tableLookupTargets = new Label[tableLookupKeys.length];
                    var i = 0;
                    for (var entry : hashCodeToLabel.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                        tableLookupKeys[i] = entry.getKey();
                        tableLookupTargets[i] = entry.getValue();
                        i++;
                    }
                    loadClass.visitLookupSwitchInsn(failLabel, tableLookupKeys, tableLookupTargets);
                }

                for (var h2labelEntry : hashCodeToLabel.entrySet()) {
                    loadClass.visitLabel(h2labelEntry.getValue());
                    var strs = hashCodeToString.get(h2labelEntry.getKey());

                    for (var str : strs) {
                        loadClass.visitLdcInsn(str);
                        loadClass.visitVarInsn(Opcodes.ALOAD, 1);
                        loadClass.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                        loadClass.visitJumpInsn(Opcodes.IFNE, classNameToLabel.get(str));
                    }
                    loadClass.visitJumpInsn(Opcodes.GOTO, failLabel);
                }

                loadClass.visitLabel(failLabel);
                loadClass.visitTypeInsn(Opcodes.NEW, "java/lang/UnsupportedOperationException");
                loadClass.visitInsn(Opcodes.DUP);
                loadClass.visitVarInsn(Opcodes.ALOAD, 1);
                loadClass.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V", false);
                loadClass.visitInsn(Opcodes.ATHROW);

                for (var klass : classes.values()) {
                    loadClass.visitLabel(classNameToLabel.get(klass.name));


                    loadClass.visitTypeInsn(Opcodes.NEW, KUIMI_CLASS.getInternalName());
                    loadClass.visitInsn(Opcodes.DUP);

                    loadClass.visitVarInsn(Opcodes.ALOAD, 0);
                    loadClass.visitLdcInsn(klass.name);
                    loadClass.visitMethodInsn(Opcodes.INVOKESTATIC, TYPE.getInternalName(), "getObjectType", Type.getMethodDescriptor(TYPE, STRING), false);
                    pushInt(loadClass, klass.access);
                    loadClass.visitVarInsn(Opcodes.ALOAD, 2); // classLoader

                    // parent class
                    loadClass.visitVarInsn(Opcodes.ALOAD, 0);
                    loadClass.visitLdcInsn(klass.superName);
                    loadClass.visitVarInsn(Opcodes.ALOAD, 2);
                    loadClass.visitMethodInsn(Opcodes.INVOKESTATIC, outInternalName, "loadClass", Type.getMethodDescriptor(KUIMI_CLASS, KUIMI_VM, STRING, KUIMI_OBJECT), false);

                    // interfaces
                    if (klass.interfaces == null || klass.interfaces.isEmpty()) {
                        loadClass.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/List", "of", "()Ljava/util/List;", true);
                    } else {
                        loadClass.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
                        loadClass.visitInsn(Opcodes.DUP);
                        loadClass.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);

                        for (var itf : klass.interfaces) {
                            loadClass.visitInsn(Opcodes.DUP);

                            loadClass.visitVarInsn(Opcodes.ALOAD, 0);
                            loadClass.visitLdcInsn(itf);
                            loadClass.visitVarInsn(Opcodes.ALOAD, 2);
                            loadClass.visitMethodInsn(Opcodes.INVOKESTATIC, outInternalName, "loadClass", Type.getMethodDescriptor(KUIMI_CLASS, KUIMI_VM, STRING, KUIMI_OBJECT), false);

                            loadClass.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
                            loadClass.visitInsn(Opcodes.POP);
                        }
                    }

                    loadClass.visitMethodInsn(Opcodes.INVOKESPECIAL, KUIMI_CLASS.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, KUIMI_VM, TYPE, Type.INT_TYPE, KUIMI_OBJECT, KUIMI_CLASS, Type.getObjectType("java/util/List")), false);
                    loadClass.visitInsn(Opcodes.DUP);

                    loadClass.visitVarInsn(Opcodes.ALOAD, 0);
                    loadClass.visitInsn(Opcodes.SWAP);

                    loadClass.visitMethodInsn(Opcodes.INVOKESTATIC, outInternalName, "classRegister", Type.getMethodDescriptor(Type.VOID_TYPE, KUIMI_VM, KUIMI_CLASS), false);

                    loadClass.visitInsn(Opcodes.ARETURN);
                }

                loadClass.visitMaxs(0, 0);

            }


            var tool = new Object() {
                public void resolveClass(MethodVisitor init, Type type) {
                    init.visitVarInsn(Opcodes.ALOAD, 0);
                    init.visitLdcInsn(type.getDescriptor());
                    init.visitMethodInsn(Opcodes.INVOKESTATIC, TYPE.getInternalName(), "getType", Type.getMethodDescriptor(TYPE, STRING), false);
                    init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_VM.getInternalName(), "resolveClass", Type.getMethodDescriptor(KUIMI_CLASS, TYPE), false);
                }

                void directLoadClass(MethodVisitor init, String name) {

                    init.visitVarInsn(Opcodes.ALOAD, 0);
                    init.visitLdcInsn(name);
                    init.visitVarInsn(Opcodes.ALOAD, 1);
                    init.visitMethodInsn(Opcodes.INVOKESTATIC, outInternalName, "loadClass", Type.getMethodDescriptor(KUIMI_CLASS, KUIMI_VM, STRING, KUIMI_OBJECT), false);

                }
            };

            // static void init(KuimiVM vm, KuimiObject<?> classLoader)
            var init = classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "initialize", Type.getMethodDescriptor(Type.VOID_TYPE, KUIMI_VM, KUIMI_OBJECT), null, null);
            for (var klass : classes.values()) {
                tool.directLoadClass(init, klass.name);
                init.visitInsn(Opcodes.POP);
            }

            for (var kclass : classes.values()) {
                tool.directLoadClass(init, kclass.name);

                if (debug) {
                    init.visitInsn(Opcodes.DUP);
                    init.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                    init.visitInsn(Opcodes.SWAP);
                    init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
                }

                // @context: arg2 = <current-class>
                init.visitInsn(Opcodes.DUP);
                init.visitVarInsn(Opcodes.ASTORE, 2);

                init.visitInsn(Opcodes.DUP); // for method table

                // region fields
                init.visitInsn(Opcodes.DUP);
                init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_CLASS.getInternalName(), "getFieldTable", Type.getMethodDescriptor(KUIMI_FIELD_TABLE), false);

                for (var field : kclass.fields) {
                    init.visitInsn(Opcodes.DUP);

                    init.visitTypeInsn(Opcodes.NEW, KUIMI_FIELD.getInternalName());
                    init.visitInsn(Opcodes.DUP);

                    init.visitVarInsn(Opcodes.ALOAD, 2);// declared class
                    pushInt(init, field.access);
                    init.visitLdcInsn(field.name);
                    tool.resolveClass(init, Type.getType(field.desc));
                    init.visitMethodInsn(Opcodes.INVOKESPECIAL, KUIMI_FIELD.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, KUIMI_CLASS, Type.INT_TYPE, STRING, KUIMI_CLASS), false);

                    if (debug) {
                        init.visitInsn(Opcodes.DUP);
                        init.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                        init.visitLdcInsn("  |- Adding field ");
                        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "append", "(Ljava/lang/CharSequence;)Ljava/io/PrintStream;", false);
                        init.visitInsn(Opcodes.SWAP);
                        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
                    }

                    init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_FIELD_TABLE.getInternalName(), "addField", Type.getMethodDescriptor(Type.VOID_TYPE, KUIMI_FIELD), false);
                }

                init.visitInsn(Opcodes.POP); // pop field table
                // endregion

                init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_CLASS.getInternalName(), "getMethodTable", Type.getMethodDescriptor(KUIMI_METHOD_TABLE), false);

                for (var method : kclass.methods) {
                    init.visitInsn(Opcodes.DUP);

                    init.visitTypeInsn(Opcodes.NEW, KUIMI_METHOD.getInternalName());
                    init.visitInsn(Opcodes.DUP);

                    init.visitVarInsn(Opcodes.ALOAD, 2);// declared class
                    pushInt(init, method.access);
                    init.visitLdcInsn(method.name);
                    tool.resolveClass(init, Type.getReturnType(method.desc));
                    var params = Type.getArgumentTypes(method.desc);
                    if (params.length == 0) {
                        init.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/List", "of", "()Ljava/util/List;", true);
                    } else {
                        init.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
                        init.visitInsn(Opcodes.DUP);
                        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);

                        for (var param : params) {
                            init.visitInsn(Opcodes.DUP);
                            tool.resolveClass(init, param);
                            init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
                            init.visitInsn(Opcodes.POP);
                        }
                    }
                    init.visitMethodInsn(Opcodes.INVOKESPECIAL, KUIMI_METHOD.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, KUIMI_CLASS, Type.INT_TYPE, STRING, KUIMI_CLASS, Type.getType(List.class)), false);


                    init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_METHOD_TABLE.getInternalName(), "addMethod", Type.getMethodDescriptor(KUIMI_METHOD, KUIMI_METHOD), false);

                    if (debug) {
                        init.visitInsn(Opcodes.DUP);
                        init.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                        init.visitLdcInsn("  |- Adding method ");
                        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "append", "(Ljava/lang/CharSequence;)Ljava/io/PrintStream;", false);
                        init.visitInsn(Opcodes.SWAP);
                        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
                    }
                    if (method.instructions != null && method.instructions.size() > 0) {
                        if (debug) {
                            init.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                            init.visitLdcInsn("  |  `- Method code found");
                            init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
                        }

                        var wcpName = generateMethod(method, kclass);
                        var paramTypes = getParamsType(method);

                        init.visitLdcInsn(new Handle(
                                Opcodes.H_INVOKESTATIC, wcpName, "execute", Type.getMethodDescriptor(
                                paramTypes.remove(0), paramTypes.toArray(Type[]::new)
                        ), false));

                        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_METHOD.getInternalName(), "attachImplementation", Type.getMethodDescriptor(KUIMI_METHOD, Type.getType(MethodHandle.class)), false);
                    }
                    init.visitInsn(Opcodes.POP);
                }

                init.visitInsn(Opcodes.POP); // pop method table

            }

            init.visitInsn(Opcodes.RETURN);
            init.visitMaxs(0, 0);
        }


        output.put(outInternalName, classWriter.toByteArray());
    }

    public Map<String, byte[]> getOutput() {
        return output;
    }

    private String generateMethod(MethodNode method, ClassNode kclass) {
        var wcpName = kclass.name + "$met" + method.name.replace('<', '_').replace('>', '_');
        {
            var counter = 0;
            var baseName = wcpName + '$';
            while (output.containsKey(wcpName)) {
                wcpName = baseName + counter;
                counter++;
            }
        }

        try {
            var cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

            generateMethodImpl(method, cw, wcpName);

            output.put(wcpName, cw.toByteArray());

            return wcpName;
        } catch (Throwable throwable) {
            method.instructions.resetLabels();
            var cw = new ClassWriter(0);

            // TODO: output
            generateMethodImpl(method, new TraceClassVisitor(cw, new Textifier(), new PrintWriter(System.out, true)), wcpName);

            if (true) { // FIXME
                var shorten = wcpName.substring(wcpName.lastIndexOf('/') + 1);
                System.out.println(shorten);
                try {
                    Files.write(Path.of("B:/vmts", shorten + ".class"), cw.toByteArray());
                } catch (IOException e) {
                    throwable.addSuppressed(e);
                    throw throwable;
                }
            }

            output.put(wcpName, cw.toByteArray());

            throw throwable;
        }
    }


    private ArrayList<Type> getParamsType(MethodNode method) {
        var mtdesc = Type.getMethodType(method.desc);

        var params = new ArrayList<Type>();
        params.add(mtdesc.getReturnType());
        if (!Modifier.isStatic(method.access)) {
            params.add(Type.getObjectType("java/lang/Object"));
        }

        params.addAll(List.of(mtdesc.getArgumentTypes()));
        var kuimiObject = Type.getType(KuimiObject.class);

        params.replaceAll(type -> {
            var sort = type.getSort();
            if (sort == Type.ARRAY || sort == Type.OBJECT) return kuimiObject;
            return type;
        });
        // var rtType = params.remove(0);


        params.add(1, Type.getType(KuimiVM.class));
        params.add(2, Type.getType(StackTrace.class));

        return params;
    }

    private void generateMethodImpl(MethodNode method, ClassVisitor cw, String wcpName) {
        cw.visit(
                Opcodes.V11,
                Opcodes.ACC_PUBLIC,
                wcpName,
                null, "java/lang/Object", null
        );

        var params = getParamsType(method);
        var rtType = params.remove(0);

        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "initialized", "Z", null, null);

        var dataInit = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "init", Type.getMethodDescriptor(
                Type.VOID_TYPE, Type.getType(KuimiVM.class)
        ), null, null);

        var postDataInit = new MethodNode();


        {
            dataInit.visitFieldInsn(Opcodes.GETSTATIC, wcpName, "initialized", "Z");
            var endLabel = new Label();
            dataInit.visitJumpInsn(Opcodes.IFEQ, endLabel);
            dataInit.visitInsn(Opcodes.RETURN);
            dataInit.visitLabel(endLabel);
            dataInit.visitFrame(Opcodes.F_SAME, 1, null, 0, null);
        }

        method.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                var mv = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "execute", Type.getMethodDescriptor(
                        rtType, params.toArray(Type[]::new)
                ), signature, exceptions);


                var typeFieldMapping = new HashMap<String, String>() {
                    String map(String type) {
                        return map(Type.getObjectType(type));
                    }

                    String map(Type type) {
                        var rsp = get(type.getDescriptor());
                        if (rsp != null) return rsp;

                        var name = "t" + size();
                        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, name, KUIMI_CLASS.getDescriptor(), null, null);

                        dataInit.visitVarInsn(Opcodes.ALOAD, 0);
                        dataInit.visitLdcInsn(type.getDescriptor());
                        dataInit.visitMethodInsn(Opcodes.INVOKESTATIC, TYPE.getInternalName(), "getType", Type.getMethodDescriptor(TYPE, STRING), false);
                        dataInit.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_VM.getInternalName(), "resolveClass", Type.getMethodDescriptor(
                                KUIMI_CLASS, TYPE
                        ), false);
                        dataInit.visitFieldInsn(Opcodes.PUTSTATIC, wcpName, name, KUIMI_CLASS.getDescriptor());

                        put(type.getDescriptor(), name);
                        return name;
                    }

                    void loadType(MethodVisitor mv, String name) {
                        mv.visitFieldInsn(Opcodes.GETSTATIC, wcpName, map(name), KUIMI_CLASS.getDescriptor());
                    }

                    void loadType(MethodVisitor mv, Type type) {
                        mv.visitFieldInsn(Opcodes.GETSTATIC, wcpName, map(type), KUIMI_CLASS.getDescriptor());
                    }
                };
                var metMapping = new HashMap<String, String>() {
                    String map(boolean isStatic, String owner, String name, String descriptor) {
                        var key = isStatic + "-" + owner + "." + name + descriptor;
                        var rsp = get(key);
                        if (rsp != null) return rsp;

                        rsp = "m" + size();


                        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, rsp, KUIMI_METHOD.getDescriptor(), null, null);

                        typeFieldMapping.loadType(postDataInit, owner);
                        getMethodTable(postDataInit);

                        postDataInit.visitLdcInsn(name);

                        typeFieldMapping.loadType(postDataInit, Type.getReturnType(descriptor));

                        postDataInit.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
                        postDataInit.visitInsn(Opcodes.DUP);
                        postDataInit.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);

                        for (var param : Type.getArgumentTypes(descriptor)) {
                            postDataInit.visitInsn(Opcodes.DUP);
                            typeFieldMapping.loadType(postDataInit, param);
                            postDataInit.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
                            postDataInit.visitInsn(Opcodes.POP);
                        }

                        postDataInit.visitInsn(isStatic ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
                        // public KuimiMethod resolveMethod(String name, KuimiClass retType, List<KuimiClass> params, boolean isStatic)

                        postDataInit.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_METHOD_TABLE.getInternalName(), "resolveMethod", Type.getMethodDescriptor(
                                KUIMI_METHOD, STRING, KUIMI_CLASS, Type.getType(List.class), Type.BOOLEAN_TYPE
                        ), false);
                        postDataInit.visitFieldInsn(Opcodes.PUTSTATIC, wcpName, rsp, KUIMI_METHOD.getDescriptor());

                        put(key, rsp);
                        return rsp;
                    }

                    void getMethodTable(MethodVisitor mv) {
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_CLASS.getInternalName(), "getMethodTable", Type.getMethodDescriptor(KUIMI_METHOD_TABLE), false);
                    }
                };
                var objMap = new HashMap<String, String>() {
                    String ldcField(String value) {
                        var rsp = get(value);
                        if (rsp != null) return rsp;

                        rsp = "str" + size();
                        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, rsp, KUIMI_OBJECT.getDescriptor(), null, null);


                        postDataInit.visitTypeInsn(Opcodes.NEW, Type.getType(KuimiString.class).getInternalName());
                        postDataInit.visitInsn(Opcodes.DUP);
                        postDataInit.visitVarInsn(Opcodes.ALOAD, 0);
                        postDataInit.visitLdcInsn(value);
                        postDataInit.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getType(KuimiString.class).getInternalName(), "<init>", Type.getMethodDescriptor(
                                Type.VOID_TYPE, KUIMI_VM, STRING
                        ), false);

                        postDataInit.visitFieldInsn(Opcodes.PUTSTATIC, wcpName, rsp, KUIMI_OBJECT.getDescriptor());


                        put(value, rsp);
                        return rsp;
                    }
                };
                var fieldAccessMap = new HashMap<String, String>() {
                    String fieldField(String owner, String name, String type, boolean isStatic) {
                        var key = isStatic + "." + owner + "." + name + ":" + type;
                        var rsp = get(key);
                        if (rsp != null) return rsp;

                        rsp = "fd" + size();

                        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, rsp, KUIMI_FIELD.getDescriptor(), null, null);

                        typeFieldMapping.loadType(postDataInit, owner);
                        postDataInit.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_CLASS.getInternalName(), "getFieldTable", Type.getMethodDescriptor(KUIMI_FIELD_TABLE), false);
                        postDataInit.visitInsn(isStatic ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
                        postDataInit.visitLdcInsn(name);
                        typeFieldMapping.loadType(postDataInit, Type.getType(type));
                        postDataInit.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_FIELD_TABLE.getInternalName(), "findField", Type.getMethodDescriptor(
                                KUIMI_FIELD, Type.BOOLEAN_TYPE, Type.getType(String.class), KUIMI_CLASS
                        ), false);

                        postDataInit.visitFieldInsn(Opcodes.PUTSTATIC, wcpName, rsp, KUIMI_FIELD.getDescriptor());

                        put(key, rsp);
                        return rsp;
                    }
                };

                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, wcpName, "init", Type.getMethodDescriptor(
                        Type.VOID_TYPE, Type.getType(KuimiVM.class)
                ), false);


                return new MethodVisitor(api, mv) {

                    private void loadType(String name) {
                        typeFieldMapping.loadType(mv, name);
                    }

                    private void pushMethod(String fname) {
                        mv.visitFieldInsn(Opcodes.GETSTATIC, wcpName, fname, KUIMI_METHOD.getDescriptor());
                    }

                    @Override
                    public void visitVarInsn(int opcode, int varIndex) {
                        super.visitVarInsn(opcode, varIndex + 2);
                    }

                    @Override
                    public void visitIincInsn(int varIndex, int increment) {
                        super.visitIincInsn(varIndex + 2, increment);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String str) {
                            mv.visitFieldInsn(Opcodes.GETSTATIC, wcpName, objMap.ldcField(str), KUIMI_OBJECT.getDescriptor());
                            return;
                        }
                        super.visitLdcInsn(value);
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ARRAYLENGTH) {
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_OBJECT.getInternalName(), "getDelegateInstance", "()Ljava/lang/Object;", false);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/reflect/Array", "getLength", "(Ljava/lang/Object;)I", false);
                            return;
                        }

                        //@formatter:off
                        if (opcode == Opcodes.AALOAD) {mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "AALOAD", Type.getMethodDescriptor(KUIMI_OBJECT, KUIMI_OBJECT, Type.INT_TYPE), false);return;}
                        if (opcode == Opcodes.BALOAD) {mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "BALOAD", Type.getMethodDescriptor(Type.BYTE_TYPE, KUIMI_OBJECT, Type.INT_TYPE), false);return;}
                        if (opcode == Opcodes.CALOAD) {mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "CALOAD", Type.getMethodDescriptor(Type.CHAR_TYPE, KUIMI_OBJECT, Type.INT_TYPE), false);return;}
                        if (opcode == Opcodes.SALOAD) {mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "SALOAD", Type.getMethodDescriptor(Type.SHORT_TYPE, KUIMI_OBJECT, Type.INT_TYPE), false);return;}
                        if (opcode == Opcodes.IALOAD) {mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "IALOAD", Type.getMethodDescriptor(Type.INT_TYPE, KUIMI_OBJECT, Type.INT_TYPE), false);return;}
                        if (opcode == Opcodes.LALOAD) {mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "LALOAD", Type.getMethodDescriptor(Type.LONG_TYPE, KUIMI_OBJECT, Type.INT_TYPE), false);return;}
                        if (opcode == Opcodes.FALOAD) {mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "FALOAD", Type.getMethodDescriptor(Type.FLOAT_TYPE, KUIMI_OBJECT, Type.INT_TYPE), false);return;}
                        if (opcode == Opcodes.DALOAD) {mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "DALOAD", Type.getMethodDescriptor(Type.DOUBLE_TYPE, KUIMI_OBJECT, Type.INT_TYPE), false);return;}

                        if (opcode == Opcodes.AASTORE){mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "AASTORE", Type.getMethodDescriptor(Type.VOID_TYPE, KUIMI_OBJECT, Type.INT_TYPE, KUIMI_OBJECT), false);return;}
                        if (opcode == Opcodes.BASTORE){mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "BASTORE", Type.getMethodDescriptor(Type.VOID_TYPE, KUIMI_OBJECT, Type.INT_TYPE, Type.BYTE_TYPE), false);return;}
                        if (opcode == Opcodes.CASTORE){mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "CASTORE", Type.getMethodDescriptor(Type.VOID_TYPE, KUIMI_OBJECT, Type.INT_TYPE, Type.CHAR_TYPE), false);return;}
                        if (opcode == Opcodes.SASTORE){mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "SASTORE", Type.getMethodDescriptor(Type.VOID_TYPE, KUIMI_OBJECT, Type.INT_TYPE, Type.SHORT_TYPE), false);return;}
                        if (opcode == Opcodes.IASTORE){mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "IASTORE", Type.getMethodDescriptor(Type.VOID_TYPE, KUIMI_OBJECT, Type.INT_TYPE, Type.INT_TYPE), false);return;}
                        if (opcode == Opcodes.LASTORE){mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "LASTORE", Type.getMethodDescriptor(Type.VOID_TYPE, KUIMI_OBJECT, Type.INT_TYPE, Type.LONG_TYPE), false);return;}
                        if (opcode == Opcodes.FASTORE){mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "FASTORE", Type.getMethodDescriptor(Type.VOID_TYPE, KUIMI_OBJECT, Type.INT_TYPE, Type.FLOAT_TYPE), false);return;}
                        if (opcode == Opcodes.DASTORE){mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "FASTORE", Type.getMethodDescriptor(Type.VOID_TYPE, KUIMI_OBJECT, Type.INT_TYPE, Type.DOUBLE_TYPE), false);return;}
                        //@formatter:on

                        super.visitInsn(opcode);
                    }

                    private void newArray(Type type) {
                        typeFieldMapping.loadType(mv, type);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAY_ACCESS_BRIDGE.getInternalName(), "newArray", Type.getMethodDescriptor(KUIMI_OBJECT, Type.INT_TYPE, KUIMI_CLASS), false);
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        if (opcode == Opcodes.NEWARRAY) {
                            switch (operand) {
                                case Opcodes.T_BOOLEAN -> newArray(Type.BOOLEAN_TYPE);
                                case Opcodes.T_BYTE -> newArray(Type.BYTE_TYPE);
                                case Opcodes.T_CHAR -> newArray(Type.CHAR_TYPE);
                                case Opcodes.T_SHORT -> newArray(Type.SHORT_TYPE);
                                case Opcodes.T_INT -> newArray(Type.INT_TYPE);
                                case Opcodes.T_LONG -> newArray(Type.LONG_TYPE);
                                case Opcodes.T_FLOAT -> newArray(Type.FLOAT_TYPE);
                                case Opcodes.T_DOUBLE -> newArray(Type.DOUBLE_TYPE);
                            }
                            return;
                        }
                        super.visitIntInsn(opcode, operand);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        var isObj = descriptor.length() != 1;
                        fieldAccessMap.fieldField(owner, name, descriptor, opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC);

                        var dstType = Type.getType(descriptor);
                        var accessBridge = FIELD_ACCESS_BRIDGE;


                        if (opcode == Opcodes.GETSTATIC) { // TODO
                            typeFieldMapping.loadType(mv, owner);
                        }
                        if (opcode == Opcodes.GETSTATIC || opcode == Opcodes.GETFIELD) {
                            mv.visitFieldInsn(Opcodes.GETSTATIC, wcpName, fieldAccessMap.fieldField(
                                    owner, name, descriptor, opcode == Opcodes.GETSTATIC
                            ), KUIMI_FIELD.getDescriptor());

                            if (opcode == Opcodes.GETSTATIC) { // get object field don't need call init because class was initialized when object allocating
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, CLASS_INIT_ENSURE.getInternalName(), "ensureInit", Type.getMethodDescriptor(KUIMI_FIELD, KUIMI_FIELD), false);
                            }

                            if (isObj) {
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, accessBridge.getInternalName(), "getobject", Type.getMethodDescriptor(
                                        KUIMI_OBJECT, KUIMI_OBJECT, KUIMI_FIELD
                                ), false);
                            } else {
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, accessBridge.getInternalName(), "get" + dstType.getClassName(), Type.getMethodDescriptor(
                                        dstType, KUIMI_OBJECT, KUIMI_FIELD
                                ), false);
                            }
                        }

                        if (opcode == Opcodes.PUTSTATIC) {
                            mv.visitFieldInsn(Opcodes.GETSTATIC, wcpName, fieldAccessMap.fieldField(
                                    owner, name, descriptor, true
                            ), KUIMI_FIELD.getDescriptor());
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, CLASS_INIT_ENSURE.getInternalName(), "ensureInit", Type.getMethodDescriptor(KUIMI_FIELD, KUIMI_FIELD), false);

                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, accessBridge.getInternalName(), "put", Type.getMethodDescriptor(
                                    Type.VOID_TYPE, isObj ? KUIMI_OBJECT : dstType, KUIMI_FIELD
                            ), false);
                        }
                        if (opcode == Opcodes.PUTFIELD) { // get object field don't need call init because class was initialized when object allocating
                            mv.visitFieldInsn(Opcodes.GETSTATIC, wcpName, fieldAccessMap.fieldField(
                                    owner, name, descriptor, false
                            ), KUIMI_FIELD.getDescriptor());
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, accessBridge.getInternalName(), "put", Type.getMethodDescriptor(
                                    Type.VOID_TYPE, KUIMI_OBJECT, isObj ? KUIMI_OBJECT : dstType, KUIMI_FIELD
                            ), false);
                        }
                        // super.visitFieldInsn(opcode, owner, name, descriptor);
                    }

                    @Override
                    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
                        if ("this".equals(name)) {
                            return;
                        }
                        super.visitLocalVariable(name, descriptor, signature, start, end, index + 2);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        var fname = metMapping.map(opcode == Opcodes.INVOKESTATIC, owner, name, descriptor);
                        List<Type> args = new ArrayList<>(List.of(Type.getArgumentTypes(descriptor)));
                        args.add(0, Type.getReturnType(descriptor));
                        args.replaceAll(it -> {
                            if (it.getSort() == Type.ARRAY || it.getSort() == Type.OBJECT) {
                                return KUIMI_OBJECT;
                            }
                            return it;
                        });


                        if (opcode != Opcodes.INVOKESTATIC) {
                            // reversedInvoke
                            args.add(1, KUIMI_OBJECT);
                        }

                        pushMethod(fname);
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitVarInsn(Opcodes.ALOAD, 1);

                        args.add(KUIMI_METHOD);
                        args.add(KUIMI_VM);
                        args.add(STACK_TRACE);

                        var retType = args.remove(0);
                        var mDesc = Type.getMethodDescriptor(retType, args.toArray(Type[]::new));

                        var specialCall = opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.INVOKESPECIAL;
                        mv.visitMethodInsn(
                                Opcodes.INVOKESTATIC, wcpName,
                                specialCall ? "specialCall" : "call",
                                mDesc, false
                        );
                        genWptCall(mDesc, retType, args, specialCall);
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW) {
                            loadType(type);
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_CLASS.getInternalName(), "allocateNewObject", Type.getMethodDescriptor(KUIMI_OBJECT), false);
                            return;
                        }
                        if (opcode == Opcodes.CHECKCAST) {
                            // TODO
                            loadType(type);
                            mv.visitInsn(Opcodes.POP);
                            return;
                        }
                        if (opcode == Opcodes.ANEWARRAY) {
                            newArray(Type.getObjectType(type));
                            return;
                        }
                        super.visitTypeInsn(opcode, type);
                    }

                    private final HashSet<String> calls = new HashSet<>();

                    void genWptCall(String desc, Type ret, List<Type> params, boolean specialCall) {
                        if (!calls.add(specialCall + "." + desc)) return;

                        var mtc = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, specialCall ? "specialCall" : "call", desc, null, null);
                        int metSlot;

                        var subl = params.subList(0, params.size() - 3);
                        {
                            var ix = 0;
                            for (var p : subl) {
                                ix += p.getSize();
                            }
                            metSlot = ix;
                        }

                        if (specialCall) {
                            mtc.visitVarInsn(Opcodes.ALOAD, metSlot);
                        } else {
                            var mtTable = KUIMI_METHOD_TABLE;

                            mtc.visitVarInsn(Opcodes.ALOAD, 0); // this
                            mtc.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_OBJECT.getInternalName(), "getObjectClass", Type.getMethodDescriptor(KUIMI_CLASS), false);
                            mtc.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_CLASS.getInternalName(), "getMethodTable", Type.getMethodDescriptor(mtTable), false);
                            mtc.visitMethodInsn(Opcodes.INVOKEVIRTUAL, mtTable.getInternalName(), "getMergedMethods", "()Ljava/util/List;", false);

                            mtc.visitVarInsn(Opcodes.ALOAD, metSlot);
                            mtc.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_METHOD.getInternalName(), "getMethodSlot", "()I", false);

                            mtc.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
                            mtc.visitTypeInsn(Opcodes.CHECKCAST, KUIMI_METHOD.getInternalName());
                        }

                        mtc.visitMethodInsn(Opcodes.INVOKESTATIC, CLASS_INIT_ENSURE.getInternalName(), "ensureInit", Type.getMethodDescriptor(KUIMI_METHOD, KUIMI_METHOD), false);

                        mtc.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KUIMI_METHOD.getInternalName(), "resolveMethodHandle", "()Ljava/lang/invoke/MethodHandle;", false);


                        mtc.visitVarInsn(Opcodes.ALOAD, metSlot + 1);
                        mtc.visitVarInsn(Opcodes.ALOAD, metSlot + 2);
                        var i = 0;
                        for (var p : subl) {
                            mtc.visitVarInsn(p.getOpcode(Opcodes.ILOAD), i);
                            i += p.getSize();
                        }

                        var mhCallArgs = new ArrayList<>(subl);
                        mhCallArgs.add(0, KUIMI_VM);
                        mhCallArgs.add(1, STACK_TRACE);

                        mtc.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeExact",
                                Type.getMethodDescriptor(ret, mhCallArgs.toArray(Type[]::new)),
                                false
                        );
                        mtc.visitInsn(ret.getOpcode(Opcodes.IRETURN));

                        mtc.visitMaxs(i + 4, i + 4);
                        mtc.visitEnd();
                    }
                };
            }
        });

        {
            for (var insn : postDataInit.instructions) {
                insn.accept(dataInit);
            }

            dataInit.visitInsn(Opcodes.ICONST_1);
            dataInit.visitFieldInsn(Opcodes.PUTSTATIC, wcpName, "initialized", "Z");
            dataInit.visitInsn(Opcodes.RETURN);
            dataInit.visitMaxs(6, 4);
            dataInit.visitEnd();
        }

        cw.visitEnd();

    }

}
