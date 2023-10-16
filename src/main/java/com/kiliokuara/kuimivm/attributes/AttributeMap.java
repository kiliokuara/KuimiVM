package com.kiliokuara.kuimivm.attributes;

import java.util.concurrent.ConcurrentHashMap;

public class AttributeMap<O> extends ConcurrentHashMap<AttributeKey<? super O, ?>, Object> {
    private final O owner;

    public AttributeMap(O owner) {
        this.owner = owner;
    }

    public <T> T attribute(AttributeKey<? super O, T> key) {
        //noinspection unchecked
        return (T) computeIfAbsent(key, k -> key.valueComputer.apply(owner));
    }
}
