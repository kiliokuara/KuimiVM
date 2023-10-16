package com.kiliokuara.kuimivm.abstractvm;

import com.kiliokuara.kuimivm.*;
import com.kiliokuara.kuimivm.execute.ObjectPool;
import com.kiliokuara.kuimivm.execute.StackTrace;
import com.kiliokuara.kuimivm.objects.KuimiArrays;
import com.kiliokuara.kuimivm.objects.KuimiString;
import com.kiliokuara.kuimivm.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

public class KuimiAbstractVM extends KuimiVM {
    private final ClassPool bootstrapPool = new ClassPool();
    private final ObjectPool globalPool = new ObjectPool(this, 2048);
    private final ObjectPool weakGlobalPool = new ObjectPool(this, 2048);

    private final KuimiClass javaLangObject, javaLangClassLoader, javaLangClass, javaLangString;

    private final ThreadLocal<StackTrace> THREAD_STACK_TRACE = new ThreadLocal<>();

    @SuppressWarnings({"rawtypes", "unchecked", "unused"})
    public KuimiAbstractVM() {
        {// base classes
            // TODO: sb
            javaLangObject = new KuimiClass(this, Type.getObjectType("java/lang/Object"), Opcodes.ACC_PUBLIC, null, null, null);
            javaLangClass = new KuimiClass(this, Type.getObjectType("java/lang/Class"), Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, null, javaLangObject, null);
            bootstrapPool.put(javaLangObject);
            bootstrapPool.put(javaLangClass);


            { // primitive classes
                for (var type : new Type[]{
                        Type.BOOLEAN_TYPE,
                        Type.BYTE_TYPE,
                        Type.CHAR_TYPE,
                        Type.SHORT_TYPE,
                        Type.INT_TYPE,
                        Type.LONG_TYPE,

                        Type.FLOAT_TYPE,
                        Type.DOUBLE_TYPE,

                        Type.VOID_TYPE,
                }) {
                    var typeC = new KuimiClass(this, type, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, null, null, null);
                    bootstrapPool.put(typeC);
                    typeC.getMethodTable().closeTable();
                }

                BiConsumer<KuimiClass, IntFunction<Object>> attachPM = (elmt, allo) -> {
                    //noinspection deprecation
                    KuimiClass.Internal.attachArrayType(elmt, new KuimiArrays.PrimitiveArrayClass(elmt, allo));
                };

                attachPM.accept(getPrimitiveClass(Type.BOOLEAN_TYPE), boolean[]::new);
                attachPM.accept(getPrimitiveClass(Type.BYTE_TYPE), byte[]::new);
                attachPM.accept(getPrimitiveClass(Type.CHAR_TYPE), char[]::new);
                attachPM.accept(getPrimitiveClass(Type.SHORT_TYPE), short[]::new);
                attachPM.accept(getPrimitiveClass(Type.INT_TYPE), int[]::new);
                attachPM.accept(getPrimitiveClass(Type.LONG_TYPE), long[]::new);
                attachPM.accept(getPrimitiveClass(Type.FLOAT_TYPE), float[]::new);
                attachPM.accept(getPrimitiveClass(Type.DOUBLE_TYPE), double[]::new);
            }

            var javaLangCharSequence = new KuimiClass(this, Type.getObjectType("java/lang/CharSequence"), Opcodes.ACC_INTERFACE | Opcodes.ACC_PUBLIC, null, javaLangObject, null);
            bootstrapPool.put(javaLangCharSequence);
            var javaLangString = new KuimiClass(this, Type.getObjectType("java/lang/String"), Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, null, javaLangObject, List.of(javaLangCharSequence)) {
                @Override
                public KuimiObject<?> allocateNewObject() {
                    return new KuimiString(KuimiAbstractVM.this, null);
                }
            };
            bootstrapPool.put(javaLangString);
            this.javaLangString = javaLangString;

            javaLangClassLoader = new KuimiClass(this, Type.getObjectType("java/lang/ClassLoader"), Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, null, javaLangObject, null);
            javaLangClassLoader.getMethodTable().addMethod(new KuimiMethod(javaLangClassLoader, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getSystemClassLoader", javaLangClassLoader, List.of()) {
                KuimiObject<?> execute(KuimiVM vm, StackTrace st) {
                    KuimiObject<ClassLoader> object = new KuimiObject<>(javaLangClassLoader);
                    object.setDelegateInstance(ClassLoader.getSystemClassLoader());
                    return object;
                }
            });
            javaLangClassLoader.getMethodTable().addMethod(new KuimiMethod(javaLangClassLoader, Opcodes.ACC_PUBLIC, "loadClass", javaLangClass, List.of(javaLangString)) {
                KuimiObject<?> execute(KuimiVM vm, StackTrace st, KuimiObject<ClassLoader> kuimiObject, KuimiObject name) throws ClassNotFoundException {
                    var obj = new KuimiObject<>(javaLangClass);
                    System.out.println("!LoadClass: " + name.getDelegateInstance());
                    obj.setDelegateInstance(kuimiObject.getDelegateInstance().loadClass((String) name.getDelegateInstance()));
                    return obj;
                }
            });
            bootstrapPool.put(javaLangClassLoader);

            var javaIoSerializable = new KuimiClass(this, Type.getObjectType("java/io/Serializable"), Opcodes.ACC_INTERFACE | Opcodes.ACC_PUBLIC, null, javaLangObject, null);
            var javaLangComparable = new KuimiClass(this, Type.getObjectType("java/lang/Comparable"), Opcodes.ACC_INTERFACE | Opcodes.ACC_PUBLIC, null, javaLangObject, null);
            bootstrapPool.put(javaIoSerializable);
            bootstrapPool.put(javaLangComparable);

            KuimiClass javaLangThrowable = new KuimiClass(this, Type.getObjectType("java/lang/Throwable"), Opcodes.ACC_PUBLIC, null, javaLangObject, List.of(javaIoSerializable));
            KuimiClass javaLangException = new KuimiClass(this, Type.getObjectType("java/lang/Exception"), Opcodes.ACC_PUBLIC, null, javaLangThrowable, null);
            KuimiClass javaLangError = new KuimiClass(this, Type.getObjectType("java/lang/Error"), Opcodes.ACC_PUBLIC, null, javaLangThrowable, null);
            KuimiClass javaLangRuntimeException = new KuimiClass(this, Type.getObjectType("java/lang/RuntimeException"), Opcodes.ACC_PUBLIC, null, javaLangException, null);
            KuimiClass javaLangNullPointerException = new KuimiClass(this, Type.getObjectType("java/lang/NullPointerException"), Opcodes.ACC_PUBLIC, null, javaLangRuntimeException, null);
            KuimiClass javaLangLinkageError = new KuimiClass(this, Type.getObjectType("java/lang/LinkageError"), Opcodes.ACC_PUBLIC, null, javaLangError, null);
            KuimiClass javaLangNoClassDefFoundError = new KuimiClass(this, Type.getObjectType("java/lang/NoClassDefFoundError"), Opcodes.ACC_PUBLIC, null, javaLangLinkageError, null);
            KuimiClass javaLangReflectiveOperationException = new KuimiClass(this, Type.getObjectType("java/lang/ReflectiveOperationException"), Opcodes.ACC_PUBLIC, null, javaLangException, null);
            KuimiClass javaLangClassNotFoundException = new KuimiClass(this, Type.getObjectType("java/lang/ClassNotFoundException"), Opcodes.ACC_PUBLIC, null, javaLangReflectiveOperationException, null);

            bootstrapPool.put(javaLangThrowable);
            bootstrapPool.put(javaLangException);
            bootstrapPool.put(javaLangError);
            bootstrapPool.put(javaLangRuntimeException);
            bootstrapPool.put(javaLangNullPointerException);
            bootstrapPool.put(javaLangLinkageError);
            bootstrapPool.put(javaLangNoClassDefFoundError);
            bootstrapPool.put(javaLangReflectiveOperationException);
            bootstrapPool.put(javaLangClassNotFoundException);


            javaLangCharSequence.getMethodTable().addMethod(new KuimiMethod(javaLangCharSequence, Opcodes.ACC_ABSTRACT | Opcodes.ACC_PUBLIC, "length", getPrimitiveClass(Type.INT_TYPE), List.of()));
            javaLangCharSequence.getMethodTable().addMethod(new KuimiMethod(javaLangCharSequence, Opcodes.ACC_ABSTRACT | Opcodes.ACC_PUBLIC, "charAt", getPrimitiveClass(Type.CHAR_TYPE), List.of(getPrimitiveClass(Type.INT_TYPE))));
            javaLangCharSequence.getMethodTable().addMethod(new KuimiMethod(javaLangCharSequence, Opcodes.ACC_ABSTRACT | Opcodes.ACC_PUBLIC, "subSequence", javaLangCharSequence, List.of(getPrimitiveClass(Type.INT_TYPE), getPrimitiveClass(Type.INT_TYPE))));


            javaLangObject.getMethodTable().addMethod(new KuimiMethod(javaLangObject, Opcodes.ACC_PUBLIC, "toString", javaLangString, List.of()) {
                KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz) {
                    var toStrx = thiz.toString();
                    if (toStrx != null) {
                        return new KuimiString(vm, toStrx);
                    }
                    return null;
                }
            });
            javaLangObject.getMethodTable().addMethod(new KuimiMethod(javaLangObject, Opcodes.ACC_PUBLIC, "hashCode", getPrimitiveClass(Type.INT_TYPE), List.of()) {
                int execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz) {
                    return thiz.hashCode();
                }
            });

