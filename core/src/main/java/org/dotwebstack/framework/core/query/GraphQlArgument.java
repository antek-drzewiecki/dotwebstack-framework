package org.dotwebstack.framework.core.query;

import graphql.language.Type;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GraphQlArgument {
  private String name;

  private String baseType;

  private Type<?> type;

  private boolean required;

  private boolean hasDefault;

  @Builder.Default
  private List<GraphQlArgument> children = Collections.emptyList();
}
