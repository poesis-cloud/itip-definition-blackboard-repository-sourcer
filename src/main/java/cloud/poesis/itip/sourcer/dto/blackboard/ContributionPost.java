package cloud.poesis.itip.sourcer.dto.blackboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * Body of {@code POST /blackboards/{id}/contributions}.
 *
 * <p>Identifies the target slot by {@code (panel, slot)} name within the addressed Blackboard. The
 * substrate validates {@code post} against the slot's {@code schemaUri} at POST time; failure → 422
 * (not persisted).
 *
 * @param panel client-namespaced panel name
 * @param slot slot name within the panel
 * @param post payload to validate against the slot schema
 * @param derivedContribution optional in-Blackboard contribution lineage; cross-Blackboard URIs are
 *     rejected (422)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContributionPost(
    String panel, String slot, JsonNode post, List<URI> derivedContribution) {

  public ContributionPost {
    derivedContribution = derivedContribution == null ? null : List.copyOf(derivedContribution);
  }

  public ContributionPost(String panel, String slot, JsonNode post) {
    this(panel, slot, post, null);
  }

  @Override
  public List<URI> derivedContribution() {
    return derivedContribution == null ? null : Collections.unmodifiableList(derivedContribution);
  }
}
