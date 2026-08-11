package org.congcong.algomentor.api.input;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Set;
import org.congcong.algomentor.api.learningplan.model.LearningPlanCreateDraftRequest;
import org.congcong.algomentor.api.config.UserInputLimitProperties;
import org.congcong.algomentor.api.practice.model.PracticeMessageRequest;
import org.congcong.algomentor.api.review.model.UpsertUserProblemNoteRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** 为需要保护的用户输入请求提供原始请求体大小检查。 */
@ControllerAdvice
public class UserInputRequestBodyAdvice extends RequestBodyAdviceAdapter {

  private static final Set<Class<?>> SUPPORTED_REQUEST_TYPES = Set.of(
      UpsertUserProblemNoteRequest.class,
      LearningPlanCreateDraftRequest.class,
      PracticeMessageRequest.class);

  private final UserInputValidator validator;

  public UserInputRequestBodyAdvice(ObjectMapper objectMapper, UserInputLimitProperties limits) {
    this.validator = new UserInputValidator(objectMapper, limits);
  }

  @Override
  public boolean supports(
      MethodParameter methodParameter,
      Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType
  ) {
    return SUPPORTED_REQUEST_TYPES.contains(methodParameter.getParameterType());
  }

  @Override
  public HttpInputMessage beforeBodyRead(
      HttpInputMessage inputMessage,
      MethodParameter parameter,
      Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType
  ) throws IOException {
    Class<?> requestType = parameter.getParameterType();
    long maxBytes = validator.requestMaxBytes(requestType);
    int readLimit = Math.toIntExact(maxBytes + 1);
    byte[] body = inputMessage.getBody().readNBytes(readLimit);
    if (body.length > maxBytes) {
      throw UserInputValidationException.limitExceeded("$request", maxBytes, "bytes", body.length);
    }
    validator.validateRaw(requestType, body);
    return new CachedHttpInputMessage(inputMessage.getHeaders(), body);
  }

  @Override
  public Object afterBodyRead(
      Object body,
      HttpInputMessage inputMessage,
      MethodParameter parameter,
      Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType
  ) {
    validator.validateParsed(body);
    return body;
  }

  private static final class CachedHttpInputMessage implements HttpInputMessage {

    private final HttpHeaders headers;
    private final byte[] body;

    private CachedHttpInputMessage(HttpHeaders headers, byte[] body) {
      this.headers = headers;
      this.body = body;
    }

    @Override
    public InputStream getBody() {
      return new ByteArrayInputStream(body);
    }

    @Override
    public HttpHeaders getHeaders() {
      return headers;
    }
  }
}
