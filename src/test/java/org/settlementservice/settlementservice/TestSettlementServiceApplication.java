package org.settlementservice.settlementservice;

import org.springframework.boot.SpringApplication;

public class TestSettlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(SettlementServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
