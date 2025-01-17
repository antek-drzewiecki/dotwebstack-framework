package org.dotwebstack.framework.backend.rdf4j.query;

import static org.dotwebstack.framework.backend.rdf4j.query.context.FilterHelper.getFilterRulePath;
import static org.dotwebstack.framework.core.helpers.ExceptionHelper.illegalArgumentException;

import graphql.schema.GraphQLDirective;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;
import org.dotwebstack.framework.backend.rdf4j.directives.Rdf4jDirectives;
import org.dotwebstack.framework.backend.rdf4j.query.context.FilterRule;
import org.dotwebstack.framework.backend.rdf4j.query.context.SelectVerticeFactory;
import org.dotwebstack.framework.backend.rdf4j.query.context.Vertice;
import org.dotwebstack.framework.backend.rdf4j.query.context.VerticeHelper;
import org.dotwebstack.framework.backend.rdf4j.shacl.NodeShape;
import org.dotwebstack.framework.core.directives.CoreDirectives;
import org.dotwebstack.framework.core.jexl.JexlHelper;
import org.dotwebstack.framework.core.traversers.DirectiveContainerTuple;
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern;

class SubjectQueryBuilder extends AbstractQueryBuilder<SelectQuery> {

  private static final Variable SUBJECT_VAR = SparqlBuilder.var("s");

  private final JexlHelper jexlHelper;

  private final NodeShape nodeShape;

  private final SelectVerticeFactory selectVerticeFactory;

  private SubjectQueryBuilder(@NonNull QueryEnvironment environment, @NonNull JexlEngine jexlEngine,
      @NonNull SelectVerticeFactory selectVerticeFactory) {
    super(environment, Queries.SELECT());
    this.jexlHelper = new JexlHelper(jexlEngine);
    this.nodeShape = this.environment.getNodeShapeRegistry()
        .get(this.environment.getObjectType());
    this.selectVerticeFactory = selectVerticeFactory;
  }

  static SubjectQueryBuilder create(@NonNull QueryEnvironment environment, @NonNull JexlEngine jexlEngine,
      @NonNull SelectVerticeFactory selectVerticeFactory) {
    return new SubjectQueryBuilder(environment, jexlEngine, selectVerticeFactory);
  }

  String getQueryString(final Map<String, Object> arguments, final GraphQLDirective sparqlDirective,
      List<DirectiveContainerTuple> filterMapping) {
    final MapContext context = new MapContext(arguments);

    @SuppressWarnings({"unchecked", "rawtypes"})
    List<Object> orderByObject =
        jexlHelper.evaluateDirectiveArgument(Rdf4jDirectives.SPARQL_ARG_ORDER_BY, sparqlDirective, context, List.class)
            .orElse(new ArrayList());

    List<FilterRule> filterRules = filterMapping.stream()
        .map(filterRule -> FilterRule.builder()
            .path(getFilterRulePath(filterRule.getContainer()))
            .operator((String) filterRule.getContainer()
                .getDirective(CoreDirectives.FILTER_NAME)
                .getArgument(CoreDirectives.FILTER_ARG_OPERATOR)
                .getValue())
            .value(filterRule.getValue())
            .build())
        .collect(Collectors.toList());

    Vertice root = selectVerticeFactory.createRoot(SUBJECT_VAR, query, nodeShape, filterRules, orderByObject);

    query.select(root.getSubject())
        .where(VerticeHelper.getWherePatterns(root)
            .toArray(new GraphPattern[] {}));

    getLimitFromContext(context, sparqlDirective).ifPresent(query::limit);
    getOffsetFromContext(context, sparqlDirective).ifPresent(query::offset);
    root.getOrderables()
        .forEach(query::orderBy);

    if (distinctQuery(sparqlDirective)) {
      return this.query.distinct()
          .getQueryString();
    }
    return this.query.getQueryString();
  }

  private boolean distinctQuery(@NonNull GraphQLDirective sparqlDirective) {
    return Optional.of(sparqlDirective)
        .map(directive -> directive.getArgument(Rdf4jDirectives.SPARQL_ARG_DISTINCT))
        .map(argument -> (Boolean) argument.getValue())
        .orElse(false);
  }

  Optional<Integer> getLimitFromContext(MapContext context, GraphQLDirective sparqlDirective) {
    Optional<Integer> limitOptional = this.jexlHelper.evaluateDirectiveArgument(Rdf4jDirectives.SPARQL_ARG_LIMIT,
        sparqlDirective, context, Integer.class);
    limitOptional.ifPresent(limit -> {
      if (limit < 1) {
        throw illegalArgumentException("An error occured in the limit expression evaluation");
      }
    });
    return limitOptional;
  }

  Optional<Integer> getOffsetFromContext(MapContext context, GraphQLDirective sparqlDirective) {
    Optional<Integer> offsetOptional = this.jexlHelper.evaluateDirectiveArgument(Rdf4jDirectives.SPARQL_ARG_OFFSET,
        sparqlDirective, context, Integer.class);

    offsetOptional.ifPresent(offset -> {
      if (offset < 0) {
        throw illegalArgumentException("An error occured in the offset expression evaluation");
      }
    });
    return offsetOptional;
  }
}
