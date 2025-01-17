package org.dotwebstack.framework.backend.rdf4j.scalars;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import lombok.NonNull;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

public class IriCoercing implements Coercing<IRI, IRI> {

  private static final ValueFactory VF = SimpleValueFactory.getInstance();

  @Override
  public IRI serialize(@NonNull Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IRI parseValue(@NonNull Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IRI parseLiteral(@NonNull Object value) {
    if (value instanceof IRI) {
      return (IRI) value;
    }

    if (!(value instanceof StringValue)) {
      throw new CoercingParseLiteralException(String.format("Unable to parse IRI from '%s' type.", value.getClass()
          .getName()));
    }

    String valueStr = ((StringValue) value).getValue();

    try {
      return VF.createIRI(valueStr);
    } catch (IllegalArgumentException e) {
      throw new CoercingParseLiteralException(String.format("Unable to parse IRI from string value '%s'.", valueStr),
          e);
    }
  }
}
