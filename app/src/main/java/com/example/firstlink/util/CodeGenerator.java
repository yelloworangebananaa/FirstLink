package com.example.firstlink.util;

import java.security.SecureRandom;

public class CodeGenerator {

    // Excludes easily confused characters (0/O, 1/I).
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodeGenerator() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Generates a code guaranteed to differ from {@code other}. */
    public static String generateDifferentFrom(String other) {
        String code = generate();
        while (code.equals(other)) {
            code = generate();
        }
        return code;
    }
}
