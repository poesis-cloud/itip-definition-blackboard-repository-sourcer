package cloud.poesis.itip.sourcer.dto.blackboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.net.URI;
import java.time.Instant;

/**
 * Response representation of a {@code Blackboard} resource.
 *
 * <p>Mirrors the {@code Blackboard} class in {@code blackboard.puml}: the substrate's single
 * top-level resource carrying its own seal lifecycle.
 *
 * @param id server-minted resource URI
 * @param name client-supplied label
 * @param timestamp creation timestamp
 * @param status lifecycle state
 * @param sealTimestamp set on transition to {@link BlackboardStatus#SEALED}; {@code null} otherwise
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Blackboard(
    URI id, String name, Instant timestamp, BlackboardStatus status, Instant sealTimestamp) {}
