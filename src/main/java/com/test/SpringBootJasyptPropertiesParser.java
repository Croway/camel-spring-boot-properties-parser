package com.test;

import org.apache.camel.component.jasypt.JasyptPropertiesParser;
import org.apache.camel.component.jasypt.springboot.EncryptablePropertySourcesPlaceholderConfigurer;
import org.apache.camel.component.jasypt.springboot.JasyptEncryptedPropertiesAutoconfiguration;
import org.apache.camel.component.jasypt.springboot.JasyptEncryptedPropertiesConfiguration;
import org.apache.camel.component.properties.PropertiesParser;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.config.EnvironmentStringPBEConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.Properties;

public class SpringBootJasyptPropertiesParser implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(SpringBootJasyptPropertiesParser.class);

    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();

        final Properties props = new Properties();

        String enabled = environment.getProperty("camel.component.jasypt.decrypt.properties.enabled");

        if (Boolean.parseBoolean(enabled)) {
            // Manual Autoconfigure jasypt component
            JasyptEncryptedPropertiesAutoconfiguration jasyptEncryptedPropertiesAutoconfiguration = new JasyptEncryptedPropertiesAutoconfiguration();
            JasyptEncryptedPropertiesConfiguration jasyptEncryptedPropertiesConfiguration =
                jasyptEncryptedPropertiesAutoconfiguration.JasyptEncryptedPropertiesAutoconfiguration(event.getEnvironment());
            // this code is missing from Camel Spring Boot
            String password = jasyptEncryptedPropertiesConfiguration.getPassword();
            if (password.startsWith("sysenv:")) {
                password = System.getenv(StringHelper.after(password, "sysenv:"));
            }
            if (ObjectHelper.isNotEmpty(password) && password.startsWith("sys:")) {
                password = System.getProperty(StringHelper.after(password, "sys:"));
            }
            jasyptEncryptedPropertiesConfiguration.setPassword(password);
            EnvironmentStringPBEConfig environmentStringPBEConfig =
                jasyptEncryptedPropertiesAutoconfiguration.environmentVariablesConfiguration(jasyptEncryptedPropertiesConfiguration);
            StringEncryptor stringEncryptor =
                jasyptEncryptedPropertiesAutoconfiguration.stringEncryptor(environmentStringPBEConfig);
            EncryptablePropertySourcesPlaceholderConfigurer encryptablePropertySourcesPlaceholderConfigurer =
                jasyptEncryptedPropertiesAutoconfiguration.propertyConfigurer(stringEncryptor);
            PropertiesParser propertiesParser = jasyptEncryptedPropertiesAutoconfiguration.encryptedPropertiesParser(environment,
                stringEncryptor,
                environment);

            for (PropertySource mutablePropertySources : event.getEnvironment().getPropertySources()) {
                if (mutablePropertySources instanceof MapPropertySource mapPropertySource) {
                    mapPropertySource.getSource().forEach((key, value) -> {
                        if (value instanceof OriginTrackedValue originTrackedValue &&
                            originTrackedValue.getValue() instanceof String stringValue &&
                            stringValue.startsWith(JasyptPropertiesParser.JASYPT_PREFIX_TOKEN) &&
                            stringValue.endsWith(JasyptPropertiesParser.JASYPT_SUFFIX_TOKEN)) {

                            LOG.debug("decrypting and overriding property {}", key);
                            props.put(key, propertiesParser.parseProperty(key.toString(), stringValue, null));
                        }
                    });
                }
            }

            environment.getPropertySources().addFirst(new PropertiesPropertySource("overridden-camel-jasypt-properties", props));
        }
    }
}
