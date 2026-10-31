package org.openjcontroltower.config;

import org.openjcontroltower.domain.Models.*;
import org.openjcontroltower.infrastructure.DataStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Configuration
public class BootstrapTemplates {
    @Bean ApplicationRunner seedInterviewTemplates(DataStore store) {
        return args -> {
            if (store.specTemplates().isEmpty()) {
                try (var input = new ClassPathResource("spec-templates/default.md").getInputStream()) {
                    store.saveSpecTemplate(new SpecTemplate(UUID.randomUUID().toString(), "OpenJCockpit default",
                        new String(input.readAllBytes(), StandardCharsets.UTF_8), true));
                }
            }
            if (!store.templates().isEmpty()) return;
            store.saveTemplate(new Template(UUID.randomUUID().toString(),"Project discovery","PROJECT",List.of(
                new Question("goal","What is the measurable project outcome and who benefits?",true,2,"User groups and measurable objective"),
                new Question("scope","What is included and explicitly out of scope?",true,2,"Inclusions and non-goals"),
                new Question("architecture","Which architecture, integrations, technologies and constraints apply?",true,2,"Technical boundaries are concrete"),
                new Question("quality","Which ISO 25010, security and acceptance requirements are important?",true,2,"Verifiable quality requirements")
            )));
            store.saveTemplate(new Template(UUID.randomUUID().toString(),"Feature discovery","FEATURE",List.of(
                new Question("goal","Who needs this feature and which problem does it solve?",true,2,"Identified user and value"),
                new Question("behavior","What should happen in the happy path and relevant failures?",true,2,"Concrete examples and error paths"),
                new Question("acceptance","Which testable criteria determine that this feature is done?",true,2,"Measurable acceptance criteria"),
                new Question("non-goals","What must explicitly not change?",true,2,"Boundaries and backward compatibility")
            )));
        };
    }
}
