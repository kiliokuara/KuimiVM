package com.kiliokuara.kuimivm.utils;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

public abstract class DynamicClassAllocator {
    private final MethodHandles.Lookup lookup;
    private final Map<String, MethodHandles.Lookup> lookupMap;
    private final String cnprefix;

    protected final Map<String, Object> extArg;

    public DynamicClassAllocator(MethodHandles.Lookup lookup, String suffix) {
        this.lookup = lookup;
        lookupMap = new HashMap<>();
        extArg = new HashMap<>();
        this.cnprefix = lookup.lookupClass().getName().replace('.', '/') + "$$" + suffix + "$";

    }

    protected abstract byte[] computeBytecode(String input, String classname, Object extParam);

    protected Object extArg(MethodHandles.Lookup lookup, Object extParam) {
        return null;
    }

    public Object gExtArg(String key, Object extParam) {
        recompute(key, extParam);
        var mapKey = cnprefix + key;
        return extArg.get(mapKey);
    }

    public MethodHandles.Lookup recompute(String key, Object extParam) {
        var mapKey = cnprefix + key;
        var result = lookupMap.get(mapKey);
        if (result != null) {
            return result;
        }
        synchronized (this) {
            result = lookupMap.get(mapKey);
            if (result != null) return null;

            var code = computeBytecode(key, mapKey, extParam);
            MethodHandles.Lookup lkx;
            try {
                lkx = lookup.defineHiddenClass(code, false, MethodHandles.Lookup.ClassOption.NESTMATE);
                // lkx = MethodHandles.privateLookupIn(lookup.defineClass(code), lookup);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            lookupMap.put(mapKey, lkx);

            var etx = extArg(lkx, extParam);
            if (etx != null) {
                this.extArg.put(mapKey, etx);
            }

            return lkx;
        }
    }
}
