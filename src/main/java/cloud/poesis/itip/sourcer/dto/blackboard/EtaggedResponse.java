package cloud.poesis.itip.sourcer.dto.blackboard;

/**
 * Wrapper carrying both a response body and the server-returned {@code ETag} header value.
 *
 * <p>Used by client calls that participate in {@code If-Match} CAS (panel declaration, seal): the
 * caller stashes the ETag from the create / read response and threads it back into subsequent
 * mutating calls.
 *
 * @param body deserialized response body ({@code null} for empty responses)
 * @param etag {@code ETag} header value as returned by the substrate; {@code null} if absent
 */
public record EtaggedResponse<T>(T body, String etag) {}
