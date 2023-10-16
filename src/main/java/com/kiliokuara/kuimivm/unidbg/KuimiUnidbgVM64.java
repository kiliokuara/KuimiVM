package com.kiliokuara.kuimivm.unidbg;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.arm.Arm64Svc;
import com.github.unidbg.arm.backend.BackendException;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.pointer.UnidbgPointer;
import com.kiliokuara.kuimivm.execute.StackTrace;
import com.kiliokuara.kuimivm.vaarg.VaArgModelBuilder;
import com.kiliokuara.kuimivm.KuimiClass;
import com.kiliokuara.kuimivm.KuimiObject;
import com.kiliokuara.kuimivm.KuimiVM;
import com.kiliokuara.kuimivm.execute.ObjectPool;
import com.kiliokuara.kuimivm.objects.KuimiArrays;
import com.kiliokuara.kuimivm.objects.KuimiString;
import com.sun.jna.Pointer;
import org.objectweb.asm.Type;
import unicorn.Arm64Const;

import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@SuppressWarnings({"unused", "UnnecessaryLocalVariable"})
public class KuimiUnidbgVM64 extends KuimiUnidbgVM implements VMConst {


    private final UnidbgPointer _JavaVM;
    private final UnidbgPointer _JNIEnv;
    private final UnidbgPointer _JNIImpl;

