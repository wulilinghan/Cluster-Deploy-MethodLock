package top.b0x0.methoddistributedlock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author ManJiis
 */
@SpringBootApplication
@EnableScheduling
public class MethodDistributedLockApplication {

    public static void main(String[] args) {
        SpringApplication.run(MethodDistributedLockApplication.class, args);
    }

}
