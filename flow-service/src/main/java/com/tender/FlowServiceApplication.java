package com.tender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FlowServiceApplication {

    public static void main(String[] args) {

        SpringApplication.run(FlowServiceApplication.class, args);
    }

}