    public KuimiUnidbgVM64(KuimiVM vm, AndroidEmulator emulator) {
        super(vm, emulator);


        var svcMemory = emulator.getSvcMemory();
        var _JavaVM = svcMemory.allocate(emulator.getPointerSize(), "_JavaVM");


        Pointer _GetVersion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                return JNI_VERSION_1_8;
            }
        });

        Pointer _DefineClass = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _FindClass = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                Pointer env = context.getPointerArg(0);
                Pointer className = context.getPointerArg(1);
                String name = className.getString(0);

                if (verbose) {
                    System.out.format("JNIEnv->FindClass(%s)", name);
                }

                var st = vm.getStackTrace();
                var kc = resolveClass(st, name);
                if (verbose) {
                    System.out.append(" --> ").println(kc);
                }
                if (kc == null) {
                    throwException(new NoClassDefFoundError(name));
                    return 0;
                }

                // TODO: FIND CLASS
                return st.pushObject(kc);
            }
        });

        Pointer _FromReflectedMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _FromReflectedField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _ToReflectedMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);

                throw new UnsupportedOperationException();
            }
        });

        Pointer _GetSuperclass = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);

                var obj = vm.resolveObject(clazz.toIntPeer());
                if (obj == null) {
                    throwException(new NullPointerException());
                    return 0;
                }

                var stack = vm.getStackTrace();
                return stack.pushObject(((KuimiClass) obj).getSuperClass());
            }
        });

        Pointer _IsAssignableFrom = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                // TODO IsAssignableFrom
                throw new UnsupportedOperationException();
            }
        });

        Pointer _ToReflectedField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _Throw = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);

                var dvmObject = vm.resolveObject(object.toIntPeer());
                if (verbose) {
                    System.out.format("JNIEnv->Throw(%s)\n", dvmObject);
                }

                throwException(dvmObject);
                return 0;
            }
        });

        Pointer _ThrowNew = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException(); // TODO
            }
        });

        Pointer _ExceptionOccurred = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                var st = vm.getStackTrace();
                return st.pushObject(st.throwable);
            }
        });

        Pointer _ExceptionDescribe = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                if (verbose) {
                    System.out.println("JNIEnv->ExceptionDescribe()");
                }
                var st = vm.getStackTrace();
                if (st.throwable != null) {
                    System.out.println("JNIEnv->ExceptionDescribe(): " + st.throwable);
                }
                return 0;
            }
        });

        Pointer _ExceptionClear = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                if (verbose) {
                    System.out.println("ExceptionClear()");
                }
                vm.getStackTrace().throwable = null;
                return 0;
            }
        });

        Pointer _FatalError = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException("__Fatal Error called");
            }
        });

        Pointer _PushLocalFrame = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                int capacity = context.getIntArg(1);

                var st = vm.getStackTrace();
                st.pushFrame();

                if (verbose) {
                    System.out.println("JNIEnv->PushLocalFrame()");
                }

                return JNI_OK;
            }
        });

        Pointer _PopLocalFrame = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer jresult = context.getPointerArg(1);

                var st = vm.getStackTrace();
                var oobj = vm.resolveObject(jresult.toIntPeer());
                st.popFrame();

                if (verbose) {
                    System.out.println("JNIEnv->PopLocalFrame()");
                }

                return st.pushObject(oobj);
            }
        });

        Pointer _NewGlobalRef = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                if (object == null) {
                    return 0;
                }

                var obj = vm.resolveObject(object.toIntPeer());
                if (obj == null) return 0;

                if (verbose) {
                    System.out.format("JNIEnv->NewGlobalRef(%s)\n", obj);
                }

                return ObjectPool.GLOBAL_OBJECT_PREFIX | vm.getGlobalPool().addObject(obj);

            }
        });

        Pointer _DeleteGlobalRef = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                var ptr = object.toIntPeer();
                if ((ptr & ObjectPool.OBJECT_PREFIX) == ObjectPool.GLOBAL_OBJECT_PREFIX) {

                    if (verbose) {
                        System.out.format("JNIEnv->DeleteGlobalRef(%s)\n", vm.resolveObject(ptr));
                    }

                    vm.getGlobalPool().removeObject(ptr & ~ObjectPool.OBJECT_PREFIX);

                    return JNI_OK;
                }

                return JNI_ERR;
            }
        });

        Pointer _DeleteLocalRef = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);

                if (object == null) return JNI_OK;

                var ptr = object.toIntPeer();
                if ((ptr & ObjectPool.OBJECT_PREFIX) == ObjectPool.LOCAL_OBJECT_PREFIX) {
                    var st = vm.getStackTrace();
                    var obj = st.deleteObject(ptr);

                    if (verbose) {
                        System.out.format("JNIEnv->DeleteLocalRef(%s, %s, %s)\n", obj, ptr, Integer.toBinaryString(ptr));
                    }

                    return JNI_OK;
                }

                return JNI_ERR;
            }
        });

        Pointer _IsSameObject = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer ref1 = context.getPointerArg(1);
                UnidbgPointer ref2 = context.getPointerArg(2);

                if (ref1 == ref2) return JNI_TRUE;
                if (ref1.equals(ref2)) return JNI_TRUE;

                var obj1 = vm.resolveObject(ref1.toIntPeer());
                var obj2 = vm.resolveObject(ref2.toIntPeer());

                return obj1 == obj2 ? JNI_TRUE : JNI_FALSE;
            }
        });

        Pointer _NewLocalRef = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                if (object == null) {
                    return 0;
                }
                var obj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();

                var rsp = st.pushObject(obj);
                if (verbose) {
                    System.out.format("JNIEnv->NewLocalRef(%s -> %s, %s)\n", obj, rsp, Integer.toBinaryString(rsp));
                }
                return rsp;
            }
        });

        Pointer _EnsureLocalCapacity = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                int capacity = context.getIntArg(1);

                return 0;
            }
        });

        Pointer _AllocObject = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);

                var vmc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());

                if (verbose) {
                    System.out.format("JNIEnv->AllocObject(%s)\n", vmc);
                }

                return vm.getStackTrace().pushObject(vmc.allocateNewObject());
            }
        });

        Pointer _NewObject = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);

                var vmc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var init = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                if (verbose) {
                    System.out.format("JNIEnv->NewObject(%s): %s\n", vmc, init);
                }

                var newObject = vmc.allocateNewObject();
                try {
                    init.resolveMethodHandle().invokeExact(vm, st, newObject);
                } catch (Throwable e) {
                    throw new BackendException(e);
                }

                return st.pushObject(newObject);
            }
        });

        Pointer _NewObjectV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer va_list = context.getPointerArg(3);


                var vmc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var init = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                if (verbose) {
                    System.out.format("JNIEnv->NewObjectV(%s): %s\n", vmc, init);
                }

                var newObject = vmc.allocateNewObject();

                var vaarg = VaArgModelBuilder.builder(init.getFullCallShorten()).putInt(st.pushObject(newObject));
                VaList64Visitor.read(emulator, va_list, vaarg, init.getParamsShorten());
                init.getInvoker().voidInvoke(vm, st, init, vaarg.build());

                return st.pushObject(newObject);
            }
        });

        Pointer _NewObjectA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer jvalue = context.getPointerArg(3);

                var vmc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var init = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                if (verbose) {
                    System.out.format("JNIEnv->NewObjectA(%s): %s\n", vmc, init);
                }

                var newObject = vmc.allocateNewObject();

                var vaarg = VaArgModelBuilder.builder(init.getFullCallShorten()).putInt(st.pushObject(newObject));
                JValueListVisitor.read(emulator, jvalue, vaarg, init.getParameters());
                init.getInvoker().voidInvoke(vm, st, init, vaarg.build());

                return st.pushObject(newObject);
            }
        });

        Pointer _GetObjectClass = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);

                var obj = vm.resolveObject(object.toIntPeer());
                if (obj == null) return 0;

                if (verbose) {
                    System.out.format("JNIEnv->GetObjectClass(%s) -> %s%n", obj, obj.getObjectClass());
                }

                var st = vm.getStackTrace();
                return st.pushObject(obj.getObjectClass());
            }
        });

        Pointer _IsInstanceOf = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer clazz = context.getPointerArg(2);

                throw new UnsupportedOperationException("JNIEnv->IsInstanceOf"); // TODO
            }
        });

        Pointer _GetMethodID = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                Pointer methodName = context.getPointerArg(2);
                Pointer argsPointer = context.getPointerArg(3);
                String name = methodName.getString(0);
                String args = argsPointer.getString(0);


                var st = vm.getStackTrace();
                var targetC = (KuimiClass) vm.resolveObject(clazz.toIntPeer());

                if (verbose) {
                    System.out.format("JNIEnv->GetMethodID(%s.%s%s)", targetC, name, args);
                }

                var mttype = Type.getMethodType(args);
                var rtType = resolveClass(st, mttype.getReturnType());
                var pttype = Arrays.stream(mttype.getArgumentTypes()).map(it -> resolveClass(st, it)).toList();

                var mtt = targetC.getMethodTable().resolveMethod(name, rtType, pttype, false);
                var rsp = jniMemberTable.push(mtt);
                if (verbose) {
                    System.out.append("  ->  ").append(String.valueOf(mtt)).append(" <").append(String.valueOf(rsp)).println(">");
                }
                return rsp;
            }
        });

        Pointer _CallObjectMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);

                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                ArmVarArg64Visitor.read(emulator, vaarg, met.getParamsShorten());
                var rpx = met.getInvoker().anyInvoke(vm, st, met, vaarg.build());

                return st.pushObject((KuimiObject<?>) rpx);
            }
        });

        Pointer _CallObjectMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer va_list = context.getPointerArg(3);


                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                VaList64Visitor.read(emulator, va_list, vaarg, met.getParamsShorten());
                Object rpx=null;
                try {
                    rpx = met.getInvoker().anyInvoke(vm, st, met, vaarg.build());
                }catch (Exception e){
                    throwException(e);
                }

                return st.pushObject((KuimiObject<?>) rpx);
            }
        });

        Pointer _CallObjectMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer jvalue = context.getPointerArg(3);

                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                JValueListVisitor.read(emulator, jvalue, vaarg, met.getParameters());
                var rpx = met.getInvoker().anyInvoke(vm, st, met, vaarg.build());

                return st.pushObject((KuimiObject<?>) rpx);
            }
        });

        Pointer _CallBooleanMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);


                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                ArmVarArg64Visitor.read(emulator, vaarg, met.getParamsShorten());
                return met.getInvoker().intInvoke(vm, st, met, vaarg.build());
            }
        });

        Pointer _CallBooleanMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer va_list = context.getPointerArg(3);

                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                VaList64Visitor.read(emulator, va_list, vaarg, met.getParamsShorten());
                return met.getInvoker().intInvoke(vm, st, met, vaarg.build());
            }
        });

        Pointer _CallBooleanMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer jvalue = context.getPointerArg(3);


                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                JValueListVisitor.read(emulator, jvalue, vaarg, met.getParameters());
                return met.getInvoker().intInvoke(vm, st, met, vaarg.build());
            }
        });

        Pointer _CallByteMethod = _CallBooleanMethod;

        Pointer _CallByteMethodV = _CallBooleanMethodV;

        Pointer _CallByteMethodA = _CallBooleanMethodA;

        Pointer _CallCharMethod = _CallBooleanMethod;

        Pointer _CallCharMethodV = _CallBooleanMethodV;

        Pointer _CallCharMethodA = _CallBooleanMethodA;

        Pointer _CallShortMethod = _CallBooleanMethod;

        Pointer _CallShortMethodV = _CallBooleanMethodV;

        Pointer _CallShortMethodA = _CallBooleanMethodA;

        Pointer _CallIntMethod = _CallBooleanMethod;

        Pointer _CallIntMethodV = _CallBooleanMethodV;

        Pointer _CallIntMethodA = _CallBooleanMethodA;

        Pointer _CallLongMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);


                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                ArmVarArg64Visitor.read(emulator, vaarg, met.getParamsShorten());
                return met.getInvoker().longInvoke(vm, st, met, vaarg.build());
            }
        });

        Pointer _CallLongMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer va_list = context.getPointerArg(3);


                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                VaList64Visitor.read(emulator, va_list, vaarg, met.getParamsShorten());
                return met.getInvoker().longInvoke(vm, st, met, vaarg.build());

            }
        });

        Pointer _CallLongMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer jvalue = context.getPointerArg(3);


                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                JValueListVisitor.read(emulator, jvalue, vaarg, met.getParameters());
                return met.getInvoker().longInvoke(vm, st, met, vaarg.build());
            }
        });

        Pointer _CallFloatMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);


                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                ArmVarArg64Visitor.read(emulator, vaarg, met.getParamsShorten());
                return Float.floatToRawIntBits(met.getInvoker().floatInvoke(vm, st, met, vaarg.build()));
            }
        });

        Pointer _CallFloatMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer va_list = context.getPointerArg(3);


                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                VaList64Visitor.read(emulator, va_list, vaarg, met.getParamsShorten());
                return Float.floatToRawIntBits(met.getInvoker().floatInvoke(vm, st, met, vaarg.build()));
            }
        });

        Pointer _CallFloatMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer jvalue = context.getPointerArg(3);


                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                JValueListVisitor.read(emulator, jvalue, vaarg, met.getParameters());
                return Float.floatToRawIntBits(met.getInvoker().floatInvoke(vm, st, met, vaarg.build()));
            }
        });

        Pointer _CallDoubleMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);

                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                ArmVarArg64Visitor.read(emulator, vaarg, met.getParamsShorten());
                return Double.doubleToRawLongBits(met.getInvoker().doubleInvoke(vm, st, met, vaarg.build()));

            }
        });

        Pointer _CallDoubleMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {

                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer va_list = context.getPointerArg(3);


                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                VaList64Visitor.read(emulator, va_list, vaarg, met.getParamsShorten());
                return Double.doubleToRawLongBits(met.getInvoker().doubleInvoke(vm, st, met, vaarg.build()));
            }
        });

        Pointer _CallDoubleMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer jvalue = context.getPointerArg(3);


                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                JValueListVisitor.read(emulator, jvalue, vaarg, met.getParameters());
                return Double.doubleToRawLongBits(met.getInvoker().doubleInvoke(vm, st, met, vaarg.build()));
            }
        });

        Pointer _CallVoidMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);

                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                ArmVarArg64Visitor.read(emulator, vaarg, met.getParamsShorten());
                met.getInvoker().voidInvoke(vm, st, met, vaarg.build());
                return 0;
            }
        });

        Pointer _CallVoidMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {

                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer va_list = context.getPointerArg(3);


                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                VaList64Visitor.read(emulator, va_list, vaarg, met.getParamsShorten());
                met.getInvoker().voidInvoke(vm, st, met, vaarg.build());
                return 0;
            }
        });

        Pointer _CallVoidMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer jvalue = context.getPointerArg(3);


                var kobj = vm.resolveObject(object.toIntPeer());
                var st = vm.getStackTrace();
                var met = kobj.getObjectClass().getMethodTable().resolveMethod(jniMemberTable.resolveMethodId(jmethodID.toIntPeer()));

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten())
                        .putInt(object.toIntPeer());
                JValueListVisitor.read(emulator, jvalue, vaarg, met.getParameters());
                met.getInvoker().voidInvoke(vm, st, met, vaarg.build());
                return 0;
            }
        });

        Pointer _CallNonvirtualObjectMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualObjectMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualObjectMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualBooleanMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualBooleanMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualBooleanMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer clazz = context.getPointerArg(2);
                UnidbgPointer jmethodID = context.getPointerArg(3);
                UnidbgPointer jvalue = context.getPointerArg(4);


                throw new UnsupportedOperationException(""); // TODO
            }
        });

        Pointer _CallNonvirtualByteMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualByteMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualByteMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualCharMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualCharMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualCharMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualShortMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualShortMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualShortMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualIntMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualIntMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualIntMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualLongMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualLongMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualLongMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualFloatMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualFloatMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualFloatMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualDoubleMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualDoubleMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualDoubleMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualVoidMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _CallNonvirtualVoidMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer clazz = context.getPointerArg(2);
                UnidbgPointer jmethodID = context.getPointerArg(3);
                UnidbgPointer va_list = context.getPointerArg(4);


                throw new UnsupportedOperationException(""); // TODO
            }
        });

        Pointer _CallNonVirtualVoidMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer clazz = context.getPointerArg(2);
                UnidbgPointer jmethodID = context.getPointerArg(3);
                UnidbgPointer jvalue = context.getPointerArg(4);


                throw new UnsupportedOperationException(""); // TODO

            }
        });

        Pointer _GetFieldID = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer fieldName = context.getPointerArg(2);
                Pointer argsPointer = context.getPointerArg(3);
                String name = fieldName.getString(0);
                String args = argsPointer.getString(0);


                var tc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                if (verbose) {
                    System.out.format("JNIEnv->GetFieldID(%s.%s: %s)  -> ", tc, name, args);
                }

                var st = vm.getStackTrace();
                var tp = resolveClass(st, Type.getType(args));

                var ft = tc.getFieldTable();
                for (var ff : ft.getDeclaredFields()) {
                    if (Modifier.isStatic(ff.getModifiers())) continue;
                    if (!ff.getType().equals(tp)) continue;
                    if (!ff.getName().equals(name)) continue;

                    var rsp = jniMemberTable.getMemberId(ff);
                    if (verbose) {
                        System.out.print(ff);
                        System.out.append(" <").append(String.valueOf(rsp)).println(">");
                    }

                    return rsp;
                }

                if (verbose) {
                    System.out.println("null");
                }

                return 0;
            }
        });

        Pointer _GetObjectField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);

                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                return vm.getStackTrace().pushObject(obj.memoryView().objects[f.getObjectIndex()]);
            }
        });

        Pointer _GetBooleanField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);


                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                return obj.memoryView().buffer.get((int) f.getOffset()) & 0xFF;

            }
        });

        Pointer _GetByteField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);


                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                return obj.memoryView().buffer.get((int) f.getOffset());
            }
        });

        Pointer _GetCharField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);


                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                return obj.memoryView().buffer.getChar((int) f.getOffset());
            }
        });

        Pointer _GetShortField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);


                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                return obj.memoryView().buffer.getShort((int) f.getOffset());
            }
        });

        Pointer _GetIntField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);


                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                return obj.memoryView().buffer.getInt((int) f.getOffset());
            }
        });

        Pointer _GetLongField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);


                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                return obj.memoryView().buffer.getLong((int) f.getOffset());

            }
        });

        Pointer _GetFloatField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);


                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                return obj.memoryView().buffer.getInt((int) f.getOffset());
            }
        });

        Pointer _GetDoubleField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);


                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                return obj.memoryView().buffer.getLong((int) f.getOffset());
            }
        });

        Pointer _SetObjectField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);
                UnidbgPointer value = context.getPointerArg(3);

                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                obj.memoryView().objects[f.getObjectIndex()] = vm.resolveObject(value.toIntPeer());
                return 0;
            }
        });

        Pointer _SetBooleanField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);
                int value = context.getIntArg(3);

                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                obj.memoryView().buffer.put((int) f.getOffset(), (byte) value);
                return 0;
            }
        });

        Pointer _SetByteField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);
                int value = context.getIntArg(3);

                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                obj.memoryView().buffer.put((int) f.getOffset(), (byte) value);
                return 0;
            }
        });

        Pointer _SetCharField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);
                int value = context.getIntArg(3);

                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                obj.memoryView().buffer.putChar((int) f.getOffset(), (char) value);
                return 0;
            }
        });

        Pointer _SetShortField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);
                int value = context.getIntArg(3);

                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                obj.memoryView().buffer.putShort((int) f.getOffset(), (short) value);
                return 0;
            }
        });

        Pointer _SetIntField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);
                int value = context.getIntArg(3);

                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                obj.memoryView().buffer.putInt((int) f.getOffset(), value);
                return 0;
            }
        });

        Pointer _SetLongField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);
                long value = context.getLongArg(3);

                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                obj.memoryView().buffer.putLong((int) f.getOffset(), value);
                return 0;
            }
        });

        Pointer _SetFloatField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);

                ByteBuffer buffer = ByteBuffer.allocate(16);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.put(emulator.getBackend().reg_read_vector(Arm64Const.UC_ARM64_REG_Q0));
                buffer.flip();
                int value = buffer.getInt();

                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                obj.memoryView().buffer.putInt((int) f.getOffset(), value);
                return 0;
            }
        });

        Pointer _SetDoubleField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);

                ByteBuffer buffer = ByteBuffer.allocate(16);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.put(emulator.getBackend().reg_read_vector(Arm64Const.UC_ARM64_REG_Q0));
                buffer.flip();
                long value = buffer.getLong();

                var f = jniMemberTable.resolveFieldId(jfieldID.toIntPeer());
                var obj = vm.resolveObject(object.toIntPeer());

                obj.memoryView().buffer.putLong((int) f.getOffset(), value);
                return 0;
            }
        });

        Pointer _GetStaticMethodID = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                Pointer methodName = context.getPointerArg(2);
                Pointer argsPointer = context.getPointerArg(3);
                String name = methodName.getString(0);
                String args = argsPointer.getString(0);

                var st = vm.getStackTrace();
                var targetC = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                if (verbose) {
                    System.out.format("JNIEnv->GetStaticMethodID(%s.%s%s)", targetC, name, args);
                }

                var mttype = Type.getMethodType(args);
                var rtType = resolveClass(st, mttype.getReturnType());
                var pttype = Arrays.stream(mttype.getArgumentTypes()).map(it -> resolveClass(st, it)).toList();

                var mtt = targetC.getMethodTable().resolveMethod(name, rtType, pttype, true);
                var rsp = jniMemberTable.push(mtt);
                if (verbose) {
                    System.out.append("  ->  ").append(String.valueOf(mtt)).append(" <").append(String.valueOf(rsp)).println(">");
                }
                return rsp;

            }
        });

        Pointer _CallStaticObjectMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);

                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                ArmVarArg64Visitor.read(emulator, vaarg, met.getParamsShorten());
                var rpx = met.getInvoker().anyInvoke(vm, st, met, vaarg.build());

                return st.pushObject((KuimiObject<?>) rpx);
            }
        });

        Pointer _CallStaticObjectMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer va_list = context.getPointerArg(3);

                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                VaList64Visitor.read(emulator, va_list, vaarg, met.getParamsShorten());
                var rpx = met.getInvoker().anyInvoke(vm, st, met, vaarg.build());

                return st.pushObject((KuimiObject<?>) rpx);

            }
        });

        Pointer _CallStaticObjectMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer jvalue = context.getPointerArg(3);


                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                JValueListVisitor.read(emulator, jvalue, vaarg, met.getParameters());
                var rpx = met.getInvoker().anyInvoke(vm, st, met, vaarg.build());

                return st.pushObject((KuimiObject<?>) rpx);

            }
        });

        Pointer _CallStaticBooleanMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);


                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                ArmVarArg64Visitor.read(emulator, vaarg, met.getParamsShorten());
                return met.getInvoker().intInvoke(vm, st, met, vaarg.build());
            }
        });

        Pointer _CallStaticBooleanMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer va_list = context.getPointerArg(3);


                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                VaList64Visitor.read(emulator, va_list, vaarg, met.getParamsShorten());
                return met.getInvoker().intInvoke(vm, st, met, vaarg.build());

            }
        });

        Pointer _CallStaticBooleanMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer jvalue = context.getPointerArg(3);

                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                JValueListVisitor.read(emulator, jvalue, vaarg, met.getParameters());
                return met.getInvoker().intInvoke(vm, st, met, vaarg.build());

            }
        });

        Pointer _CallStaticByteMethod = _CallStaticBooleanMethod;

        Pointer _CallStaticByteMethodV = _CallStaticBooleanMethodV;

        Pointer _CallStaticByteMethodA = _CallStaticBooleanMethodA;

        Pointer _CallStaticCharMethod = _CallStaticBooleanMethod;

        Pointer _CallStaticCharMethodV = _CallStaticBooleanMethodV;

        Pointer _CallStaticCharMethodA = _CallStaticBooleanMethodA;

        Pointer _CallStaticShortMethod = _CallStaticBooleanMethod;

        Pointer _CallStaticShortMethodV = _CallStaticBooleanMethodV;

        Pointer _CallStaticShortMethodA = _CallStaticBooleanMethodA;

        Pointer _CallStaticIntMethod = _CallStaticBooleanMethod;

        Pointer _CallStaticIntMethodV = _CallStaticBooleanMethodV;

        Pointer _CallStaticIntMethodA = _CallStaticBooleanMethodA;

        Pointer _CallStaticLongMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);

                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                ArmVarArg64Visitor.read(emulator, vaarg, met.getParamsShorten());
                return met.getInvoker().longInvoke(vm, st, met, vaarg.build());
            }
        });

        Pointer _CallStaticLongMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer va_list = context.getPointerArg(3);

                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                VaList64Visitor.read(emulator, va_list, vaarg, met.getParamsShorten());
                return met.getInvoker().intInvoke(vm, st, met, vaarg.build());

            }
        });

        Pointer _CallStaticLongMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer jvalue = context.getPointerArg(3);

                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                JValueListVisitor.read(emulator, jvalue, vaarg, met.getParameters());
                return met.getInvoker().longInvoke(vm, st, met, vaarg.build());
            }
        });

        Pointer _CallStaticFloatMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);


                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                ArmVarArg64Visitor.read(emulator, vaarg, met.getParamsShorten());
                return Float.floatToRawIntBits(met.getInvoker().floatInvoke(vm, st, met, vaarg.build()));
            }
        });

        Pointer _CallStaticFloatMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer va_list = context.getPointerArg(3);

                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                VaList64Visitor.read(emulator, va_list, vaarg, met.getParamsShorten());
                return Float.floatToRawIntBits(met.getInvoker().floatInvoke(vm, st, met, vaarg.build()));
            }
        });

        Pointer _CallStaticFloatMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer jvalue = context.getPointerArg(3);

                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                JValueListVisitor.read(emulator, jvalue, vaarg, met.getParameters());
                return Float.floatToRawIntBits(met.getInvoker().floatInvoke(vm, st, met, vaarg.build()));
            }
        });

        Pointer _CallStaticDoubleMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);


                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                ArmVarArg64Visitor.read(emulator, vaarg, met.getParamsShorten());
                return Double.doubleToRawLongBits(met.getInvoker().doubleInvoke(vm, st, met, vaarg.build()));
            }
        });

        Pointer _CallStaticDoubleMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer va_list = context.getPointerArg(3);

                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                VaList64Visitor.read(emulator, va_list, vaarg, met.getParamsShorten());
                return Double.doubleToRawLongBits(met.getInvoker().doubleInvoke(vm, st, met, vaarg.build()));
            }
        });

        Pointer _CallStaticDoubleMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer jvalue = context.getPointerArg(3);

                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                JValueListVisitor.read(emulator, jvalue, vaarg, met.getParameters());
                return Double.doubleToRawLongBits(met.getInvoker().doubleInvoke(vm, st, met, vaarg.build()));
            }
        });

        Pointer _CallStaticVoidMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);


                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                ArmVarArg64Visitor.read(emulator, vaarg, met.getParamsShorten());
                met.getInvoker().voidInvoke(vm, st, met, vaarg.build());
                return 0;
            }
        });

        Pointer _CallStaticVoidMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer va_list = context.getPointerArg(3);

                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                VaList64Visitor.read(emulator, va_list, vaarg, met.getParamsShorten());
                met.getInvoker().voidInvoke(vm, st, met, vaarg.build());
                return 0;
            }
        });

        Pointer _CallStaticVoidMethodA = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jmethodID = context.getPointerArg(2);
                UnidbgPointer jvalue = context.getPointerArg(3);

                var kcc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                var st = vm.getStackTrace();
                var met = jniMemberTable.resolveMethodId(jmethodID.toIntPeer());

                var vaarg = VaArgModelBuilder.builder(met.getFullCallShorten());
                JValueListVisitor.read(emulator, jvalue, vaarg, met.getParameters());
                met.getInvoker().voidInvoke(vm, st, met, vaarg.build());
                return 0;
            }
        });

        Pointer _GetStaticFieldID = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer fieldName = context.getPointerArg(2);
                Pointer argsPointer = context.getPointerArg(3);
                String name = fieldName.getString(0);
                String args = argsPointer.getString(0);


                var tc = (KuimiClass) vm.resolveObject(clazz.toIntPeer());
                if (verbose) {
                    System.out.format("JNIEnv->GetStaticFieldID(%s.%s: %s) --> ", tc, name, args);
                }

                var st = vm.getStackTrace();
                var tp = resolveClass(st, Type.getType(args));

                var ft = tc.getFieldTable();
                for (var ff : ft.getDeclaredFields()) {
                    if (!Modifier.isStatic(ff.getModifiers())) continue;
                    if (!ff.getType().equals(tp)) continue;
                    if (!ff.getName().equals(name)) continue;

                    var rsp = jniMemberTable.push(ff);
                    if (verbose) {
                        System.out.print(ff);
                        System.out.append(" <").append(String.valueOf(rsp)).println(">");
                    }

                    return rsp;
                }

                if (verbose) {
                    System.out.println("null");
                }

                return 0;

            }
        });

        Pointer _GetStaticObjectField = _GetObjectField;

        Pointer _GetStaticBooleanField = _GetBooleanField;

        Pointer _GetStaticByteField = _GetByteField;

        Pointer _GetStaticCharField = _GetCharField;

        Pointer _GetStaticShortField = _GetShortField;

        Pointer _GetStaticIntField = _GetIntField;

        Pointer _GetStaticLongField = _GetLongField;

        Pointer _GetStaticFloatField = _GetFloatField;

        Pointer _GetStaticDoubleField = _GetDoubleField;

        Pointer _SetStaticObjectField = _SetObjectField;

        Pointer _SetStaticBooleanField = _SetBooleanField;

        Pointer _SetStaticByteField = _SetByteField;

        Pointer _SetStaticCharField = _SetCharField;

        Pointer _SetStaticShortField = _SetShortField;

        Pointer _SetStaticIntField = _SetIntField;

        Pointer _SetStaticLongField = _SetLongField;

        Pointer _GetStringUTFLength = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);

                if (object == null) return 0;
                var strObj = (KuimiString) vm.resolveObject(object.toIntPeer());
                if (strObj == null) return 0;


                return strObj.getDelegateInstance().getBytes(StandardCharsets.UTF_8).length;

            }
        });

        Pointer _GetStringUTFChars = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer isCopy = context.getPointerArg(2);

                if (object == null) return 0;
                var strObj = (KuimiString) vm.resolveObject(object.toIntPeer());
                if (strObj == null) return 0;

                if (isCopy != null) {
                    isCopy.setInt(0, JNI_TRUE);
                }

                var data = strObj.toString().getBytes(StandardCharsets.UTF_8);
                if (verbose) {
                    System.out.printf("JNIEnv->GetStringUTFChars(\"%s\") was called from %s%n", strObj, context.getLRPointer());
                }

                var ptr = strObj.getAttributeMap().attribute(MEMORY).allocateMemoryBlock(data.length + 1);
                ptr.write(0, data, 0, data.length);
                ptr.setByte(data.length, (byte) 0);
                return ptr.peer;
            }
        });

        Pointer _ReleaseStringUTFChars = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                UnidbgPointer pointer = context.getPointerArg(2);

                if (object == null) return JNI_ERR;
                var strObj = (KuimiString) vm.resolveObject(object.toIntPeer());
                if (strObj == null) return JNI_ERR;

                if (verbose) {
                    System.out.format("JNIEnv->ReleaseStringChars(%s)\n", strObj);
                }

                strObj.getAttributeMap().attribute(MEMORY).freeMemoryBlock(pointer);
                return 0;
            }
        });

        Pointer _GetArrayLength = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer pointer = context.getPointerArg(1);

                var arrObj = vm.resolveObject(pointer.toIntPeer());
                if (arrObj == null) return JNI_ERR;

                return ((KuimiArrays.Array) arrObj).length();
            }
        });

        Pointer _NewObjectArray = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                int size = context.getIntArg(1);
                UnidbgPointer elementClass = context.getPointerArg(2);
                UnidbgPointer initialElement = context.getPointerArg(3);

                var etype = (KuimiClass) vm.resolveObject(elementClass.toIntPeer());
                if (verbose) {
                    System.out.format("JNIEnv->NewObjectArray(%s, %s)%n", etype, size);
                }
                if (etype.isPrimitive()) {
                    throw new IllegalArgumentException("Allocating primitive array by JNIEnv->NewObjectArray()");
                }

                var arrx = etype.arrayType().allocateArray(size);
                if (initialElement != null) {
                    var initObj = vm.resolveObject(initialElement.toIntPeer());
                    var delegateArray = (KuimiObject<?>[]) arrx.getDelegateInstance();
                    for (var i = 0; i < size; i++) {
                        delegateArray[i] = initObj;
                    }
                }

                return vm.getStackTrace().pushObject(arrx);
            }
        });

        Pointer _GetObjectArrayElement = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                int index = context.getIntArg(2);

                var arrayObj = vm.resolveObject(object.toIntPeer());
                var rsp = ((KuimiObject<?>[]) arrayObj.getDelegateInstance())[index];
                if (verbose) {
                    System.out.printf("JNIEnv->GetObjectArrayElement(%s, %d) => %s was called from %s%n", arrayObj, index, rsp, context.getLRPointer());
                }
                return vm.getStackTrace().pushObject(rsp);
            }
        });

        Pointer _SetObjectArrayElement = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                int index = context.getIntArg(2);
                UnidbgPointer element = context.getPointerArg(3);

                var arr = ((KuimiObject<?>[]) vm.resolveObject(object.toIntPeer()).getDelegateInstance());
                if (element == null) {
                    arr[index] = null;
                } else {
                    arr[index] = vm.resolveObject(element.toIntPeer());
                }

                return 0;

            }
        });

        Pointer _NewBooleanArray = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                int size = context.getIntArg(1);

                return vm.getStackTrace().pushObject(
                        vm.getPrimitiveClass(Type.BOOLEAN_TYPE).arrayType().allocateArray(size)
                );
            }
        });

        Pointer _NewByteArray = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                int size = context.getIntArg(1);


                return vm.getStackTrace().pushObject(
                        vm.getPrimitiveClass(Type.BYTE_TYPE).arrayType().allocateArray(size)
                );
            }
        });

        Pointer _NewCharArray = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                int size = context.getIntArg(1);


                return vm.getStackTrace().pushObject(
                        vm.getPrimitiveClass(Type.CHAR_TYPE).arrayType().allocateArray(size)
                );
            }
        });

        Pointer _NewShortArray = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                int size = context.getIntArg(1);


                return vm.getStackTrace().pushObject(
                        vm.getPrimitiveClass(Type.SHORT_TYPE).arrayType().allocateArray(size)
                );
            }
        });

        Pointer _NewIntArray = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                int size = context.getIntArg(1);


                return vm.getStackTrace().pushObject(
                        vm.getPrimitiveClass(Type.INT_TYPE).arrayType().allocateArray(size)
                );

            }
        });

        Pointer _NewLongArray = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _NewFloatArray = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                int size = context.getIntArg(1);


                return vm.getStackTrace().pushObject(
                        vm.getPrimitiveClass(Type.LONG_TYPE).arrayType().allocateArray(size)
                );
            }
        });

        Pointer _NewDoubleArray = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                int size = context.getIntArg(1);


                return vm.getStackTrace().pushObject(
                        vm.getPrimitiveClass(Type.DOUBLE_TYPE).arrayType().allocateArray(size)
                );
            }
        });

        Pointer _GetBooleanArrayElements = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer arrayPointer = context.getPointerArg(1);
                Pointer isCopy = context.getPointerArg(2);

                var arrx = vm.resolveObject(arrayPointer.toIntPeer());
                if (isCopy != null) isCopy.setInt(0, JNI_ERR);

                return arrx.getAttributeMap().attribute(MEMORY).arrayCritical(arrx);
            }
        });

        Pointer _GetByteArrayElements = _GetBooleanArrayElements;

        Pointer _GetCharArrayElements = _GetBooleanArrayElements;

        Pointer _GetShortArrayElements = _GetBooleanArrayElements;

        Pointer _GetIntArrayElements = _GetBooleanArrayElements;

        Pointer _SetStaticFloatField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);
                ByteBuffer buffer = ByteBuffer.allocate(16);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.put(emulator.getBackend().reg_read_vector(Arm64Const.UC_ARM64_REG_Q0));
                buffer.flip();
                float value = buffer.getFloat();


                throw new UnsupportedOperationException(""); // TODO

            }
        });

        Pointer _SetStaticDoubleField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                UnidbgPointer jfieldID = context.getPointerArg(2);
                ByteBuffer buffer = ByteBuffer.allocate(16);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.put(emulator.getBackend().reg_read_vector(Arm64Const.UC_ARM64_REG_Q0));
                buffer.flip();
                double value = buffer.getDouble();


                throw new UnsupportedOperationException(""); // TODO

            }
        });

        Pointer _NewString = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                var chars = context.getPointerArg(1);
                var len = context.getIntArg(2);

                if (chars == null)
                    return vm.getStackTrace().pushObject(new KuimiString(vm, ""));

                var charx = new char[len];
                var bfx = new byte[len * Character.BYTES];
                chars.read(0, bfx, 0, bfx.length);

                ByteBuffer.wrap(bfx).order(ByteOrder.LITTLE_ENDIAN).asCharBuffer().get(charx);

                return vm.getStackTrace().pushObject(new KuimiString(vm, new String(charx)));
            }
        });

        Pointer _GetStringLength = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);


                var str = (KuimiString) vm.resolveObject(object.toIntPeer());
                return str.getDelegateInstance().length();
            }
        });

        Pointer _GetStringChars = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                Pointer isCopy = context.getPointerArg(2);

                if (isCopy != null) isCopy.setInt(0, JNI_TRUE);

                var obj = vm.resolveObject(object.toIntPeer());
                var val = obj.toString();
                var buf = ByteBuffer.allocate(val.length() * 2).order(ByteOrder.LITTLE_ENDIAN);
                buf.asCharBuffer().put(val);

                var ptr = obj.getAttributeMap().attribute(MEMORY).allocateMemoryBlock(val.length() * 2);
                ptr.write(buf.array());
                return ptr.peer;
            }
        });

        Pointer _ReleaseStringChars = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                Pointer pointer = context.getPointerArg(2);

                var sobj = vm.resolveObject(object.toIntPeer());
                sobj.getAttributeMap().attribute(MEMORY).freeMemoryBlock(pointer);

                return 0;
            }
        });

        Pointer _NewStringUTF = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer bytes = context.getPointerArg(1);
                if (bytes == null) {
                    return VM.JNI_NULL;
                }

                return vm.getStackTrace().pushObject(new KuimiString(vm, bytes.getString(0)));
            }
        });

        Pointer _GetLongArrayElements = _GetBooleanArrayElements;

        Pointer _GetFloatArrayElements = _GetBooleanArrayElements;

        Pointer _GetDoubleArrayElements = _GetBooleanArrayElements;

        Pointer _ReleaseBooleanArrayElements = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer arrayPointer = context.getPointerArg(1);
                Pointer pointer = context.getPointerArg(2);
                int mode = context.getIntArg(3);

                var obj = vm.resolveObject(arrayPointer.toIntPeer());
                var mem = obj.getAttributeMap().attribute(MEMORY);
                mem.arrayCriticalRelease(obj, pointer, mode);
                return 0;
            }
        });

        Pointer _ReleaseByteArrayElements = _ReleaseBooleanArrayElements;

        Pointer _ReleaseCharArrayElements = _ReleaseBooleanArrayElements;

        Pointer _ReleaseShortArrayElements = _ReleaseBooleanArrayElements;

        Pointer _ReleaseIntArrayElements = _ReleaseBooleanArrayElements;

        Pointer _ReleaseLongArrayElements = _ReleaseBooleanArrayElements;

        Pointer _ReleaseFloatArrayElements = _ReleaseBooleanArrayElements;

        Pointer _ReleaseDoubleArrayElements = _ReleaseBooleanArrayElements;

        Pointer _GetBooleanArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _GetByteArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                int start = context.getIntArg(2);
                int length = context.getIntArg(3);
                Pointer buf = context.getPointerArg(4);


                byte[] data = (byte[]) vm.resolveObject(object.toIntPeer()).getDelegateInstance();
                buf.write(0, data, start, length);

                return 0;

            }
        });

        Pointer _GetCharArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _GetShortArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                int start = context.getIntArg(2);
                int length = context.getIntArg(3);
                Pointer buf = context.getPointerArg(4);


                throw new UnsupportedOperationException(""); // TODO
            }
        });

        Pointer _GetIntArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _GetLongArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _GetFloatArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _GetDoubleArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                int start = context.getIntArg(2);
                int length = context.getIntArg(3);
                Pointer buf = context.getPointerArg(4);


                throw new UnsupportedOperationException(""); // TODO

            }
        });

        Pointer _SetBooleanArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _SetByteArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                int start = context.getIntArg(2);
                int length = context.getIntArg(3);
                Pointer buf = context.getPointerArg(4);

                var arrObj = vm.resolveObject(object.toIntPeer());
                var barray = (byte[]) arrObj.getDelegateInstance();

                var cpy = buf.getByteArray(0, length);
                System.arraycopy(cpy, 0, barray, start, length);

                return 0;
            }
        });

        Pointer _SetCharArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _SetShortArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _SetIntArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                int start = context.getIntArg(2);
                int length = context.getIntArg(3);
                Pointer buf = context.getPointerArg(4);


                throw new UnsupportedOperationException(""); // TODO
            }
        });

        Pointer _SetLongArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _SetFloatArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                int start = context.getIntArg(2);
                int length = context.getIntArg(3);
                Pointer buf = context.getPointerArg(4);


                throw new UnsupportedOperationException(""); // TODO

            }
        });

        Pointer _SetDoubleArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                int start = context.getIntArg(2);
                int length = context.getIntArg(3);
                Pointer buf = context.getPointerArg(4);


                throw new UnsupportedOperationException(""); // TODO

            }
        });

        Pointer _RegisterNatives = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer clazz = context.getPointerArg(1);
                Pointer methods = context.getPointerArg(2);
                int nMethods = context.getIntArg(3);

                var targetC = (KuimiClass) vm.resolveObject(clazz.toIntPeer());

                var st = vm.getStackTrace();

                for (int i = 0; i < nMethods; i++) {
                    Pointer method = methods.share((long) i * emulator.getPointerSize() * 3);
                    Pointer name = method.getPointer(0);
                    Pointer signature = method.getPointer(emulator.getPointerSize());
                    Pointer fnPtr = method.getPointer(emulator.getPointerSize() * 2L);
                    String methodName = name.getString(0);
                    String signatureValue = signature.getString(0);

                    // dvmClass.nativesMap.put(methodName + signatureValue, (UnidbgPointer) fnPtr);

                    if (verbose) {
                        System.out.format("JNIEnv->RegisterNative(%s, %s%s, %s)%n", targetC, methodName, signatureValue, fnPtr);
                    }

                    var mttype = Type.getMethodType(signatureValue);
                    var rtType = resolveClass(st, mttype.getReturnType());
                    var pttype = Arrays.stream(mttype.getArgumentTypes()).map(it -> resolveClass(st, it)).toList();

                    var mttable = targetC.getMethodTable().getDeclaredMethodNoClose();
                    reg:
                    {
                        for (var mtt : mttable) {
                            if (!mtt.getReturnType().equals(rtType)) continue;
                            if (!mtt.getParameters().equals(pttype)) continue;
                            if (!mtt.getMethodName().equals(methodName)) continue;

                            if (verbose) {
                                System.out.format("JNIEnv->RegisterNativeFound(%s, %s)%n", mtt, fnPtr);
                            }

                            mtt.attachImplementation(KuimiJniMethodHandle.invoker(mtt, KuimiUnidbgVM64.this, ((UnidbgPointer) fnPtr).peer));

                            break reg;
                        }
                        if (verbose) {
                            System.out.format("JNIEnv->RegisterNative: WARNING, (%s%s, %s) was not attached because method not found.%n", methodName, signatureValue, fnPtr);
                        }
                    }

                }
                return JNI_OK;

            }
        });

        Pointer _UnregisterNatives = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _MonitorEnter = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                Pointer env = context.getPointerArg(0);


                // TODO Moitor Enter
                return 0;
            }
        });

        Pointer _MonitorExit = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                Pointer env = context.getPointerArg(0);


                // TODO Monitor Exit
                return 0;
            }
        });

        Pointer _GetJavaVM = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer vm = context.getPointerArg(1);


                vm.setPointer(0, _JavaVM);
                return JNI_OK;
            }
        });

        Pointer _GetStringRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                int start = context.getIntArg(2);
                int length = context.getIntArg(3);
                Pointer buf = context.getPointerArg(4);


                throw new UnsupportedOperationException(""); // TODO

            }
        });

        Pointer _GetStringUTFRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                int start = context.getIntArg(2);
                int length = context.getIntArg(3);
                Pointer buf = context.getPointerArg(4);


                throw new UnsupportedOperationException(""); // TODO

            }
        });

        Pointer _GetPrimitiveArrayCritical = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                Pointer isCopy = context.getPointerArg(2);


                throw new UnsupportedOperationException(""); // TODO

            }
        });

        Pointer _ReleasePrimitiveArrayCritical = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                Pointer pointer = context.getPointerArg(2);
                int mode = context.getIntArg(3);


                throw new UnsupportedOperationException(""); // TODO

            }
        });

        Pointer _GetStringCritical = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _ReleaseStringCritical = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _NewWeakGlobalRef = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);
                if (object == null) {
                    return 0;
                }

                throw new UnsupportedOperationException(""); // TODO

            }
        });

        Pointer _DeleteWeakGlobalRef = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _ExceptionCheck = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                var st = vm.getStackTrace();
                if (st == null) return JNI_TRUE;

                return st.throwable == null ? JNI_FALSE : JNI_TRUE;
            }
        });

        Pointer _NewDirectByteBuffer = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _GetDirectBufferAddress = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _GetDirectBufferCapacity = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        Pointer _GetObjectRefType = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnidbgPointer object = context.getPointerArg(1);

                if (verbose) {
                    System.out.format("JNIEnv->GetObjectRefType(%s)%n", object);
                }

                throw new UnsupportedOperationException(""); // TODO
            }
        });

        Pointer _GetModule = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                throw new UnsupportedOperationException();
            }
        });

        final int last = 0x748;
        final UnidbgPointer impl = svcMemory.allocate(last + 8, "JNIEnv.impl");
        for (int i = 0; i <= last; i += 8) {
            impl.setLong(i, i);
        }
        impl.setPointer(0x20, _GetVersion);
        impl.setPointer(0x28, _DefineClass);
        impl.setPointer(0x30, _FindClass);
        impl.setPointer(0x38, _FromReflectedMethod);
        impl.setPointer(0x40, _FromReflectedField);
        impl.setPointer(0x48, _ToReflectedMethod);
        impl.setPointer(0x50, _GetSuperclass);
        impl.setPointer(0x58, _IsAssignableFrom);
        impl.setPointer(0x60, _ToReflectedField);
        impl.setPointer(0x68, _Throw);
        impl.setPointer(0x70, _ThrowNew);
        impl.setPointer(0x78, _ExceptionOccurred);
        impl.setPointer(0x80, _ExceptionDescribe);
        impl.setPointer(0x88, _ExceptionClear);
        impl.setPointer(0x90, _FatalError);
        impl.setPointer(0x98, _PushLocalFrame);
        impl.setPointer(0xa0, _PopLocalFrame);
        impl.setPointer(0xa8, _NewGlobalRef);
        impl.setPointer(0xb0, _DeleteGlobalRef);
        impl.setPointer(0xb8, _DeleteLocalRef);
        impl.setPointer(0xc0, _IsSameObject);
        impl.setPointer(0xc8, _NewLocalRef);
        impl.setPointer(0xd0, _EnsureLocalCapacity);
        impl.setPointer(0xd8, _AllocObject);
        impl.setPointer(0xe0, _NewObject);
        impl.setPointer(0xe8, _NewObjectV);
        impl.setPointer(0xf0, _NewObjectA);
        impl.setPointer(0xf8, _GetObjectClass);
        impl.setPointer(0x100, _IsInstanceOf);
        impl.setPointer(0x108, _GetMethodID);
        impl.setPointer(0x110, _CallObjectMethod);
        impl.setPointer(0x118, _CallObjectMethodV);
        impl.setPointer(0x120, _CallObjectMethodA);
        impl.setPointer(0x128, _CallBooleanMethod);
        impl.setPointer(0x130, _CallBooleanMethodV);
        impl.setPointer(0x138, _CallBooleanMethodA);
        impl.setPointer(0x140, _CallByteMethod);
        impl.setPointer(0x148, _CallByteMethodV);
        impl.setPointer(0x150, _CallByteMethodA);
        impl.setPointer(0x158, _CallCharMethod);
        impl.setPointer(0x160, _CallCharMethodV);
        impl.setPointer(0x168, _CallCharMethodA);
        impl.setPointer(0x170, _CallShortMethod);
        impl.setPointer(0x178, _CallShortMethodV);
        impl.setPointer(0x180, _CallShortMethodA);
        impl.setPointer(0x188, _CallIntMethod);
        impl.setPointer(0x190, _CallIntMethodV);
        impl.setPointer(0x198, _CallIntMethodA);
        impl.setPointer(0x1a0, _CallLongMethod);
        impl.setPointer(0x1a8, _CallLongMethodV);
        impl.setPointer(0x1b0, _CallLongMethodA);
        impl.setPointer(0x1b8, _CallFloatMethod);
        impl.setPointer(0x1c0, _CallFloatMethodV);
        impl.setPointer(0x1c8, _CallFloatMethodA);
        impl.setPointer(0x1d0, _CallDoubleMethod);
        impl.setPointer(0x1d8, _CallDoubleMethodV);
        impl.setPointer(0x1e0, _CallDoubleMethodA);
        impl.setPointer(0x1e8, _CallVoidMethod);
        impl.setPointer(0x1f0, _CallVoidMethodV);
        impl.setPointer(0x1f8, _CallVoidMethodA);
        impl.setPointer(0x200, _CallNonvirtualObjectMethod);
        impl.setPointer(0x208, _CallNonvirtualObjectMethodV);
        impl.setPointer(0x210, _CallNonvirtualObjectMethodA);
        impl.setPointer(0x218, _CallNonvirtualBooleanMethod);
        impl.setPointer(0x220, _CallNonvirtualBooleanMethodV);
        impl.setPointer(0x228, _CallNonvirtualBooleanMethodA);
        impl.setPointer(0x230, _CallNonvirtualByteMethod);
        impl.setPointer(0x238, _CallNonvirtualByteMethodV);
        impl.setPointer(0x240, _CallNonvirtualByteMethodA);
        impl.setPointer(0x248, _CallNonvirtualCharMethod);
        impl.setPointer(0x250, _CallNonvirtualCharMethodV);
        impl.setPointer(0x258, _CallNonvirtualCharMethodA);
        impl.setPointer(0x260, _CallNonvirtualShortMethod);
        impl.setPointer(0x268, _CallNonvirtualShortMethodV);
        impl.setPointer(0x270, _CallNonvirtualShortMethodA);
        impl.setPointer(0x278, _CallNonvirtualIntMethod);
        impl.setPointer(0x280, _CallNonvirtualIntMethodV);
        impl.setPointer(0x288, _CallNonvirtualIntMethodA);
        impl.setPointer(0x290, _CallNonvirtualLongMethod);
        impl.setPointer(0x298, _CallNonvirtualLongMethodV);
        impl.setPointer(0x2a0, _CallNonvirtualLongMethodA);
        impl.setPointer(0x2a8, _CallNonvirtualFloatMethod);
        impl.setPointer(0x2b0, _CallNonvirtualFloatMethodV);
        impl.setPointer(0x2b8, _CallNonvirtualFloatMethodA);
        impl.setPointer(0x2c0, _CallNonvirtualDoubleMethod);
        impl.setPointer(0x2c8, _CallNonvirtualDoubleMethodV);
        impl.setPointer(0x2d0, _CallNonvirtualDoubleMethodA);
        impl.setPointer(0x2d8, _CallNonvirtualVoidMethod);
        impl.setPointer(0x2e0, _CallNonvirtualVoidMethodV);
        impl.setPointer(0x2e8, _CallNonVirtualVoidMethodA);
        impl.setPointer(0x2f0, _GetFieldID);
        impl.setPointer(0x2f8, _GetObjectField);
        impl.setPointer(0x300, _GetBooleanField);
        impl.setPointer(0x308, _GetByteField);
        impl.setPointer(0x310, _GetCharField);
        impl.setPointer(0x318, _GetShortField);
        impl.setPointer(0x320, _GetIntField);
        impl.setPointer(0x328, _GetLongField);
        impl.setPointer(0x330, _GetFloatField);
        impl.setPointer(0x338, _GetDoubleField);
        impl.setPointer(0x340, _SetObjectField);
        impl.setPointer(0x348, _SetBooleanField);
        impl.setPointer(0x350, _SetByteField);
        impl.setPointer(0x358, _SetCharField);
        impl.setPointer(0x360, _SetShortField);
        impl.setPointer(0x368, _SetIntField);
        impl.setPointer(0x370, _SetLongField);
        impl.setPointer(0x378, _SetFloatField);
        impl.setPointer(0x380, _SetDoubleField);
        impl.setPointer(0x388, _GetStaticMethodID);
        impl.setPointer(0x390, _CallStaticObjectMethod);
        impl.setPointer(0x398, _CallStaticObjectMethodV);
        impl.setPointer(0x3a0, _CallStaticObjectMethodA);
        impl.setPointer(0x3a8, _CallStaticBooleanMethod);
        impl.setPointer(0x3b0, _CallStaticBooleanMethodV);
        impl.setPointer(0x3b8, _CallStaticBooleanMethodA);
        impl.setPointer(0x3c0, _CallStaticByteMethod);
        impl.setPointer(0x3c8, _CallStaticByteMethodV);
        impl.setPointer(0x3d0, _CallStaticByteMethodA);
        impl.setPointer(0x3d8, _CallStaticCharMethod);
        impl.setPointer(0x3e0, _CallStaticCharMethodV);
        impl.setPointer(0x3e8, _CallStaticCharMethodA);
        impl.setPointer(0x3f0, _CallStaticShortMethod);
        impl.setPointer(0x3f8, _CallStaticShortMethodV);
        impl.setPointer(0x400, _CallStaticShortMethodA);
        impl.setPointer(0x408, _CallStaticIntMethod);
        impl.setPointer(0x410, _CallStaticIntMethodV);
        impl.setPointer(0x418, _CallStaticIntMethodA);
        impl.setPointer(0x420, _CallStaticLongMethod);
        impl.setPointer(0x428, _CallStaticLongMethodV);
        impl.setPointer(0x430, _CallStaticLongMethodA);
        impl.setPointer(0x438, _CallStaticFloatMethod);
        impl.setPointer(0x440, _CallStaticFloatMethodV);
        impl.setPointer(0x448, _CallStaticFloatMethodA);
        impl.setPointer(0x450, _CallStaticDoubleMethod);
        impl.setPointer(0x458, _CallStaticDoubleMethodV);
        impl.setPointer(0x460, _CallStaticDoubleMethodA);
        impl.setPointer(0x468, _CallStaticVoidMethod);
        impl.setPointer(0x470, _CallStaticVoidMethodV);
        impl.setPointer(0x478, _CallStaticVoidMethodA);
        impl.setPointer(0x480, _GetStaticFieldID);
        impl.setPointer(0x488, _GetStaticObjectField);
        impl.setPointer(0x490, _GetStaticBooleanField);
        impl.setPointer(0x498, _GetStaticByteField);
        impl.setPointer(0x4a0, _GetStaticCharField);
        impl.setPointer(0x4a8, _GetStaticShortField);
        impl.setPointer(0x4b0, _GetStaticIntField);
        impl.setPointer(0x4b8, _GetStaticLongField);
        impl.setPointer(0x4c0, _GetStaticFloatField);
        impl.setPointer(0x4c8, _GetStaticDoubleField);
        impl.setPointer(0x4d0, _SetStaticObjectField);
        impl.setPointer(0x4d8, _SetStaticBooleanField);
        impl.setPointer(0x4e0, _SetStaticByteField);
        impl.setPointer(0x4e8, _SetStaticCharField);
        impl.setPointer(0x4f0, _SetStaticShortField);
        impl.setPointer(0x4f8, _SetStaticIntField);
        impl.setPointer(0x500, _SetStaticLongField);
        impl.setPointer(0x508, _SetStaticFloatField);
        impl.setPointer(0x510, _SetStaticDoubleField);
        impl.setPointer(0x518, _NewString);
        impl.setPointer(0x520, _GetStringLength);
        impl.setPointer(0x528, _GetStringChars);
        impl.setPointer(0x530, _ReleaseStringChars);
        impl.setPointer(0x538, _NewStringUTF);
        impl.setPointer(0x540, _GetStringUTFLength);
        impl.setPointer(0x548, _GetStringUTFChars);
        impl.setPointer(0x550, _ReleaseStringUTFChars);
        impl.setPointer(0x558, _GetArrayLength);
        impl.setPointer(0x560, _NewObjectArray);
        impl.setPointer(0x568, _GetObjectArrayElement);
        impl.setPointer(0x570, _SetObjectArrayElement);
        impl.setPointer(0x578, _NewBooleanArray);
        impl.setPointer(0x580, _NewByteArray);
        impl.setPointer(0x588, _NewCharArray);
        impl.setPointer(0x590, _NewShortArray);
        impl.setPointer(0x598, _NewIntArray);
        impl.setPointer(0x5a0, _NewLongArray);
        impl.setPointer(0x5a8, _NewFloatArray);
        impl.setPointer(0x5b0, _NewDoubleArray);
        impl.setPointer(0x5b8, _GetBooleanArrayElements);
        impl.setPointer(0x5c0, _GetByteArrayElements);
        impl.setPointer(0x5c8, _GetCharArrayElements);
        impl.setPointer(0x5d0, _GetShortArrayElements);
        impl.setPointer(0x5d8, _GetIntArrayElements);
        impl.setPointer(0x5e0, _GetLongArrayElements);
        impl.setPointer(0x5e8, _GetFloatArrayElements);
        impl.setPointer(0x5f0, _GetDoubleArrayElements);
        impl.setPointer(0x5f8, _ReleaseBooleanArrayElements);
        impl.setPointer(0x600, _ReleaseByteArrayElements);
        impl.setPointer(0x608, _ReleaseCharArrayElements);
        impl.setPointer(0x610, _ReleaseShortArrayElements);
        impl.setPointer(0x618, _ReleaseIntArrayElements);
        impl.setPointer(0x620, _ReleaseLongArrayElements);
        impl.setPointer(0x628, _ReleaseFloatArrayElements);
        impl.setPointer(0x630, _ReleaseDoubleArrayElements);
        impl.setPointer(0x638, _GetBooleanArrayRegion);
        impl.setPointer(0x640, _GetByteArrayRegion);
        impl.setPointer(0x648, _GetCharArrayRegion);
        impl.setPointer(0x650, _GetShortArrayRegion);
        impl.setPointer(0x658, _GetIntArrayRegion);
        impl.setPointer(0x660, _GetLongArrayRegion);
        impl.setPointer(0x668, _GetFloatArrayRegion);
        impl.setPointer(0x670, _GetDoubleArrayRegion);
        impl.setPointer(0x678, _SetBooleanArrayRegion);
        impl.setPointer(0x680, _SetByteArrayRegion);
        impl.setPointer(0x688, _SetCharArrayRegion);
        impl.setPointer(0x690, _SetShortArrayRegion);
        impl.setPointer(0x698, _SetIntArrayRegion);
        impl.setPointer(0x6a0, _SetLongArrayRegion);
        impl.setPointer(0x6a8, _SetFloatArrayRegion);
        impl.setPointer(0x6b0, _SetDoubleArrayRegion);
        impl.setPointer(0x6b8, _RegisterNatives);
        impl.setPointer(0x6c0, _UnregisterNatives);
        impl.setPointer(0x6c8, _MonitorEnter);
        impl.setPointer(0x6d0, _MonitorExit);
        impl.setPointer(0x6d8, _GetJavaVM);
        impl.setPointer(0x6e0, _GetStringRegion);
        impl.setPointer(0x6e8, _GetStringUTFRegion);
        impl.setPointer(0x6f0, _GetPrimitiveArrayCritical);
        impl.setPointer(0x6f8, _ReleasePrimitiveArrayCritical);
        impl.setPointer(0x700, _GetStringCritical);
        impl.setPointer(0x708, _ReleaseStringCritical);
        impl.setPointer(0x710, _NewWeakGlobalRef);
        impl.setPointer(0x718, _DeleteWeakGlobalRef);
        impl.setPointer(0x720, _ExceptionCheck);
        impl.setPointer(0x728, _NewDirectByteBuffer);
        impl.setPointer(0x730, _GetDirectBufferAddress);
        impl.setPointer(0x738, _GetDirectBufferCapacity);
        impl.setPointer(0x740, _GetObjectRefType);
        impl.setPointer(last, _GetModule);

        var _JNIEnv = svcMemory.allocate(emulator.getPointerSize(), "_JNIEnv");
        _JNIEnv.setPointer(0, impl);

        UnidbgPointer _AttachCurrentThread = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                Pointer vm = context.getPointerArg(0);
                Pointer env = context.getPointerArg(1);
                Pointer args = context.getPointerArg(2); // JavaVMAttachArgs*


                env.setPointer(0, _JNIEnv);
                return JNI_OK;
            }
        });

        UnidbgPointer _GetEnv = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                Pointer vm = context.getPointerArg(0);
                Pointer env = context.getPointerArg(1);
                int version = context.getIntArg(2);


                env.setPointer(0, _JNIEnv);
                return JNI_OK;
            }
        });

        UnidbgPointer _JNIInvokeInterface = svcMemory.allocate(emulator.getPointerSize() * 8, "_JNIInvokeInterface");
        for (int i = 0; i < emulator.getPointerSize() * 8; i += emulator.getPointerSize()) {
            _JNIInvokeInterface.setInt(i, i);
        }
        _JNIInvokeInterface.setPointer(emulator.getPointerSize() * 4L, _AttachCurrentThread);
        _JNIInvokeInterface.setPointer(emulator.getPointerSize() * 6L, _GetEnv);

        _JavaVM.setPointer(0, _JNIInvokeInterface);

        this._JNIEnv = _JNIEnv;
        this._JNIImpl = impl;
        this._JavaVM = _JavaVM;
    }


    public Pointer getJavaVM() {
        return _JavaVM;
    }

    public Pointer getJNIEnv() {
        return _JNIEnv;
    }


    protected final KuimiClass resolveClass(StackTrace st, Type type) {
        var sort = type.getSort();
        if (sort == Type.ARRAY || sort == Type.OBJECT) {
            return resolve1(st, type);
        }
        return vm.getPrimitiveClass(type);
    }

    protected KuimiClass resolve1(StackTrace st, Type type) {
        return vm.resolveClass(type);
    }
    public void throwException(Throwable error) {
        var clazz= vm.resolveClass(Type.getType(error.getClass()));
        if(clazz==null){
            throw new RuntimeException("Class impl not found:"+error.getClass().getName());
        }
        var obj = (KuimiObject<Throwable>)clazz.allocateNewObject();
        obj.setDelegateInstance(error);
        vm.getStackTrace().throwable = obj;
    }
    public void throwException(KuimiObject<?> error) {
        vm.getStackTrace().throwable = error;
    }

    private KuimiClass resolveClass(StackTrace stackTrace, String type) {
        return resolveClass(stackTrace, Type.getObjectType(type));
    }
}
