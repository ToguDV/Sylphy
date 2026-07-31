package com.togudv.sylphy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SylphyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SylphyApplication.class, args);
    }

}
