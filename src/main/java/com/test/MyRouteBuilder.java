package com.test;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jasypt.JasyptPropertiesParser;
import org.apache.camel.component.properties.PropertiesComponent;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.jasypt.iv.RandomIvGenerator;
import org.springframework.stereotype.Component;

@Component()
public class MyRouteBuilder extends RouteBuilder {

    final String pbePWD = "secret";

    private StringEncryptor configureEncryptor() {
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPasswordCharArray(pbePWD.toCharArray());
        config.setAlgorithm("PBEWithHmacSHA512AndAES_256");
        config.setIvGenerator(new RandomIvGenerator());
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setConfig(config);

        return encryptor;
    }

    private JasyptPropertiesParser jasyptParser(StringEncryptor stringEncryptor) throws Exception {
        JasyptPropertiesParser jasypt = new JasyptPropertiesParser();
        jasypt.setEncryptor(stringEncryptor);
        jasypt.setPassword(pbePWD);

        return jasypt;
    }

    private PropertiesComponent getPropertiesComponent() {
        PropertiesComponent pc = new PropertiesComponent();
        pc.setLocation("classpath:jasypt/mysecrets.properties");
        pc.setNestedPlaceholder(true);

        return pc;
    }

    @Override
    public void configure() throws Exception {

        from("timer:x")
            .log("{{my.secret.password}}");

        StringEncryptor encryptor = configureEncryptor();

        JasyptPropertiesParser jasyptParser = jasyptParser(encryptor);
        PropertiesComponent propertiesComponent = getPropertiesComponent();
        propertiesComponent.setPropertiesParser(jasyptParser);
        getCamelContext().getRegistry().bind("encryptorType", encryptor);
        getCamelContext().setPropertiesComponent(propertiesComponent);
        restConfiguration().component("servlet");
        rest().post().to("direct:post");
        from("direct:post").log("${header.testName}").toD("direct:${header.testName}");
        rest().post("encryptAndDecrypt").produces("text/plain").to("direct:encrypt");
        rest().post("decrypt").produces("text/plain").to("direct:decrypt");
        from("direct:encryptAndDecrypt").routeId("encryptor").log("${body}").process(exchange -> {
            String body = exchange.getIn().getBody(String.class);
            StringEncryptor stringEncryptor = (StringEncryptor) exchange.getContext().getRegistry().lookupByName("encryptorType");
            exchange.getIn().setBody(String.format("%s(%s)", "ENC", stringEncryptor.encrypt(body)));
        }).log("${body}");
        from("direct:decrypt").routeId("decryptor").log("${body}").process(exchange -> {
            String body = exchange.getIn().getBody(String.class);
            StringEncryptor stringEncryptor = (StringEncryptor) exchange.getContext().getRegistry().lookupByName("encryptorType");
            exchange.getIn().setBody(String.format("%s(%s)", "DEC", stringEncryptor.decrypt(body)));
        }).log("${body}");
        from("direct:parseProperties").routeId("propertyProcessor").process(exchange -> {
            String key = exchange.getIn().getBody(String.class);
            String encryptedSecret = (String) jasyptParser.getPropertiesComponent().loadProperties().get(key);
            String result = jasyptParser.parseProperty(key, encryptedSecret, null);
            exchange.getIn().setBody(String.format("%s(%s)", "PARSED", result));
        }).log("${body}");
        from("direct:parseFromPlaceholder").routeId("placeholderResolver").to("{{password.result}}");
        from("direct:JasyptCamelTest.2024").routeId("jasyptRoute").log("unlocked camel route: ${body}");
    }
}
