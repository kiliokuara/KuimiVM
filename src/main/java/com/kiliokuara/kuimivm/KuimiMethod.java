package com.kiliokuara.kuimivm;

import com.kiliokuara.kuimivm.execute.StackTrace;
import com.kiliokuara.kuimivm.vaarg.KuimiMethodInvoker;
import com.kiliokuara.kuimivm.vaarg.ShortenType;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class KuimiMethod extends KuimiMember {
    private final KuimiClass declaredClass;
    private final int modifiers;
    private final String methodName;
    private final KuimiClass returnType;
    private final List<KuimiClass> params;
    private final List<ShortenType> paramsShorten;
    private final List<ShortenType> fullCallShorten;

    int methodSlot = -1;


    public KuimiMethod(
            KuimiClass declaredClass,
            int modifiers,
            String methodName,
            KuimiClass returnType,
            List<KuimiClass> params
    ) {
        this.declaredClass = declaredClass;
        this.modifiers = modifiers;
        this.methodName = methodName;
        this.returnType = returnType;
        this.params = params == null ? List.of() : params;

        paramsShorten = this.params.stream().map(it -> ShortenType.from(it.getClassType())).toList();
        if (Modifier.isStatic(modifiers)) {
            fullCallShorten = paramsShorten;
        } else {
            fullCallShorten = Stream.concat(Stream.of(ShortenType.REF), paramsShorten.stream()).toList();
        }
    }


    public KuimiClass getDeclaredClass() {
        return declaredClass;
    }

    public KuimiClass getAttachedClass() {
        return declaredClass;
    }

    public int getModifiers() {
        return modifiers;
    }

    public KuimiClass getReturnType() {
        return returnType;
    }

    public List<KuimiClass> getParameters() {
        return params;
    }

    public List<ShortenType> getParamsShorten() {
        return paramsShorten;
    }

    public String getMethodName() {
        return methodName;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sharedAppendModifiers(modifiers, sb);
        sb.append(declaredClass.getTypeName()).append('.');
        sb.append(methodName);
        sb.append(params.stream()
                .map(KuimiClass::getTypeName)
                .collect(Collectors.joining(", ", "(", ")"))
        );
        sb.append(": ").append(returnType.getTypeName());

        return sb.toString();
    }

    static void sharedAppendModifiers(int modifiers, StringBuilder sb) {
        if (Modifier.isPublic(modifiers)) {
            sb.append("public ");
        }
        if (Modifier.isPrivate(modifiers)) {
            sb.append("private ");
        }
        if (Modifier.isProtected(modifiers)) {
            sb.append("protected ");
        }
        if (Modifier.isStatic(modifiers)) {
            sb.append("static ");
        }
        if (Modifier.isNative(modifiers)) {
            sb.append("native ");
        }
        if (Modifier.isSynchronized(modifiers)) {
            sb.append("synchronized ");
        }
        if (Modifier.isAbstract(modifiers)) {
            sb.append("abstract ");
        }
        if (Modifier.isVolatile(modifiers)) {
            sb.append("volatile ");
        }
        if (Modifier.isFinal(modifiers)) {
            sb.append("final ");
        }
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;
        if (!(obj instanceof KuimiMethod other)) return false;
        if (methodSlot != -1 && other.methodSlot != -1) {
            if (methodSlot != other.methodSlot) return false;
        }
        if (modifiers != other.modifiers) return false;
        if (!declaredClass.equals(other.declaredClass)) return false;
        if (!returnType.equals(other.returnType)) return false;
        if (!methodName.equals(other.methodName)) return false;
        if (!params.equals(other.params)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return methodName.hashCode() & (modifiers ^ returnType.hashCode()) + params.hashCode() ^ declaredClass.hashCode();
    }

    public int getMethodSlot() {
        if (methodSlot < 0) throw new IllegalStateException("Method slot not allocated");
        return methodSlot;
    }

    // region Execute
    private transient volatile MethodHandle targetMH;
    private transient volatile MethodType targetMHType;
    private transient volatile KuimiMethodInvoker invoker;

    public MethodHandle resolveMethodHandle() {
        if (targetMH != null) return targetMH;

        var mhType = flattenMethodHandleType();
        if (getClass() != KuimiMethod.class) {
            var lk = MethodHandles.lookup();
            try {
                lk = MethodHandles.privateLookupIn(getClass(), lk);
            } catch (Exception ignored) {
            }

            try {
                return targetMH = Internal.stackEnter(this, lk.findVirtual(getClass(), "execute", mhType).bindTo(this));
            } catch (Exception e) {
                return targetMH = Internal.notImplemented(this, mhType, e);
            }
        }

        return targetMH = Internal.notImplemented(this, mhType, null);
    }

    public final KuimiMethod attachImplementation(KuimiMethod method) {
        Objects.requireNonNull(method, "method");
        if (getClass() != KuimiMethod.class) {
            throw new IllegalStateException("Cannot attach a method handle to " + getClass());
        }
        if (!method.flattenMethodHandleType().equals(flattenMethodHandleType())) {
            throw new IllegalArgumentException("Method type not match: " + method.flattenMethodHandleType() + " -> " + flattenMethodHandleType());
        }
        if (targetMH != null) {
            throw new IllegalStateException("This method " + this + " is already attached.");
        }
        targetMH = method.resolveMethodHandle();
        return this;
    }

    public final KuimiMethod attachImplementation(MethodHandle methodHandle) {
        Objects.requireNonNull(methodHandle, "methodHandle");
        if (getClass() != KuimiMethod.class) {
            throw new IllegalStateException("Cannot attach a method handle to " + getClass());
        }
        if (!methodHandle.type().equals(flattenMethodHandleType())) {
            throw new IllegalArgumentException("Method type not match: " + methodHandle.type() + " -> " + flattenMethodHandleType());
        }
        if (targetMH != null) {
            throw new IllegalStateException("This method " + this + " is already attached.");
        }
        targetMH = Internal.stackEnter(this, methodHandle);
        return this;
    }

    public final MethodType flattenMethodHandleType() {
        if (targetMHType != null) {
            return targetMHType;
        }

        var retType = switch (returnType.getClassType().getSort()) {
            case Type.BYTE -> byte.class;
            case Type.CHAR -> char.class;
            case Type.SHORT -> short.class;
            case Type.INT -> int.class;
            case Type.LONG -> long.class;
            case Type.FLOAT -> float.class;
            case Type.DOUBLE -> double.class;
            case Type.BOOLEAN -> boolean.class;
            case Type.VOID -> void.class;
            case Type.ARRAY, Type.OBJECT -> KuimiObject.class;
            default -> throw new RuntimeException("Cannot confirm return type");
        };

        var args = new ArrayList<Class<?>>();
        args.add(KuimiVM.class);
        args.add(StackTrace.class);

        if ((modifiers & Opcodes.ACC_STATIC) == 0) {
            args.add(KuimiObject.class); // the this object
        }

        for (var param : params) {
            args.add(switch (param.getClassType().getSort()) {
                case Type.BYTE -> byte.class;
                case Type.CHAR -> char.class;
                case Type.SHORT -> short.class;
                case Type.INT -> int.class;
                case Type.LONG -> long.class;
                case Type.FLOAT -> float.class;
                case Type.DOUBLE -> double.class;
                case Type.BOOLEAN -> boolean.class;
                case Type.ARRAY, Type.OBJECT -> KuimiObject.class;
                default -> throw new RuntimeException("Cannot confirm param type: " + param);
            });
        }

        return targetMHType = MethodType.methodType(retType, args);
    }

    public KuimiMethodInvoker getInvoker() {
        if (invoker != null) return invoker;

        if (Modifier.isStatic(modifiers)) {
            return invoker = KuimiMethodInvoker.invoker(returnType, params);
        } else {
            var prm = new ArrayList<KuimiClass>();
            prm.add(declaredClass.getVm().getBaseClass());
            prm.addAll(params);
            return invoker = KuimiMethodInvoker.invoker(returnType, prm);
        }
    }


    public boolean isInterfaceMethod() {
        return declaredClass.isInterface();
    }

    public boolean isMergedInterface() {
        return false;
    }

    public List<ShortenType> getFullCallShorten() {
        return fullCallShorten;
    }
    // endregion


    static class Internal {
        private static final MethodHandle NEW_ERROR;
        private static final MethodHandle NEW_ERROR_WITH_REASON;


        private static final MethodHandle ENTER_STACK;
        private static final MethodHandle LEAVE_STACK;


        // out: (KuimiMethod, Throwable, <V>, KVM, StackTrace)<V>
        private static final ClassValue<MethodHandle> RTYPE_ID_CLEANUP_CACHE = new ClassValue<>() {
            @Override
            protected MethodHandle computeValue(Class<?> rtype) {
                if (rtype == void.class) {
                    return LEAVE_STACK;
                } else {
                    MethodHandle cleanup = MethodHandles.identity(rtype);

                    cleanup = MethodHandles.collectArguments(cleanup, 1, LEAVE_STACK);
                    // now: (V, KuimiMethod, Throwable, KVM, StackTrace)V

                    // target: (KuimiMethod, Throwable, V, KVM, StackTrace)V
                    return MethodHandles.permuteArguments(cleanup,
                            MethodType.methodType(rtype, KuimiMethod.class, Throwable.class, rtype, KuimiVM.class, StackTrace.class),
                            2, 0, 1, 3, 4
                    );
                }
            }
        };

        static {
            var lk = MethodHandles.lookup();
            try {
                NEW_ERROR = lk.findConstructor(InternalError.class, MethodType.methodType(void.class, String.class));
                NEW_ERROR_WITH_REASON = lk.findConstructor(InternalError.class, MethodType.methodType(void.class, String.class, Throwable.class));

                ENTER_STACK = lk.findStatic(Internal.class, "enterStack", MethodType.methodType(void.class, KuimiMethod.class, KuimiVM.class, StackTrace.class));
                LEAVE_STACK = lk.findStatic(Internal.class, "leaveStack", MethodType.methodType(void.class, KuimiMethod.class, Throwable.class, KuimiVM.class, StackTrace.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private static void enterStack(KuimiMethod method, KuimiVM vm, StackTrace stackTrace) {
            // new Throwable("enter stack: " + method + " - " + stackTrace).printStackTrace(System.out);
            if (stackTrace != null) {
                stackTrace.enter(method);
                stackTrace.pushFrame();
            }
        }

        private static void leaveStack(KuimiMethod method, Throwable throwable, KuimiVM vm, StackTrace stackTrace) {
            // new Throwable("leave stack: " + method + " - " + stackTrace).printStackTrace(System.out);
            if (stackTrace != null) {
                stackTrace.leave(method);
            }
        }

        public static MethodHandle notImplemented(KuimiMethod kuimiMethod, MethodType mhType, Throwable exception) {
            var msg = "Exception when binding implementation to " + kuimiMethod;
            MethodHandle emh;
            if (exception == null) {
                emh = NEW_ERROR.bindTo(msg);
            } else {
                emh = MethodHandles.insertArguments(NEW_ERROR_WITH_REASON, 0, msg, exception);
            }

            var terr = MethodHandles.throwException(mhType.returnType(), InternalError.class);
            var empThrow = MethodHandles.collectArguments(terr, 0, emh);


            return MethodHandles.dropArguments(empThrow, 0, mhType.parameterArray());
        }

        public static MethodHandle stackEnter(KuimiMethod kuimiMethod, MethodHandle execute) {
            return MethodHandles.tryFinally(
                    MethodHandles.foldArguments(execute, ENTER_STACK.bindTo(kuimiMethod)),
                    RTYPE_ID_CLEANUP_CACHE.get(execute.type().returnType()).bindTo(kuimiMethod)
            );
        }

        static class DelegatedMethod extends KuimiMethod {

            final KuimiMethod original;
            private final KuimiClass attachedClass;

            public DelegatedMethod(KuimiMethod original, KuimiClass attachedClass) {
                super(original.declaredClass, original.modifiers, original.methodName, original.returnType, original.params);
                this.original = original;
                this.attachedClass = attachedClass;
            }

            @Override
            public MethodHandle resolveMethodHandle() {
                return original.resolveMethodHandle();
            }

            @Override
            public KuimiClass getAttachedClass() {
                return attachedClass;
            }

            @Override
            public KuimiMethodInvoker getInvoker() {
                return original.getInvoker();
            }

            @Override
            public boolean isMergedInterface() {
                return true;
            }
        }
    }
}
