package com.kgqa.util;

import java.util.UUID;

public class UUIDUtil {

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String generateShort() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
