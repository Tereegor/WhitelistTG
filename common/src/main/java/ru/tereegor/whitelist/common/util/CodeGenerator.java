package ru.tereegor.whitelist.common.util;

import java.security.SecureRandom;

public class CodeGenerator {
    
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    
    public static String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
    
    public static String generate() {
        return generate(6);
    }
    
    public static String generateFormatted() {
        return generate(3) + "-" + generate(3);
    }
}

