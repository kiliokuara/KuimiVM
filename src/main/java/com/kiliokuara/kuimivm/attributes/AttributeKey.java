package com.kiliokuara.kuimivm.attributes;

import java.util.function.Function;

public class AttributeKey<O, T> {
    private final String name;

    final Function<O, T> valueComputer;

    public AttributeKey(String name, Function<O, T> valueComputer) {
        this.name = name;
        this.valueComputer = valueComputer;
    }

    @Override
    public String toString() {
        return "Attribute[" + name + "]";
    }
}
