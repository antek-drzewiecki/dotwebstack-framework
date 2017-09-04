package org.dotwebstack.framework.frontend.http.provider.graph;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.OutputStream;
import javax.ws.rs.core.MediaType;
import org.dotwebstack.framework.test.DBEERPEDIA;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TurtleGraphProviderTest {

  @Mock
  private OutputStream outputStream;

  @Captor
  private ArgumentCaptor<byte[]> byteCaptor;

  @Test
  public void isWritableForTurtleMediaType() {
    // Arrange
    TurtleGraphProvider provider = new TurtleGraphProvider();

    // Act
    boolean result = provider.isWriteable(LinkedHashModel.class, null, null,
        new MediaType("text", "turtle"));

    // Assert
    assertThat(result, is(true));
  }

  @Test
  public void isNotWritableForStringClass() {
    // Arrange
    TurtleGraphProvider provider = new TurtleGraphProvider();

    // Act
    boolean result = provider.isWriteable(String.class, null, null,
        new MediaType("text", "turtle"));

    // Assert
    assertThat(result, is(false));
  }

  @Test
  public void isNotWritableForXmlMediaType() {
    // Arrange
    TurtleGraphProvider provider = new TurtleGraphProvider();

    // Act
    boolean result = provider.isWriteable(String.class, null, null,
        MediaType.APPLICATION_XML_TYPE);

    // Assert
    assertThat(result, is(false));
  }

  @Test
  public void writesTurtleFormat() throws IOException {
    // Arrange
    TurtleGraphProvider provider = new TurtleGraphProvider();
    Model model = new ModelBuilder().subject(DBEERPEDIA.BREWERIES)
        .add(RDF.TYPE, DBEERPEDIA.BACKEND)
        .add(RDFS.LABEL, DBEERPEDIA.BREWERIES_LABEL)
        .build();

    // Act
    provider.writeTo(model, null, null, null, null, null, outputStream);

    // Assert
    verify(outputStream).write(byteCaptor.capture(), anyInt(), anyInt());
    String result = new String(byteCaptor.getValue());
    assertThat(result,
        containsString("<http://dbeerpedia.org#Breweries> a <http://dbeerpedia.org#Backend> ;"));
    assertThat(result, containsString(
        "<http://www.w3.org/2000/01/rdf-schema#label> \"Beer breweries in The Netherlands\""));
  }

}
