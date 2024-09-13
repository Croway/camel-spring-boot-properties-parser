package com.test;

import org.apache.camel.component.jasypt.JasyptPropertiesParser;
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

    private JasyptPropertiesParser jasyptParser(String password,
        String algorithm,
        String randomIvGeneratorAlgorithm,
        String randomSaltGeneratorAlgorithm) {

        JasyptPropertiesParser jasypt = new JasyptPropertiesParser();
        jasypt.setPassword(password);
        if (randomIvGeneratorAlgorithm != null) {
            jasypt.setRandomIvGeneratorAlgorithm(randomIvGeneratorAlgorithm);
        }
        if (randomSaltGeneratorAlgorithm != null) {
            jasypt.setRandomSaltGeneratorAlgorithm(randomSaltGeneratorAlgorithm);
        }
        if (algorithm != null) {
            jasypt.setAlgorithm(algorithm);
        }

        return jasypt;
    }

    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();

        // Autoconfigure jasypt component
        String password = event.getEnvironment().getProperty("camel.component.jasypt.password");
        String algorithm = environment.getProperty("camel.component.jasypt.algorithm");
        String randomIvGeneratorAlgorithm = event.getEnvironment().getProperty("camel.component.jasypt.random-iv-generator-algorithm");
        String randomSaltGeneratorAlgorithm = environment.getProperty("camel.component.jasypt.random-salt-generator-algorithm");

        final Properties props = new Properties();

        final JasyptPropertiesParser jasyptPropertiesParser =
            jasyptParser(password, algorithm, randomIvGeneratorAlgorithm, randomSaltGeneratorAlgorithm);

        for (PropertySource mutablePropertySources : event.getEnvironment().getPropertySources()) {
            if (mutablePropertySources instanceof MapPropertySource mapPropertySource) {
                mapPropertySource.getSource().forEach((key, value) -> {
                    if (value instanceof OriginTrackedValue originTrackedValue &&
                        originTrackedValue.getValue() instanceof String stringValue &&
                        stringValue.startsWith(JasyptPropertiesParser.JASYPT_PREFIX_TOKEN) &&
                        stringValue.endsWith(JasyptPropertiesParser.JASYPT_SUFFIX_TOKEN)) {

                        LOG.debug("decrypting and overriding property {}", key);
                        props.put(key, jasyptPropertiesParser.parseProperty(key.toString(), stringValue, null));
                    }
                });
            }
        }

        environment.getPropertySources().addFirst(new PropertiesPropertySource("overridden-properties", props));
    }
}
