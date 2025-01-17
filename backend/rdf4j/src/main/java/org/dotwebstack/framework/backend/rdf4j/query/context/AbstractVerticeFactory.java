package org.dotwebstack.framework.backend.rdf4j.query.context;

import static java.util.Collections.singletonList;
import static org.dotwebstack.framework.backend.rdf4j.query.context.FilterHelper.getFilterRulePath;
import static org.dotwebstack.framework.backend.rdf4j.query.context.FilterHelper.getOperand;
import static org.dotwebstack.framework.backend.rdf4j.query.context.VerticeFactoryHelper.getNextNodeShape;
import static org.dotwebstack.framework.backend.rdf4j.query.context.VerticeFactoryHelper.getSubjectForField;
import static org.dotwebstack.framework.backend.rdf4j.query.context.VerticeFactoryHelper.hasChildEdgeOfType;
import static org.dotwebstack.framework.core.helpers.ObjectHelper.castToList;

import com.google.common.collect.ImmutableList;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.SelectedField;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.dotwebstack.framework.backend.rdf4j.Rdf4jProperties;
import org.dotwebstack.framework.backend.rdf4j.serializers.SerializerRouter;
import org.dotwebstack.framework.backend.rdf4j.shacl.NodeShape;
import org.dotwebstack.framework.backend.rdf4j.shacl.PropertyShape;
import org.dotwebstack.framework.backend.rdf4j.shacl.propertypath.BasePath;
import org.dotwebstack.framework.core.directives.CoreDirectives;
import org.dotwebstack.framework.core.directives.FilterOperator;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Operand;
import org.eclipse.rdf4j.sparqlbuilder.core.Orderable;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.OuterQuery;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfPredicate;

abstract class AbstractVerticeFactory {

  private SerializerRouter serializerRouter;

  private Rdf4jProperties rdf4jProperties;

  public AbstractVerticeFactory(SerializerRouter serializerRouter, Rdf4jProperties rdf4jProperties) {
    this.serializerRouter = serializerRouter;
    this.rdf4jProperties = rdf4jProperties;
  }

  Edge createSimpleEdge(Variable subject, BasePath basePath, boolean isOptional, boolean isVisible) {
    return Edge.builder()
        .predicate(basePath.toPredicate())
        .constructPredicate(basePath.toConstructPredicate())
        .object(Vertice.builder()
            .subject(subject)
            .build())
        .isVisible(isVisible)
        .isOptional(isOptional)
        .build();
  }

  Edge createSimpleEdge(Variable subject, Set<Iri> iris, RdfPredicate predicate, boolean isVisible) {
    return Edge.builder()
        .predicate(predicate)
        .object(Vertice.builder()
            .subject(subject)
            .iris(iris)
            .build())
        .isVisible(isVisible)
        .isOptional(false)
        .build();
  }