            {
                var objInit = javaLangObject.getMethodTable()
                        .addMethod(new KuimiMethod(javaLangObject, Opcodes.ACC_PUBLIC, "<init>", getPrimitiveClass(Type.VOID_TYPE), List.of()));
                objInit.attachImplementation(MethodHandles.empty(objInit.flattenMethodHandleType()));
            }
            {
                var javaLangRunnable = new KuimiClass(this, Type.getObjectType("java/lang/Runnable"), Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, null, javaLangObject, null);
                javaLangRunnable.getMethodTable().addMethod(new KuimiMethod(javaLangRunnable, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "run", getPrimitiveClass(Type.VOID_TYPE), List.of()));
            }


            javaLangString.getMethodTable().addMethod(new KuimiMethod(javaLangString, Opcodes.ACC_PUBLIC, "<init>", getPrimitiveClass(Type.VOID_TYPE), List.of(getPrimitiveClass(Type.BYTE_TYPE).arrayType(), javaLangString)) {
                void execute(KuimiVM vm, StackTrace st, KuimiObject<?> thiz, KuimiObject<?> barr, KuimiObject<?> charset) throws Throwable {
                    ((KuimiString) thiz).forceChangeValue(
                            new String((byte[]) barr.getDelegateInstance(), charset.toString())
                    );
                }
            });
            javaLangString.getMethodTable().addMethod(new KuimiMethod(javaLangString, Opcodes.ACC_PUBLIC, "<init>", getPrimitiveClass(Type.VOID_TYPE), List.of(getPrimitiveClass(Type.CHAR_TYPE).arrayType())) {
                void execute(KuimiVM vm, StackTrace st, KuimiObject<?> thiz, KuimiObject<?> arr) throws Throwable {
                    ((KuimiString) thiz).forceChangeValue(
                            new String((char[]) arr.getDelegateInstance())
                    );
                }
            });


