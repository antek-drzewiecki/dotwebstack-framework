package org.dotwebstack.framework.service.openapi.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class ResponseTemplate {

  private int responseCode;

  private String mediaType;

  private ResponseObject responseObject;

  public boolean isApplicable(int bottom, int top) {
    return this.responseCode >= bottom && this.responseCode <= top;
  }

  public boolean isApplicable(int bottom, int top, String mediaType) {
    return this.responseCode >= bottom && this.responseCode <= top && this.mediaType.equals(mediaType);
  }
}
