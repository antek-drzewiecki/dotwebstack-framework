package org.dotwebstack.framework.core.traversers;

import static graphql.language.InputObjectTypeDefinition.newInputObjectDefinition;
import static graphql.language.InputValueDefinition.newInputValueDefinition;
import static graphql.language.ObjectTypeDefinition.newObjectTypeDefinition;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInputObjectField.newInputObjectField;
import static graphql.schema.GraphQLInputObjectType.newInputObject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import graphql.Scalars;
import graphql.language.FieldDefinition;
import graphql.language.TypeName;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoreTraverserTest {

  private DataFetchingEnvironment dataFetchingEnvironment;

  private TypeDefinitionRegistry typeDefinitionRegistry;

  private CoreTraverser coreTraverser;

  @BeforeEach
  void doBefore() {
    dataFetchingEnvironment = mock(DataFetchingEnvironment.class);
    typeDefinitionRegistry = new TypeDefinitionRegistry();
    coreTraverser = new CoreTraverser(typeDefinitionRegistry);
  }

  @Test
  void getTuples_returnsList_ForScalarArgument() {
    // Arrange
    GraphQLArgument argument = newArgument().name("identifier")
        .type(GraphQLNonNull.nonNull(Scalars.GraphQLID))
        .build();

    GraphQLFieldDefinition fieldDefinition = newFieldDefinition().name("brewery")
        .type(GraphQLObjectType.newObject()
            .name("Brewery"))
        .argument(argument)
        .build();

    when(dataFetchingEnvironment.getFieldDefinition()).thenReturn(fieldDefinition);
    when(dataFetchingEnvironment.getArguments()).thenReturn(ImmutableMap.of("identifier", 1));

    // Act & Assert
    assertThat(coreTraverser.getTuples(dataFetchingEnvironment, TraverserFilter.noFilter()))
        .contains(DirectiveContainerTuple.builder()
            .container(argument)
            .value(1)
            .build());
  }

  @Test
  void getTuples_returnsList_ForInputObjectField() {
    // Arrange
    GraphQLInputObjectField field = newInputObjectField().name("identifier")
        .type(GraphQLNonNull.nonNull(Scalars.GraphQLID))
        .build();

    GraphQLArgument argument = newArgument().name("input")
        .type(newInputObject().name("Input")
            .field(field)
            .build())
        .build();

    GraphQLFieldDefinition fieldDefinition = newFieldDefinition().name("brewery")
        .type(GraphQLObjectType.newObject()
            .name("Brewery"))
        .argument(argument)
        .build();

    when(dataFetchingEnvironment.getFieldDefinition()).thenReturn(fieldDefinition);
    when(dataFetchingEnvironment.getArguments()).thenReturn(ImmutableMap.of("input", ImmutableMap.of("identifier", 1)));

    // Act & Assert
    assertThat(coreTraverser.getTuples(dataFetchingEnvironment, TraverserFilter.noFilter()))
        .contains(DirectiveContainerTuple.builder()
            .container(field)
            .value(1)
            .build());
  }

  @Test
  void getRootResultTypeNames_returnsList_ForArgument() {
    // Arrange
    TypeName breweryTypeName = TypeName.newTypeName("Brewery")
        .build();

    typeDefinitionRegistry.add(newInputObjectDefinition().name("SubInput")
        .inputValueDefinition(newInputValueDefinition().name("a")
            .type(TypeName.newTypeName("String")
                .build())
            .build())
        .build());

    typeDefinitionRegistry.add(newInputObjectDefinition().name("Input")
        .inputValueDefinition(newInputValueDefinition().name("input")
            .type(TypeName.newTypeName("SubInput")
                .build())
            .build())
        .build());

    typeDefinitionRegistry.add(newObjectTypeDefinition().name("Brewery")
        .fieldDefinition(FieldDefinition.newFieldDefinition()
            .name("identifier")
            .type(TypeName.newTypeName("ID")
                .build())
            .build())
        .build());

    typeDefinitionRegistry.add(newObjectTypeDefinition().name("Query")
        .fieldDefinition(FieldDefinition.newFieldDefinition()
            .name("breweries_input")
            .type(breweryTypeName)
            .inputValueDefinition(newInputValueDefinition().name("input")
                .type(TypeName.newTypeName("Input")
                    .build())
                .build())
            .build())
        .build());

    // Act & Assert
    assertThat(coreTraverser.getRootResultTypeNames(typeDefinitionRegistry.types()
        .get("Input"))).contains(breweryTypeName);
  }
}
