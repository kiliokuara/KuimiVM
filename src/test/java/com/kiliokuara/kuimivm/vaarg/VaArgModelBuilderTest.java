package com.kiliokuara.kuimivm.vaarg;

import org.junit.jupiter.api.Test;

import java.util.List;

public class VaArgModelBuilderTest {
    @Test
    void test() {
        var vv = VaArgModelBuilder.builder(List.of(
                ShortenType.DOUBLE,
                ShortenType.INT,
                ShortenType.FLOAT,
                ShortenType.INT,
                ShortenType.LONG,
                ShortenType.REF
        ));

        System.out.println(vv);
    }
}
