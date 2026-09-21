package com.kury.spriteschat;

public class TestRunner {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("com.kury.spriteschat.SpritesChatTest");
        Object instance = clazz.getDeclaredConstructor().newInstance();
        int passed = 0;
        int failed = 0;
        for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
            if (m.isAnnotationPresent(org.junit.jupiter.api.Test.class)) {
                try {
                    m.invoke(instance);
                    System.out.println("  [PASS] " + m.getName());
                    passed++;
                } catch (java.lang.reflect.InvocationTargetException e) {
                    System.err.println("  [FAIL] " + m.getName() + " -> " + e.getCause());
                    e.getCause().printStackTrace();
                    failed++;
                }
            }
        }
        System.out.println("==================================================");
        System.out.println("TEST RESULTS: " + passed + " PASSED, " + failed + " FAILED.");
        System.out.println("==================================================");
        if (failed > 0) System.exit(1);
    }
}