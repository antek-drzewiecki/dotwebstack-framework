package org.dotwebstack.framework.backend.rdf4j;

import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.TupleQuery;

public interface RepositoryAdapter {
  TupleQuery prepareTupleQuery(String repositoryId, DataFetchingEnvironment environment, String query);

  GraphQuery prepareGraphQuery(String repositoryId, DataFetchingEnvironment environment, String query,
      List<String> subjectIris);

  boolean supports(String repositoryId);

  boolean addGraphQueryValuesBlock();
}
