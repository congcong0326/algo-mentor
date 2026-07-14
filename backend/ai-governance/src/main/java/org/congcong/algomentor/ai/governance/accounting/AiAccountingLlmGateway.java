package org.congcong.algomentor.ai.governance.accounting;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

/** 对真实网关进行调用级台账包装，不改变 provider 的响应或异常语义。 */
public class AiAccountingLlmGateway implements LlmGateway {

  private final LlmGateway delegate;
  private final AiLlmCallAccountingService accountingService;

  public AiAccountingLlmGateway(LlmGateway delegate, AiLlmCallAccountingService accountingService) {
    this.delegate = delegate;
    this.accountingService = accountingService;
  }

  @Override
  public LlmCompletionResult complete(LlmCompletionRequest request) {
    AiLlmCallUsage call = accountingService.start(request);
    try {
      LlmCompletionResult result = delegate.complete(request);
      accountingService.complete(call, result);
      return result;
    } catch (RuntimeException exception) {
      accountingService.fail(call, exception);
      throw exception;
    }
  }

  @Override
  public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
    return subscriber -> {
      AiLlmCallUsage call = accountingService.start(request);
      AtomicBoolean terminal = new AtomicBoolean();
      AtomicReference<LlmUsage> lastUsage = new AtomicReference<>(LlmUsage.empty());
      AtomicReference<String> provider = new AtomicReference<>(call.provider());
      AtomicReference<String> model = new AtomicReference<>(call.model());
      try {
        delegate.stream(request).subscribe(new Flow.Subscriber<>() {
          @Override
          public void onSubscribe(Flow.Subscription subscription) {
            subscriber.onSubscribe(new Flow.Subscription() {
              @Override
              public void request(long count) {
                subscription.request(count);
              }

              @Override
              public void cancel() {
                subscription.cancel();
                settleCancelled();
              }
            });
          }

          @Override
          public void onNext(LlmStreamEvent event) {
            capture(event);
            if (event instanceof LlmStreamEvent.Error error) {
              settleFailed(error.error());
            }
            subscriber.onNext(event);
          }

          @Override
          public void onError(Throwable throwable) {
            settleFailed(throwable);
            subscriber.onError(throwable);
          }

          @Override
          public void onComplete() {
            if (terminal.compareAndSet(false, true)) {
              accountingService.complete(call, provider.get(), model.get(), lastUsage.get());
            }
            subscriber.onComplete();
          }

          private void capture(LlmStreamEvent event) {
            if (event instanceof LlmStreamEvent.MessageStart start) {
              provider.set(start.provider().value());
              model.set(start.model().value());
            } else if (event instanceof LlmStreamEvent.Usage usage) {
              // Provider adapter promises a latest cumulative snapshot, not a delta to add.
              lastUsage.set(usage.usage());
            }
          }

          private void settleFailed(Throwable throwable) {
            if (terminal.compareAndSet(false, true)) {
              accountingService.fail(call, throwable);
            }
          }

          private void settleCancelled() {
            if (terminal.compareAndSet(false, true)) {
              accountingService.cancel(call, provider.get(), model.get(), lastUsage.get());
            }
          }
        });
      } catch (RuntimeException exception) {
        if (terminal.compareAndSet(false, true)) {
          accountingService.fail(call, exception);
        }
        subscriber.onSubscribe(new EmptySubscription());
        subscriber.onError(exception);
      }
    };
  }

  private static final class EmptySubscription implements Flow.Subscription {

    @Override
    public void request(long count) {
    }

    @Override
    public void cancel() {
    }
  }
}
