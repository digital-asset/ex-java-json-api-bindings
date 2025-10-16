package com.example;

public class Assert {
    public static void that(String description, boolean check) {
        if (!check) {
            throw new AssertionError("Failed assertion that " + description);
        }
    }
}
