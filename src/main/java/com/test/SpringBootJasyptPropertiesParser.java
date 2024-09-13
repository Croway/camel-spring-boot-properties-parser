package com.test;

import org.apache.camel.component.jasypt.JasyptPropertiesParser;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.jasypt.iv.RandomIvGenerator;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.Properties;

@Configuration
public class SpringBootJasyptPropertiesParser implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private StringEncryptor configureEncryptor(String password, String algorithm) {
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPasswordCharArray(password.toCharArray());
        config.setAlgorithm(algorithm);
        config.setIvGenerator(new RandomIvGenerator());
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setConfig(config);

        return encryptor;
    }

    private org.apache.camel.component.jasypt.JasyptPropertiesParser jasyptParser(String password, String algorithm) throws Exception {
        org.apache.camel.component.jasypt.JasyptPropertiesParser jasypt = new org.apache.camel.component.jasypt.JasyptPropertiesParser();
        jasypt.setEncryptor(configureEncryptor(password, algorithm));
        jasypt.setPassword(password);

        return jasypt;
    }

    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();

        // Autoconfigure jasypt component
        String password = event.getEnvironment().getProperty("camel.component.jasypt.password");
        String algorithm = environment.getProperty("camel.component.jasypt.algorithm");

        final Properties props = new Properties();

        try {
            final JasyptPropertiesParser jasyptPropertiesParser = jasyptParser(password, algorithm);

            for (PropertySource mutablePropertySources : event.getEnvironment().getPropertySources()) {
                if (mutablePropertySources instanceof MapPropertySource mapPropertySource) {
                    mapPropertySource.getSource().forEach((key, value) -> {
                        if (value instanceof OriginTrackedValue originTrackedValue &&
                            originTrackedValue.getValue() instanceof String stringValue &&
                            stringValue.startsWith(JasyptPropertiesParser.JASYPT_PREFIX_TOKEN) &&
                            stringValue.endsWith(JasyptPropertiesParser.JASYPT_SUFFIX_TOKEN)) {

                            props.put(key, jasyptPropertiesParser.parseProperty(key.toString(), stringValue, null));
                        }
                    });
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Unable to configure a Camel JasyptPropertiesParser", e);
        }

        environment.getPropertySources().addFirst(new PropertiesPropertySource("myProps", props));
    }
}
