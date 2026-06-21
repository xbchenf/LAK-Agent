package com.lak.ai;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenPassword {
    public static void main(String[] args) {
        System.out.println(new BCryptPasswordEncoder().encode(args.length > 0 ? args[0] : "admin123"));
    }
}
