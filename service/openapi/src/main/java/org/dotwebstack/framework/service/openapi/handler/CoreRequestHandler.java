package org.dotwebstack.framework.service.openapi.handler;

import static java.lang.String.format;
import static org.dotwebstack.framework.core.helpers.ExceptionHelper.invalidConfigurationException;
import static org.dotwebstack.framework.core.helpers.ExceptionHelper.unsupportedOperationException;
import static org.dotwebstack.framework.service.openapi.exception.OpenApiExceptionHelper.graphQlErrorException;
import static org.dotwebstack.framework.service.openapi.helper.CoreRequestHelper.getParameterNamesOfType;
import static org.dotwebstack.framework.service.openapi.helper.CoreRequestHelper.validateParameterExistence;
import static org.dotwebstack.framework.service.openapi.helper.CoreRequestHelper.validateRequestBodyNonexistent;
import static org.dotwebstack.framework.service.openapi.helper.OasConstants.X_DWS_EXPAND_TYPE;
import static org.dotwebstack.framework.service.openapi.helper.OasConstants.X_DWS_TYPE;
import static org.dotwebstack.framework.service.openapi.helper.SchemaResolver.resolveRequestBody;
import static org.dotwebstack.framework.service.openapi.response.ResponseWriteContextHelper.createNewDataStack;
import static org.dotwebstack.framework.service.openapi.response.ResponseWriteContextHelper.createNewResponseWriteContext;
import static org.springframework.web.reactive.function.BodyInserters.fromPublisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.dotwebstack.framework.core.InvalidConfigurationException;
import org.dotwebstack.framework.core.query.GraphQlArgument;
import org.dotwebstack.framework.core.query.GraphQlField;
import org.dotwebstack.framework.service.openapi.exception.BadRequestException;
import org.dotwebstack.framework.service.openapi.exception.GraphQlErrorException;
import org.dotwebstack.framework.service.openapi.exception.NoResultFoundException;
import org.dotwebstack.framework.service.openapi.exception.ParameterValidationException;
import org.dotwebstack.framework.service.openapi.mapping.ResponseMapper;
import org.dotwebstack.framework.service.openapi.param.ParamHandler;
import org.dotwebstack.framework.service.openapi.param.ParamHandlerRouter;
import org.dotwebstack.framework.service.openapi.query.GraphQlQueryBuilder;
import org.dotwebstack.framework.service.openapi.requestbody.RequestBodyHandlerRouter;
import org.dotwebstack.framework.service.openapi.response.RequestBodyContext;
import org.dotwebstack.framework.service.openapi.response.ResponseContextValidator;
import org.dotwebstack.framework.service.openapi.response.ResponseSchemaContext;
import org.dotwebstack.framework.service.openapi.response.ResponseTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class CoreRequestHandler implements HandlerFunction<ServerResponse> {

  private OpenAPI openApi;

  private final ResponseSchemaContext responseSchemaContext;

  private final ResponseContextValidator responseContextValidator;

  private final GraphQL graphQL;

  private final ResponseMapper responseMapper;

  private final ParamHandlerRouter paramHandlerRouter;

  private final RequestBodyHandlerRouter requestBodyHandlerRouter;

  private final String pathName;

  public CoreRequestHandler(OpenAPI openApi, String pathName, ResponseSchemaContext responseSchemaContext,
      ResponseContextValidator responseContextValidator, GraphQL graphQL, ResponseMapper responseMapper,
      ParamHandlerRouter paramHandlerRouter, RequestBodyHandlerRouter requestBodyHandlerRouter) {
    this.openApi = openApi;
    this.pathName = pathName;
    this.responseSchemaContext = responseSchemaContext;
    this.graphQL = graphQL;
    this.responseMapper = responseMapper;
    this.paramHandlerRouter = paramHandlerRouter;
    this.responseContextValidator = responseContextValidator;
    this.requestBodyHandlerRouter = requestBodyHandlerRouter;
    validateSchema();
  }

  @Override
  public Mono<ServerResponse> handle(@NonNull ServerRequest request) {
    Mono<String> bodyPublisher = Mono.fromCallable(() -> getResponse(request))
        .publishOn(Schedulers.elastic())
        .onErrorResume(ParameterValidationException.class,
            exception -> getMonoError(format("Error while obtaining request parameters: %s", exception.getMessage()),
                HttpStatus.BAD_REQUEST))
        .onErrorResume(JsonProcessingException.class,
            exception -> getMonoError("Error while serializing response to JSON.", HttpStatus.INTERNAL_SERVER_ERROR))
        .onErrorResume(GraphQlErrorException.class,
            exception -> getMonoError(exception.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR))
        .onErrorResume(NoResultFoundException.class, exception -> getMonoError(null, HttpStatus.NOT_FOUND))
        .onErrorResume(UnsupportedMediaTypeException.class,
            exception -> getMonoError(null, HttpStatus.UNSUPPORTED_MEDIA_TYPE))
        .onErrorResume(BadRequestException.class,
            exception -> getMonoError(format("Error while processing the request: %s", exception.getMessage()),
                HttpStatus.BAD_REQUEST))
        .onErrorResume(InvalidConfigurationException.class,
            exception -> getMonoError(format("Error while validating the request: %s", exception.getMessage()),
                HttpStatus.BAD_REQUEST));

    ResponseTemplate template = getResponseTemplate();
    return ServerResponse.ok()
        .contentType(MediaType.parseMediaType(template.getMediaType()))
        .body(fromPublisher(bodyPublisher, String.class));
  }

  private void validateSchema() {
    GraphQlField field = responseSchemaContext.getGraphQlField();
    if (responseSchemaContext.getResponses()
        .stream()
        .noneMatch(responseTemplate -> responseTemplate.isApplicable(200, 299))) {
      throw unsupportedOperationException("No response in the 200 range found.");
    }
    validateParameters(field, responseSchemaContext.getParameters(), pathName);
    RequestBodyContext requestBodyContext = responseSchemaContext.getRequestBodyContext();
    if (Objects.nonNull(requestBodyContext)) {
      RequestBody requestBody = resolveRequestBody(openApi, requestBodyContext.getRequestBodySchema());
      this.requestBodyHandlerRouter.getRequestBodyHandler(requestBody)
          .validate(field, requestBody, pathName);
    }
    responseSchemaContext.getResponses()
        .stream()
        .filter(responseTemplate -> responseTemplate.isApplicable(200, 299))
        .forEach(response -> responseContextValidator.validate(response.getResponseObject(), field));
  }

  private void validateParameters(GraphQlField field, List<Parameter> parameters, String pathName) {
    if (parameters.stream()
        .filter(parameter -> Objects.nonNull(parameter.getExtensions()) && Objects.nonNull(parameter.getExtensions()
            .get(X_DWS_TYPE)) && X_DWS_EXPAND_TYPE.equals(
                parameter.getExtensions()
                    .get(X_DWS_TYPE)))
        .count() > 1) {
      throw invalidConfigurationException("It is not possible to have more than one expand parameter per Operation");
    }
    parameters.forEach(parameter -> this.paramHandlerRouter.getParamHandler(parameter)
        .validate(field, parameter, pathName));
    field.getArguments()
        .forEach(argument -> verifyRequiredWithoutDefaultArgument(argument, parameters, pathName));
  }

  private void verifyRequiredWithoutDefaultArgument(GraphQlArgument argument, List<Parameter> parameters,
      String pathName) {
    if (argument.isRequired() && !argument.isHasDefault() && parameters.stream()
        .noneMatch(parameter -> Boolean.TRUE.equals(parameter.getRequired()) && parameter.getName()
            .equals(argument.getName()))) {
      throw invalidConfigurationException(
          "No required OAS parameter found for required and no-default GraphQL argument '{}' in path '{}'",
          argument.getName(), pathName);
    }
    if (argument.isRequired()) {
      argument.getChildren()
          .forEach(child -> verifyRequiredWithoutDefaultArgument(child, parameters, pathName));
    }
  }

  private Mono<String> getMonoError(String message, HttpStatus statusCode) {
    return Mono.error(new ResponseStatusException(statusCode, message));
  }

  private String getResponse(ServerRequest request)
      throws NoResultFoundException, JsonProcessingException, GraphQlErrorException, BadRequestException {
    Map<String, Object> inputParams = resolveParameters(request);

    String query = buildQueryString(inputParams);

    if (LOG.isDebugEnabled()) {
      logInputRequest(request);
      LOG.debug("GraphQL query is:\n\n{}\n", formatGraphQlQuery(query));
    }

    ExecutionInput executionInput = ExecutionInput.newExecutionInput()
        .query(query)
        .variables(inputParams)
        .build();

    ExecutionResult result = graphQL.execute(executionInput);
    if (result.getErrors()
        .isEmpty()) {
      Object data = ((Map) result.getData()).values()
          .iterator()
          .next();

      URI uri = request.uri();
      return responseMapper.toJson(createNewResponseWriteContext(getResponseTemplate().getResponseObject(), data,
          inputParams, createNewDataStack(new ArrayDeque<>(), data, inputParams), uri));
    }
    throw graphQlErrorException("GraphQL query returned errors: {}", result.getErrors());
  }

  private void logInputRequest(ServerRequest request) {
    LOG.debug("Request received at: {}", request);

    Map<Object, Object> paramMap = new LinkedHashMap<>();
    paramMap.putAll(request.queryParams());
    paramMap.putAll(request.headers()
        .asHttpHeaders());
    paramMap.putAll(request.pathVariables());
    LOG.debug("Request contains following parameters: {}", paramMap.entrySet()
        .stream()
        .map(entry -> entry.getKey() + " -> " + entry.getValue())
        .collect(Collectors.toList()));

    Mono<String> mono = request.bodyToMono(String.class);
    String value = mono.block();
    if (Objects.nonNull(value)) {
      LOG.debug("Request contains the following body: {}", value);
    }
  }

  private String formatGraphQlQuery(String query) {
    int indents = 0;
    StringBuilder builder = new StringBuilder();
    for (String character : query.split("")) {
      switch (character) {
        case "{":
          builder.append(" {\n");
          indents++;
          builder.append("\t".repeat(Math.max(0, indents)));
          break;
        case "}":
          builder.append("\n");
          indents--;
          builder.append("\t".repeat(Math.max(0, indents)));
          builder.append("}");
          break;
        case ",":
          builder.append(",\n");
          builder.append("\t".repeat(Math.max(0, indents)));
          break;
        default:
          builder.append(character);
          break;
      }
    }

    return builder.toString();
  }

  private ResponseTemplate getResponseTemplate() {
    return responseSchemaContext.getResponses()
        .stream()
        .filter(response -> response.isApplicable(200, 299))
        .findFirst()
        .orElseThrow(() -> unsupportedOperationException("No response found within the 200 range."));
  }

  private Map<String, Object> resolveParameters(ServerRequest request) throws BadRequestException {
    Map<String, Object> result = new HashMap<>();
    if (Objects.nonNull(this.responseSchemaContext.getParameters())) {

      validateParameterExistence("query", getParameterNamesOfType(this.responseSchemaContext.getParameters(), "query"),
          request.queryParams()
              .keySet());
      validateParameterExistence("path", getParameterNamesOfType(this.responseSchemaContext.getParameters(), "path"),
          request.pathVariables()
              .keySet());

      for (Parameter parameter : this.responseSchemaContext.getParameters()) {
        ParamHandler handler = paramHandlerRouter.getParamHandler(parameter);
        handler.getValue(request, parameter, responseSchemaContext)
            .ifPresent(value -> result.put(handler.getParameterName(parameter.getName()), value));
      }
    }
    RequestBodyContext requestBodyContext = this.responseSchemaContext.getRequestBodyContext();
    if (Objects.nonNull(requestBodyContext)) {
      RequestBody requestBody = resolveRequestBody(openApi, requestBodyContext.getRequestBodySchema());
      this.requestBodyHandlerRouter.getRequestBodyHandler(requestBody)
          .getValue(request, requestBody, result)
          .ifPresent(value -> result.put(requestBodyContext.getName(), value));
    } else {
      validateRequestBodyNonexistent(request);
    }
    return result;
  }

  private String buildQueryString(Map<String, Object> inputParams) {
    return new GraphQlQueryBuilder().toQuery(this.responseSchemaContext, inputParams);
  }
}
