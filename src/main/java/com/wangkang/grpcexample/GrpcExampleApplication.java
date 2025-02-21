package com.wangkang.grpcexample;

import com.wangkang.grpcexample.grpc.helloworld.HelloWorldServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class GrpcExampleApplication {

    public static void main(String[] args) throws IOException {
        final HelloWorldServer server = new HelloWorldServer();
        server.start();
        SpringApplication.run(GrpcExampleApplication.class, args);
    }
}
