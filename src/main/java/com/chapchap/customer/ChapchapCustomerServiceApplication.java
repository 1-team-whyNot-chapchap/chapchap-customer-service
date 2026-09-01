package com.chapchap.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ChapchapCustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChapchapCustomerServiceApplication.class, args);
    }

}
