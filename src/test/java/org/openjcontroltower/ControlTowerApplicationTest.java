package org.openjcontroltower;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import static org.mockito.Mockito.*;

class ControlTowerApplicationTest {
    @Test void mainDelegatesToSpringApplication() {
        try (MockedStatic<SpringApplication> spring=mockStatic(SpringApplication.class)) {
            String[] args={"--server.port=0"};
            ControlTowerApplication.main(args);
            spring.verify(()->SpringApplication.run(ControlTowerApplication.class,args));
        }
    }
}