  Map<GraphQLArgument, SelectedField> getArgumentFieldMapping(NodeShape nodeShape, List<SelectedField> fields) {
    return fields.stream()
        .filter(field -> !field.getQualifiedName()
            .contains("/"))
        .filter(field -> Objects.nonNull(nodeShape.getPropertyShape(field.getName())
            .getNode()))
        .flatMap(field -> field.getFieldDefinition()
            .getArguments()
            .stream()
            .filter(argument -> argument.getDirective(CoreDirectives.FILTER_NAME) != null)
            .map(argument -> new AbstractMap.SimpleEntry<>(argument, field)))
        .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), HashMap::putAll);
  }

  List<Edge> findEdgesToBeProcessed(NodeShape nodeShape, SelectedField field, List<Edge> edges) {
    return edges.stream()
        .filter(edge -> edge.getPredicate()
            .getQueryString()
            .equals(nodeShape.getPropertyShape(field.getName())
                .getPath()
                .toPredicate()
                .getQueryString()))
        .filter(edge -> {
          if (!GraphQLTypeUtil.isScalar(GraphQLTypeUtil.unwrapAll(field.getFieldDefinition()
              .getType()))) {
            return hasChildEdgeOfType(edge, nodeShape.getPropertyShape(field.getName())
                .getNode()
                .getTargetClasses());
          }
          return true;
        })
        .collect(Collectors.toList());
  }

  void processEdge(Vertice vertice, GraphQLArgument argument, OuterQuery<?> query, NodeShape nodeShape,
      SelectedField field) {
    Object filterValue = field.getArguments()
        .get(argument.getName());
    if (Objects.nonNull(filterValue)) {
      List<String> startPath = getFilterRulePath(argument);
      String[] fieldPath = field.getName()
          .split("\\.");

      addFilterToVertice(vertice, query, getNextNodeShape(nodeShape, Arrays.asList(fieldPath)), FilterRule.builder()
          .path(startPath)
          .value(filterValue)
          .build());
    }
  }

  void addFilterToVertice(Vertice vertice, OuterQuery<?> query, NodeShape nodeShape, FilterRule filterRule) {
    Edge match = findOrCreatePath(vertice, query, nodeShape, filterRule.getPath(), true);

    List<Filter> filters = Objects.nonNull(match.getObject()
        .getFilters()) ? match.getObject()
            .getFilters() : new ArrayList<>();

    Filter filter = createFilter(nodeShape, filterRule.getOperator(), filterRule.getValue(), filterRule.getPath()
        .get(filterRule.getPath()
            .size() - 1));

    filters.add(filter);

    match.getObject()
        .setFilters(filters);
  }

  /*
   * Create a new filter with either one argument or a list of arguments
   */
  private Filter createFilter(NodeShape nodeShape, String filterOperator, Object filterValue, String argumentName) {
    List<Object> filterArguments;
    if (filterValue instanceof List) {
      filterArguments = castToList(filterValue);
    } else {
      filterArguments = singletonList(filterValue);
    }

    List<Operand> operands = filterArguments.stream()
        .map(filterArgument -> getOperand(nodeShape, argumentName, serializerRouter.serialize(filterArgument),
            rdf4jProperties.getShape()
                .getLanguage()))
        .collect(Collectors.toList());

    return Filter.builder()
        .operator(FilterOperator.getByValue(filterOperator)
            .orElse(FilterOperator.EQ))
        .operands(operands)
        .build();
  }

  /*
   * Find the path to apply the filter on. In case no or a only partial path is found, create the part
   * of the path that does not yet exist
   */
  private Edge findOrCreatePath(Vertice vertice, OuterQuery<?> query, NodeShape nodeShape, List<String> fieldPaths,
      boolean required) {
    Edge match = findOrCreateEdge(query, nodeShape.getPropertyShape(fieldPaths.get(0)), vertice, required);
    if (required) {
      match.setOptional(false);
    }

    if (fieldPaths.size() == 1) {
      return match;
    }

    NodeShape childShape = getNextNodeShape(nodeShape, fieldPaths);
    return findOrCreatePath(match.getObject(), query, childShape, fieldPaths.subList(1, fieldPaths.size()), required);
  }

  /*
   * Find the edge belonging to the given propertyshape. In case no propertyshape is found, create a
   * new one
   */
  private Edge findOrCreateEdge(OuterQuery<?> query, PropertyShape propertyShape, Vertice vertice, boolean required) {
    List<Edge> childEdges = vertice.getEdges();

    Optional<Edge> optional = Optional.empty();
    if (Objects.nonNull(childEdges)) {
      optional = childEdges.stream()
          .filter(childEdge -> childEdge.getPredicate()
              .getQueryString()
              .equals(propertyShape.getPath()
                  .toPredicate()
                  .getQueryString()))
          .filter(childEdge -> Objects.isNull(propertyShape.getNode()) || hasChildEdgeOfType(childEdge,
              propertyShape.getNode()
                  .getTargetClasses()))
          .findFirst();
    }

    return optional.orElseGet(() -> {
      Edge edge = createSimpleEdge(query.var(), propertyShape.getPath(), required, false);
      vertice.getEdges()
          .add(edge);
      return edge;
    });
  }

  /*
   * It can happen that the same path is used twice, we want to overcome this problem, by looking at
   * the edges, if we find more edges with the same predicate, we place the child edges of the latter
   * edges we find, on top of the first edge we find.
   */
  void makeEdgesUnique(List<Edge> edges) {
    List<Edge> uniqueEdges = new ArrayList<>();
    edges.forEach(edge -> {
      Edge duplicate = uniqueEdges.stream()
          .filter(uniqueEdge -> uniqueEdge.getPredicate()
              .equals(edge.getPredicate()))
          .findFirst()
          .orElse(null);

      if (Objects.nonNull(duplicate)) {
        List<Edge> childEdges = edge.getObject()
            .getEdges();
        duplicate.getObject()
            .getEdges()
            .addAll(childEdges);
      } else {
        uniqueEdges.add(edge);
      }
    });
  }

  void addOrderables(Vertice vertice, OuterQuery<?> query, Map<String, Object> orderMap, NodeShape nodeShape) {
    String fieldName = orderMap.get("field")
        .toString();
    String order = orderMap.get("order")
        .toString();

    List<String> fieldPaths = Arrays.asList(fieldName.split("\\."));
    NodeShape childShape = getNextNodeShape(nodeShape, fieldPaths);

    // add missing edges
    Edge match;
    Variable subject;
    if (nodeShape.equals(childShape)) {
      match = findOrCreatePath(vertice, query, nodeShape, fieldPaths, false);
      subject = getSubjectForField(match, nodeShape, fieldPaths);
    } else {
      Edge edge = createSimpleEdge(query.var(), nodeShape.getPropertyShape(fieldPaths.get(0))
          .getPath(), true, false);
      fieldPaths = fieldPaths.subList(1, fieldPaths.size());

      vertice.getEdges()
          .add(edge);

      match = findOrCreatePath(edge.getObject(), query, childShape, fieldPaths, false);
      subject = getSubjectForField(match, childShape, fieldPaths);
    }

    List<Orderable> orderables = Objects.nonNull(vertice.getOrderables()) ? vertice.getOrderables() : new ArrayList<>();
    orderables.add((Objects.isNull(order) || order.equalsIgnoreCase("desc")) ? subject.desc() : subject.asc());
    vertice.setOrderables(orderables);
  }

  void addLanguageFilter(Edge edge, PropertyShape propertyShape) {
    if (Objects.equals(RDF.LANGSTRING, propertyShape.getDatatype())) {
      edge.getObject()
          .getFilters()
          .add(createLanguageFilter());
    }
  }

  private Filter createLanguageFilter() {
    return Filter.builder()
        .operator(FilterOperator.LANGUAGE)
        .operands(ImmutableList.of(Rdf.literalOf(rdf4jProperties.getShape()
            .getLanguage())))
        .build();
  }
}
