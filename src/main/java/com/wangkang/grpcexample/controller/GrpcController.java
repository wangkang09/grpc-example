package com.wangkang.grpcexample.controller;

import com.wangkang.grpcexample.grpc.helloworld.HelloWorldClient;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import jakarta.annotation.PostConstruct;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GrpcController {
    private HelloWorldClient grpcClient;

    @GetMapping("hello")
    public String hello(String hello) {
        grpcClient.greet(hello);
        return "ok";
    }

    @PostConstruct
    public void init() {
        String target = "localhost:50051";
        ManagedChannel channel = Grpc.newChannelBuilder(target, InsecureChannelCredentials.create())
                .build();
        grpcClient = new HelloWorldClient(channel);
    }
}
