package org.remus.resticexplorer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ResticExplorerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResticExplorerApplication.class, args);
    }
}