            { // stub field
                // javaLangClass.getFieldTable().addField(new KuimiField(javaLangClass, Opcodes.ACC_PRIVATE, "stub1", getPrimitiveClass(Type.INT_TYPE)));
                javaLangClass.getFieldTable().addField(new KuimiField(javaLangClass, Opcodes.ACC_PRIVATE, "classLoader", javaLangObject));
                // javaLangClass.getFieldTable().addField(new KuimiField(javaLangClass, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "CONST_0", getPrimitiveClass(Type.INT_TYPE)));
                // javaLangClass.getFieldTable().addField(new KuimiField(javaLangClass, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "CONST_1", javaLangObject));
            }

            { // collections
                var iterator = new KuimiClass(this, Type.getType(Iterator.class), Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE, null, javaLangObject, null);
                bootstrapPool.put(iterator);
                iterator.getMethodTable().addMethod(new KuimiMethod(iterator, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "hasNext", getPrimitiveClass(Type.VOID_TYPE), null) {
                    boolean execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof Iterator<?> itr) return itr.hasNext();
                        throw new UnsupportedOperationException();
                    }
                });
                iterator.getMethodTable().addMethod(new KuimiMethod(iterator, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "next", javaLangObject, null) {
                    KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof Iterator<?> itr) {
                            var nxt = itr.next();
                            if (nxt == null) return null;
                            if (nxt instanceof KuimiObject<?>) return (KuimiObject<?>) nxt;
                            if (nxt instanceof String) return new KuimiString(vm, nxt.toString());
                            throw new UnsupportedOperationException(nxt + " <" + nxt.getClass() + ">");
                        }
                        throw new UnsupportedOperationException();
                    }
                });
                iterator.getMethodTable().addMethod(new KuimiMethod(iterator, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "remove", getPrimitiveClass(Type.VOID_TYPE), null) {
                    void execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof Iterator<?> itr) {
                            itr.remove();
                            return;
                        }
                        throw new UnsupportedOperationException();
                    }
                });

                var iterable = new KuimiClass(this, Type.getType(Iterable.class), Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE, null, javaLangObject, null);
                bootstrapPool.put(iterable);
                iterable.getMethodTable().addMethod(new KuimiMethod(iterable, Opcodes.ACC_PUBLIC, "iterator", iterator, List.of()) {
                    KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof Iterable<?>) {
                            var rsp = new KuimiObject<>(iterator);
                            rsp.setDelegateInstance(((Iterable<?>) d).iterator());
                            return rsp;
                        }
                        throw new UnsupportedOperationException();
                    }
                });

                var collection = new KuimiClass(this, Type.getType(Collection.class), Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE, null, javaLangObject, List.of(iterable));
                bootstrapPool.put(collection);
                collection.getMethodTable().addMethod(new KuimiMethod(collection, Opcodes.ACC_PUBLIC, "size", getPrimitiveClass(Type.INT_TYPE), null) {
                    int execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof Collection<?> itr) return itr.size();
                        throw new UnsupportedOperationException();
                    }
                });
                collection.getMethodTable().addMethod(new KuimiMethod(collection, Opcodes.ACC_PUBLIC, "isEmpty", getPrimitiveClass(Type.BOOLEAN_TYPE), null) {
                    boolean execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof Collection<?> itr) return itr.isEmpty();
                        throw new UnsupportedOperationException();
                    }
                });
                collection.getMethodTable().addMethod(new KuimiMethod(collection, Opcodes.ACC_PUBLIC, "contains", getPrimitiveClass(Type.INT_TYPE), List.of(javaLangObject)) {
                    boolean execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz, KuimiObject<?> other) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof Collection<?> itr) return itr.contains(other);
                        throw new UnsupportedOperationException();
                    }
                });
                collection.getMethodTable().addMethod(new KuimiMethod(collection, Opcodes.ACC_PUBLIC, "toArray", javaLangObject.arrayType(), null) {
                    KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz) {
                        var d = thiz.getDelegateInstance();

                        if (d instanceof Collection<?> itr) //noinspection SuspiciousToArrayCall
                            return new KuimiArrays.ArrayObject(javaLangObject.arrayType(), itr.toArray(new KuimiObject[0]));

                        throw new UnsupportedOperationException();
                    }
                });
                collection.getMethodTable().addMethod(new KuimiMethod(collection, Opcodes.ACC_PUBLIC, "add", getPrimitiveClass(Type.BOOLEAN_TYPE), List.of(javaLangObject)) {
                    boolean execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz, KuimiObject<?> other) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof Collection itr) return itr.add(other);
                        throw new UnsupportedOperationException();
                    }
                });
                collection.getMethodTable().addMethod(new KuimiMethod(collection, Opcodes.ACC_PUBLIC, "remove", getPrimitiveClass(Type.BOOLEAN_TYPE), List.of(javaLangObject)) {
                    boolean execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz, KuimiObject<?> other) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof Collection itr) return itr.remove(other);
                        throw new UnsupportedOperationException();
                    }
                });
                collection.getMethodTable().addMethod(new KuimiMethod(collection, Opcodes.ACC_PUBLIC, "clear", getPrimitiveClass(Type.VOID_TYPE), List.of()) {
                    void execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof Collection itr) {
                            itr.clear();
                            return;
                        }
                        throw new UnsupportedOperationException();
                    }
                });

                var set = new KuimiClass(this, Type.getType(Set.class), Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE, null, javaLangObject, List.of(collection));
                bootstrapPool.put(set);

                var list = new KuimiClass(this, Type.getType(List.class), Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE, null, javaLangObject, List.of(collection));
                list.getMethodTable().addMethod(new KuimiMethod(list, Opcodes.ACC_PUBLIC, "get", javaLangObject, List.of(getPrimitiveClass(Type.INT_TYPE))) {
                    KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz, int idx) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof List itr) {
                            return (KuimiObject<?>) itr.get(idx);
                        }
                        throw new UnsupportedOperationException();
                    }
                });
                list.getMethodTable().addMethod(new KuimiMethod(list, Opcodes.ACC_PUBLIC, "remove", javaLangObject, List.of(getPrimitiveClass(Type.INT_TYPE))) {
                    KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz, int idx) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof List itr) {
                            return (KuimiObject<?>) itr.remove(idx);
                        }
                        throw new UnsupportedOperationException();
                    }
                });
                list.getMethodTable().addMethod(new KuimiMethod(list, Opcodes.ACC_PUBLIC, "set", javaLangObject, List.of(getPrimitiveClass(Type.INT_TYPE), javaLangObject)) {
                    KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz, int idx, KuimiObject<?> val) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof List itr) {
                            return (KuimiObject<?>) itr.set(idx, val);
                        }
                        throw new UnsupportedOperationException();
                    }
                });
                list.getMethodTable().addMethod(new KuimiMethod(list, Opcodes.ACC_PUBLIC, "add", getPrimitiveClass(Type.VOID_TYPE), List.of(getPrimitiveClass(Type.INT_TYPE), javaLangObject)) {
                    void execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz, int idx, KuimiObject<?> val) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof List itr) {
                            itr.add(idx, val);
                            return;
                        }
                        throw new UnsupportedOperationException();
                    }
                });

                list.getMethodTable().addMethod(new KuimiMethod(list, Opcodes.ACC_PUBLIC, "indexOf", getPrimitiveClass(Type.INT_TYPE), List.of(javaLangObject)) {
                    int execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz, KuimiObject<?> val) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof List itr) {
                            return itr.indexOf(val);
                        }
                        throw new UnsupportedOperationException();
                    }
                });

                list.getMethodTable().addMethod(new KuimiMethod(list, Opcodes.ACC_PUBLIC, "lastIndexOf", getPrimitiveClass(Type.INT_TYPE), List.of(javaLangObject)) {
                    int execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz, KuimiObject<?> val) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof List itr) {
                            return itr.lastIndexOf(val);
                        }
                        throw new UnsupportedOperationException();
                    }
                });
                // todo: listIterator
                list.getMethodTable().addMethod(new KuimiMethod(list, Opcodes.ACC_PUBLIC, "subList", list, List.of(getPrimitiveClass(Type.INT_TYPE), getPrimitiveClass(Type.INT_TYPE))) {
                    KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz, int from, int end) {
                        var d = thiz.getDelegateInstance();
                        if (d instanceof List itr) {
                            var rsp = itr.subList(from, end);
                            var obj = new KuimiObject<>(list);
                            obj.setDelegateInstance(rsp);
                            return obj;
                        }
                        throw new UnsupportedOperationException();
                    }
                });


                var arrayList = new KuimiClass(this, Type.getType(ArrayList.class), Opcodes.ACC_PUBLIC, null, javaLangObject, List.of(list));
                bootstrapPool.put(arrayList);
                arrayList.getMethodTable().addMethod(new KuimiMethod(arrayList, Opcodes.ACC_PUBLIC, "<init>", getPrimitiveClass(Type.VOID_TYPE), List.of()) {
                    void execute(KuimiVM vm, StackTrace trace, KuimiObject thiz) {
                        thiz.setDelegateInstance(new ArrayList());
                    }
                });
                arrayList.getMethodTable().addMethod(new KuimiMethod(arrayList, Opcodes.ACC_PUBLIC, "<init>", getPrimitiveClass(Type.VOID_TYPE), List.of(getPrimitiveClass(Type.INT_TYPE))) {
                    void execute(KuimiVM vm, StackTrace trace, KuimiObject thiz, int size) {
                        thiz.setDelegateInstance(new ArrayList(size));
                    }
                });

                arrayList.getMethodTable().addMethod(new KuimiMethod(arrayList, Opcodes.ACC_PUBLIC, "<init>", getPrimitiveClass(Type.VOID_TYPE), List.of(collection)) {
                    void execute(KuimiVM vm, StackTrace trace, KuimiObject thiz, KuimiObject mirror) {
                        thiz.setDelegateInstance(new ArrayList((Collection) mirror.getDelegateInstance()));
                    }
                });
            }
        }
    }


    @Override
    public KuimiClass getBaseClass() {
        return javaLangObject;
    }

    @Override
    public KuimiClass getClassClass() {
        return javaLangClass;
    }

    @Override
    public KuimiClass getStringClass() {
        return javaLangString;
    }

    @Override
    public KuimiClass getPrimitiveClass(Type type) {
        var sort = type.getSort();
        if (sort == Type.ARRAY || sort == Type.OBJECT) {
            throw new IllegalArgumentException(type + " not a primitive class");
        }


        return bootstrapPool.resolve(type.getClassName());
    }

    @Override
    public long objectPointerSize() {
        return Long.BYTES;
    }

    @Override
    public KuimiClass resolveClass(Type type) {
        if (type.getSort() == Type.ARRAY) {
            var dep = type.getElementType();
            var dsize = type.getDimensions();
            KuimiClass dclass;
            if (dep.getSort() == Type.OBJECT) {
                dclass = resolveClassImpl(dep);
            } else {
                dclass = getPrimitiveClass(dep);
            }
            if (dclass == null) return null;
            while (dsize-- > 0) {
                dclass = dclass.arrayType();
            }
            return dclass;
        }
        if (type.getSort() == Type.OBJECT) {
            return resolveClassImpl(type);
        }
        return getPrimitiveClass(type);
    }

    protected KuimiClass resolveClassImpl(Type type) {
        return bootstrapPool.resolve(type.getClassName());
    }

    @Override
    public ObjectPool getGlobalPool() {
        return globalPool;
    }

    @Override
    public ObjectPool getWeakGlobalPool() {
        return weakGlobalPool;
    }

    @Override
    public KuimiObject<?> resolveObject(int ptr) {
        if (ptr == 0) return null;

        var pfix = ptr & ObjectPool.OBJECT_PREFIX;
        var rem = ptr & ~ObjectPool.OBJECT_PREFIX;

        if (pfix == ObjectPool.GLOBAL_OBJECT_PREFIX) {
            return globalPool.getObject(rem);
        }
        if (pfix == ObjectPool.LOCAL_OBJECT_PREFIX) {
            var tstack = THREAD_STACK_TRACE.get();
            if (tstack == null) return null;

            return tstack.resolve(ptr);
        }
        if (pfix == ObjectPool.WEAK_GLOBAL_OBJECT_PREFIX) {
            return weakGlobalPool.getObject(rem);
        }
        return null;
    }


    @Override
    public StackTrace getStackTrace() {
        return THREAD_STACK_TRACE.get();
    }

    @Override
    public void attachThread(StackTrace stackTrace) {
        Objects.requireNonNull(stackTrace, "stackTrace");
        var st = THREAD_STACK_TRACE.get();
        if (st == null) {
            THREAD_STACK_TRACE.set(stackTrace);
            return;
        }

        if (st.getLocalFramePoint() == -1 && st.getStackTracePoint() == -1) {
            THREAD_STACK_TRACE.set(stackTrace);
            return;
        }

        throw new IllegalStateException("Thread was already attached and cannot be re-attach now");
    }

    @Override
    public void detatchThread() {
        var st = THREAD_STACK_TRACE.get();
        if (st == null) {
            return;
        }

        if (st.getLocalFramePoint() == -1 && st.getStackTracePoint() == -1) {
            THREAD_STACK_TRACE.remove();
            return;
        }

        throw new IllegalStateException("Current thread is not detachable");
    }

    public ClassPool getBootstrapPool() {
        return bootstrapPool;
    }
}
