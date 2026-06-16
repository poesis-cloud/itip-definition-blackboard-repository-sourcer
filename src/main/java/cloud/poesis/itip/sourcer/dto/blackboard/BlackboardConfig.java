package cloud.poesis.itip.sourcer.dto.blackboard;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for {@code POST /blackboards}.
 *
 * @param name client-supplied free-form label (not interpreted by the substrate)
 * @param auth optional auth scoping descriptor (substrate-defined; pass-through)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BlackboardConfig(String name, String auth) {

  public BlackboardConfig(String name) {
    this(name, null);
  }
}
