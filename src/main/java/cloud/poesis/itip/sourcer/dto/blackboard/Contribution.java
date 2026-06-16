package cloud.poesis.itip.sourcer.dto.blackboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Server representation of a {@code Contribution} resource.
 *
 * <p>Mirrors the {@code Contribution} class in {@code blackboard.puml}. Immutable once persisted;
 * appears in the byte-stable sealed stream after the parent Blackboard reaches {@link
 * BlackboardStatus#SEALED}.
 *
 * @param id server-minted contribution URI
 * @param contributionSlot server-minted URI of the parent slot (transitively gives panel +
 *     blackboard)
 * @param post payload (already validated against the slot's schema)
 * @param derivedContribution optional in-Blackboard lineage URIs
 * @param timestamp server-assigned timestamp; participates in sealed-stream ordering
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Contribution(
    URI id, URI contributionSlot, JsonNode post, List<URI> derivedContribution, Instant timestamp) {
  public Contribution {
    derivedContribution = derivedContribution == null ? null : List.copyOf(derivedContribution);
  }

  @Override
  public List<URI> derivedContribution() {
    return derivedContribution == null ? null : Collections.unmodifiableList(derivedContribution);
  }
}
