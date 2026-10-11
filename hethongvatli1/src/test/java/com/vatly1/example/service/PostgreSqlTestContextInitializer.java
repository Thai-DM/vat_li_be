package com.vatly1.example.service;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostgreSqlTestContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String envUrl = System.getenv("PG_TEST_DB_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            return;
        }

        File localYml = new File("src/main/resources/application-local.yml");
        if (!localYml.exists()) {
            localYml = new File("hethongvatli1/src/main/resources/application-local.yml");
        }
        if (localYml.exists()) {
            try {
                String content = Files.readString(localYml.toPath());
                Matcher mUrl = Pattern.compile("url:\\s*(.+)").matcher(content);
                Matcher mUser = Pattern.compile("username:\\s*(.+)").matcher(content);
                Matcher mPass = Pattern.compile("password:\\s*(.+)").matcher(content);

                Map<String, Object> props = new HashMap<>();
                if (mUrl.find()) {
                    props.put("spring.datasource.url", mUrl.group(1).trim());
                }
                if (mUser.find()) {
                    props.put("spring.datasource.username", mUser.group(1).trim());
                }
                if (mPass.find()) {
                    props.put("spring.datasource.password", mPass.group(1).trim());
                }

                if (!props.isEmpty()) {
                    context.getEnvironment().getPropertySources().addFirst(
                            new MapPropertySource("applicationLocalTestProperties", props)
                    );
                }
            } catch (Exception ignored) {
            }
        }
    }
}
