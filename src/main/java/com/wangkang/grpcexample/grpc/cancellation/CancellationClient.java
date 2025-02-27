/*
 * Copyright 2023 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wangkang.grpcexample.grpc.cancellation;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.*;
import io.grpc.Context.CancellableContext;
import io.grpc.examples.echo.EchoGrpc;
import io.grpc.examples.echo.EchoRequest;
import io.grpc.examples.echo.EchoResponse;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.TimeUnit;

/**
 * A client that cancels RPCs to an Echo server.
 */
public class CancellationClient {
  private final Channel channel;

  public CancellationClient(Channel channel) {
    this.channel = channel;
  }

  private void demonstrateCancellation() throws Exception {
    //阻塞式请求。并且没有cancel，正常响应
    echoBlocking("I'M A BLOCKING CLIENT! HEAR ME ROAR!");

    // io.grpc.Context can be used to cancel RPCs using any of the stubs. It is the only way to
    // cancel blocking stub RPCs. io.grpc.Context is a general-purpose alternative to thread
    // interruption and can be used outside of gRPC, like to coordinate within your application.
    //
    // CancellableContext must always be cancelled or closed at the end of its lifetime, otherwise
    // it could "leak" memory.

    //1、阻塞式请求，只能通过 context.cancel 取消。这里在请求500ms后，取消了请求（服务端有个1s的等待时间）。这个时候服务端也会收到取消请求
    try (CancellableContext context = Context.current().withCancellation()) {
      new Thread(() -> {
        try {
          Thread.sleep(500); // Do some work
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
        // Cancellation reasons are never sent to the server. But they are echoed back to the
        // client as the RPC failure reason.
        context.cancel(new RuntimeException("Oops. Messed that up, let me try again"));
      }).start();

      // context.run() attaches the context to this thread for gRPC to observe. It also restores
      // the previous context before returning.
      //请求报错：Status{code=CANCELLED, description=Context cancelled, cause=java.lang.RuntimeException: Oops. Messed that up, let me try again
      context.run(() -> echoBlocking("RAAWRR haha lol hehe AWWRR GRRR"));
    }

    // Futures cancelled with interruption cancel the RPC.
    //2、newFutureStub。调用 future.cancel 取消任务。看效果
    //接受到 onFail。报错：Status{code=UNKNOWN, description=null, cause=java.util.concurrent.CancellationException: Task was cancelled
    //异步有2种模式，1）newFutureStub 2）newStub。第一种的好处是只需处理成功的结果和失败的结果，并且可以提前取消请求。第二种的好处是方法简单，但是要处理3个方法
    ListenableFuture<EchoResponse> future = echoFuture("Future clie*cough*nt was here!");
    Thread.sleep(500); // Do some work
    // We realize we really don't want to hear that echo.
    future.cancel(true);
    Thread.sleep(100); // Make logs more obvious. Cancel is async

    //双向流测试。正常测试，正常关闭
    ClientCallStreamObserver<EchoRequest> reqCallObserver = echoAsync("Testing, testing, 1, 2, 3");
    reqCallObserver.onCompleted();
    Thread.sleep(500); // Make logs more obvious. Wait for completion

    // Async's onError() will cancel. But the method can't be called concurrently with other calls
    // on the StreamObserver. If you need thread-safety, use CancellableContext as above.
    StreamObserver<EchoRequest> reqObserver = echoAsync("... async client... is the... best...");
    try {
      Thread.sleep(500); // Do some work
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
    // Since reqObserver.onCompleted() hasn't been called, we can use onError().
    reqObserver.onError(new RuntimeException("That was weak..."));
    Thread.sleep(100); // Make logs more obvious. Cancel is async

    // Async's cancel() will cancel. Also may not be called concurrently with other calls on the
    // StreamObserver.
    reqCallObserver = echoAsync("Async client or bust!");
    reqCallObserver.onCompleted();
    try {
      Thread.sleep(250); // Do some work
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
    //服务端delay了400ms，所以这时候服务端还在发
    // Since onCompleted() has been called, we can't use onError(). It is safe to use cancel()
    // regardless of onCompleted() being called.
    reqCallObserver.cancel("That's enough. I'm bored", null);
    Thread.sleep(100); // Make logs more obvious. Cancel is async
  }

  /** Say hello to server, just like in helloworld example. */
  public void echoBlocking(String text) {
    System.out.println("\nYelling: " + text);
    EchoRequest request = EchoRequest.newBuilder().setMessage(text).build();
    EchoResponse response;
    try {
      response = EchoGrpc.newBlockingStub(channel).unaryEcho(request);
    } catch (StatusRuntimeException e) {
      System.out.println("RPC failed: " + e.getStatus());
      return;
    }
    System.out.println("Echo: " + response.getMessage());
  }

  /** Say hello to the server, but using future API. */
  public ListenableFuture<EchoResponse> echoFuture(String text) {
    System.out.println("\nYelling: " + text);
    EchoRequest request = EchoRequest.newBuilder().setMessage(text).build();
    ListenableFuture<EchoResponse> future = EchoGrpc.newFutureStub(channel).unaryEcho(request);
    Futures.addCallback(future, new FutureCallback<EchoResponse>() {
      @Override
      public void onSuccess(EchoResponse response) {
        System.out.println("Echo: " + response.getMessage());
      }

      @Override
      public void onFailure(Throwable t) {
        System.out.println("RPC failed: " + Status.fromThrowable(t));
      }
    }, MoreExecutors.directExecutor());

    EchoGrpc.newStub(channel).unaryEcho(request, new StreamObserver<EchoResponse>() {
      @Override
      public void onNext(EchoResponse value) {

      }

      @Override
      public void onError(Throwable t) {

      }

      @Override
      public void onCompleted() {

      }
    });

    return future;
  }

  /** Say hello to the server, but using async API and cancelling. */
  public ClientCallStreamObserver<EchoRequest> echoAsync(String text) {
    System.out.println("\nYelling: " + text);
    EchoRequest request = EchoRequest.newBuilder().setMessage(text).build();

    // Client-streaming and bidirectional RPCs can cast the returned StreamObserver to
    // ClientCallStreamObserver.
    //
    // Unary and server-streaming stub methods don't return a StreamObserver. For such RPCs, you can
    // use ClientResponseObserver to get the ClientCallStreamObserver. For example:
    //     EchoGrpc.newStub(channel).unaryEcho(new ClientResponseObserver<EchoResponse>() {...});
    // Since ClientCallStreamObserver.cancel() is not thread-safe, it isn't safe to call from
    // another thread until the RPC stub method (e.g., unaryEcho()) returns.
    ClientCallStreamObserver<EchoRequest> reqObserver = (ClientCallStreamObserver<EchoRequest>)
        EchoGrpc.newStub(channel).bidirectionalStreamingEcho(new StreamObserver<EchoResponse>() {
      @Override
      public void onNext(EchoResponse response) {
        System.out.println("Echo: " + response.getMessage());
      }

      @Override
      public void onCompleted() {
        System.out.println("RPC completed");
      }

      @Override
      public void onError(Throwable t) {
        System.out.println("RPC failed: " + Status.fromThrowable(t));
      }
    });

    reqObserver.onNext(request);
    return reqObserver;
  }

  /**
   * Cancel RPCs to a server. If provided, the first element of {@code args} is the target server.
   */
  public static void main(String[] args) throws Exception {
    String target = "localhost:50051";
    if (args.length > 0) {
      if ("--help".equals(args[0])) {
        System.err.println("Usage: [target]");
        System.err.println("");
        System.err.println("  target  The server to connect to. Defaults to " + target);
        System.exit(1);
      }
      target = args[0];
    }

    ManagedChannel channel = Grpc.newChannelBuilder(target, InsecureChannelCredentials.create())
        .build();
    try {
      CancellationClient client = new CancellationClient(channel);
      client.demonstrateCancellation();
    } finally {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}
