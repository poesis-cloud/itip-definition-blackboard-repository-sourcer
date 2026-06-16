package cloud.poesis.itip.sourcer.client;

import cloud.poesis.itip.sourcer.dto.blackboard.Blackboard;
import cloud.poesis.itip.sourcer.dto.blackboard.BlackboardConfig;
import cloud.poesis.itip.sourcer.dto.blackboard.BlackboardState;
import cloud.poesis.itip.sourcer.dto.blackboard.Contribution;
import cloud.poesis.itip.sourcer.dto.blackboard.ContributionList;
import cloud.poesis.itip.sourcer.dto.blackboard.ContributionPost;
import cloud.poesis.itip.sourcer.dto.blackboard.EtaggedResponse;
import cloud.poesis.itip.sourcer.dto.blackboard.PanelDeclaration;
import cloud.poesis.itip.sourcer.dto.blackboard.PanelTopology;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * REST client to the Definition Blackboard Manager (substrate).
 *
 * <p>Sole egress point of the sourcer for Blackboard operations: panel declaration, contribution
 * POST, GET state, seal. Wraps a {@link RestClient} bound to {@code sourcer.blackboard.base-url}.
 *
 * <p>Mirrors 1-1 the REST surface listed in SKILL §3 of the {@code definition-blackboard-manager}
 * skill. {@code If-Match} ETag CAS is required on {@link #declarePanels} and {@link #seal}; callers
 * obtain the current ETag from a prior {@link EtaggedResponse}.
 */
@Component
public class DefinitionBlackboardClient {

  private static final ParameterizedTypeReference<List<Blackboard>> BLACKBOARD_LIST =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;
  private final String baseUrl;

  public DefinitionBlackboardClient(
      RestClient.Builder builder, @Value("${sourcer.blackboard.base-url}") String baseUrl) {
    this.baseUrl = baseUrl;
    this.restClient = builder.baseUrl(baseUrl).build();
  }

  public RestClient restClient() {
    return restClient;
  }

  public String baseUrl() {
    return baseUrl;
  }

  // ---------- Blackboard lifecycle ----------

  /** {@code POST /blackboards}. Returns the created Blackboard + initial ETag. */
  public EtaggedResponse<Blackboard> createBlackboard(BlackboardConfig config) {
    ResponseEntity<Blackboard> resp =
        restClient
            .post()
            .uri("/blackboards")
            .contentType(MediaType.APPLICATION_JSON)
            .body(config)
            .retrieve()
            .toEntity(Blackboard.class);
    return etagged(resp);
  }

  /** {@code GET /blackboards}. List visible Blackboards (server filters by tenant). */
  public List<Blackboard> listBlackboards() {
    return restClient.get().uri("/blackboards").retrieve().body(BLACKBOARD_LIST);
  }

  /** {@code GET /blackboards/{id}}. Returns the Blackboard + current ETag. */
  public EtaggedResponse<Blackboard> getBlackboard(String blackboardId) {
    ResponseEntity<Blackboard> resp =
        restClient
            .get()
            .uri("/blackboards/{id}", blackboardId)
            .retrieve()
            .toEntity(Blackboard.class);
    return etagged(resp);
  }

  /** {@code POST /blackboards/{id}/abort}. */
  public void abortBlackboard(String blackboardId) {
    restClient.post().uri("/blackboards/{id}/abort", blackboardId).retrieve().toBodilessEntity();
  }

  /** {@code POST /blackboards/{id}/seal}. {@code If-Match} ETag CAS is mandatory. */
  public EtaggedResponse<Blackboard> seal(String blackboardId, String ifMatch) {
    ResponseEntity<Blackboard> resp =
        restClient
            .post()
            .uri("/blackboards/{id}/seal", blackboardId)
            .header(HttpHeaders.IF_MATCH, ifMatch)
            .retrieve()
            .toEntity(Blackboard.class);
    return etagged(resp);
  }

  // ---------- Panels ----------

  /** {@code POST /blackboards/{id}/panels}. {@code If-Match} ETag CAS is mandatory. */
  public EtaggedResponse<PanelTopology> declarePanels(
      String blackboardId, PanelDeclaration body, String ifMatch) {
    ResponseEntity<PanelTopology> resp =
        restClient
            .post()
            .uri("/blackboards/{id}/panels", blackboardId)
            .header(HttpHeaders.IF_MATCH, ifMatch)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toEntity(PanelTopology.class);
    return etagged(resp);
  }

  /** {@code GET /blackboards/{id}/panels}. */
  public PanelTopology getPanels(String blackboardId) {
    return restClient
        .get()
        .uri("/blackboards/{id}/panels", blackboardId)
        .retrieve()
        .body(PanelTopology.class);
  }

  // ---------- Contributions ----------

  /** {@code POST /blackboards/{id}/contributions}. */
  public Contribution postContribution(String blackboardId, ContributionPost post) {
    return restClient
        .post()
        .uri("/blackboards/{id}/contributions", blackboardId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(post)
        .retrieve()
        .body(Contribution.class);
  }

  /**
   * {@code GET /blackboards/{id}/contributions}. {@code panel} and {@code slot} are optional
   * filters; pass {@code null} to omit.
   */
  public ContributionList listContributions(String blackboardId, String panel, String slot) {
    return restClient
        .get()
        .uri(
            (UriBuilder b) -> {
              UriBuilder builder = b.path("/blackboards/{id}/contributions");
              if (panel != null) {
                builder = builder.queryParam("panel", panel);
              }
              if (slot != null) {
                builder = builder.queryParam("slot", slot);
              }
              return builder.build(blackboardId);
            })
        .retrieve()
        .body(ContributionList.class);
  }

  /** {@code GET /blackboards/{id}/contributions/{cid}}. */
  public Contribution getContribution(String blackboardId, String contributionId) {
    return restClient
        .get()
        .uri("/blackboards/{id}/contributions/{cid}", blackboardId, contributionId)
        .retrieve()
        .body(Contribution.class);
  }

  // ---------- State / audit ----------

  /** {@code GET /blackboards/{id}/state}. Live (pre-seal) view. */
  public BlackboardState getState(String blackboardId) {
    return restClient
        .get()
        .uri("/blackboards/{id}/state", blackboardId)
        .retrieve()
        .body(BlackboardState.class);
  }

  /**
   * {@code GET /blackboards/{id}/audit}. Returned as raw JSON because the substrate's audit-entry
   * shape is intentionally unspecified at this iteration.
   */
  public JsonNode getAudit(String blackboardId) {
    return restClient
        .get()
        .uri("/blackboards/{id}/audit", blackboardId)
        .retrieve()
        .body(JsonNode.class);
  }

  // ---------- helpers ----------

  private static <T> EtaggedResponse<T> etagged(ResponseEntity<T> resp) {
    return new EtaggedResponse<>(resp.getBody(), resp.getHeaders().getETag());
  }
}
