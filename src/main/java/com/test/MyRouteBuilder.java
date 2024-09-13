package com.test;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component()
public class MyRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("timer:x")
            .log("{{my.secret.password}}");
    }
}
