package org.dotwebstack.framework.service.openapi.response;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.NonNull;

public class ResponseWriteContextHelper {

  private ResponseWriteContextHelper() {}

  public static List<ResponseWriteContext> unwrapChildSchema(@NonNull ResponseWriteContext parentContext) {
    return parentContext.getResponseObject()
        .getSummary()
        .getChildren()
        .stream()
        .map(child -> {
          Object data = parentContext.getData();
          Deque<FieldContext> dataStack = new ArrayDeque<>(parentContext.getDataStack());

          if (!child.getSummary()
              .isEnvelope() && data instanceof Map) {
            data = ((Map) data).get(child.getIdentifier());
            dataStack = createNewDataStack(dataStack, data, Collections.emptyMap());
          }

          return createNewResponseWriteContext(child, data, parentContext.getParameters(), dataStack,
              parentContext.getUri());
        })
        .collect(Collectors.toList());
  }

  public static ResponseWriteContext unwrapItemSchema(@NonNull ResponseWriteContext parentContext) {
    ResponseObject childSchema = parentContext.getResponseObject()
        .getSummary()
        .getItems()
        .get(0);
    return createNewResponseWriteContext(childSchema, parentContext.getData(), parentContext.getParameters(),
        parentContext.getDataStack(), parentContext.getUri());
  }

  public static Deque<FieldContext> createNewDataStack(@NonNull Deque<FieldContext> previousDataStack, Object newData,
      Map<String, Object> newInput) {
    Deque<FieldContext> dataStack = new ArrayDeque<>(previousDataStack);
    if (newData instanceof Map) {
      dataStack.push(createFieldContext(newData, newInput));
    }
    return dataStack;
  }

  public static FieldContext createFieldContext(Object newData, Map<String, Object> newInput) {
    return FieldContext.builder()
        .data(newData)
        .input(newInput)
        .build();
  }

  public static ResponseWriteContext createResponseWriteContextFromChildSchema(
      @NonNull ResponseWriteContext parentContext, @NonNull ResponseObject childSchema) {
    Deque<FieldContext> dataStack = new ArrayDeque<>(parentContext.getDataStack());
    Object data = parentContext.getData();

    if (!childSchema.getSummary()
        .isEnvelope()) {
      if (!parentContext.getDataStack()
          .isEmpty()) {
        data = ((Map) parentContext.getDataStack()
            .peek()
            .getData()).get(childSchema.getIdentifier());
        dataStack = createNewDataStack(parentContext.getDataStack(), data, Collections.emptyMap());
        return createNewResponseWriteContext(childSchema, data, parentContext.getParameters(), dataStack,
            parentContext.getUri());
      }

      if (data instanceof Map) {
        data = ((Map) data).get(childSchema.getIdentifier());
      }
    }

    return createNewResponseWriteContext(childSchema, data, parentContext.getParameters(), dataStack,
        parentContext.getUri());
  }

  public static ResponseWriteContext createResponseContextFromChildData(@NonNull ResponseWriteContext parentContext,
      @NonNull Object childData) {
    Deque<FieldContext> dataStack = createNewDataStack(parentContext.getDataStack(), childData, Collections.emptyMap());
    return createNewResponseWriteContext(parentContext.getResponseObject(), childData, parentContext.getParameters(),
        dataStack, parentContext.getUri());
  }

  public static ResponseWriteContext createNewResponseWriteContext(@NonNull ResponseObject schema, Object data,
      Map<String, Object> parameters, @NonNull Deque<FieldContext> dataStack, URI uri) {
    return ResponseWriteContext.builder()
        .responseObject(schema)
        .data(data)
        .parameters(parameters)
        .dataStack(dataStack)
        .uri(uri)
        .build();
  }
}
