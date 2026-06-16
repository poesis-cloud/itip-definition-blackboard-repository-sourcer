package cloud.poesis.itip.sourcer.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cloud.poesis.itip.sourcer.dto.blackboard.Blackboard;
import cloud.poesis.itip.sourcer.dto.blackboard.BlackboardConfig;
import cloud.poesis.itip.sourcer.dto.blackboard.BlackboardState;
import cloud.poesis.itip.sourcer.dto.blackboard.BlackboardStatus;
import cloud.poesis.itip.sourcer.dto.blackboard.Contribution;
import cloud.poesis.itip.sourcer.dto.blackboard.ContributionList;
import cloud.poesis.itip.sourcer.dto.blackboard.ContributionPost;
import cloud.poesis.itip.sourcer.dto.blackboard.EtaggedResponse;
import cloud.poesis.itip.sourcer.dto.blackboard.PanelDeclaration;
import cloud.poesis.itip.sourcer.dto.blackboard.PanelTopology;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DefinitionBlackboardClientTest {

  private static final String BASE = "http://blackboard.local:8080";
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

  private MockRestServiceServer server;
  private DefinitionBlackboardClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client = new DefinitionBlackboardClient(builder, BASE);
  }

  @Test
  void wrapsRestClientBoundToConfiguredBaseUrl() {
    assertEquals(BASE, client.baseUrl());
    assertNotNull(client.restClient());
  }

  @Test
  void createBlackboardPostsConfigAndReturnsBodyAndEtag() throws Exception {
    String body =
        JSON.writeValueAsString(
            new Blackboard(URI.create("bb://x/1"), "n", null, BlackboardStatus.OPEN, null));
    server
        .expect(requestTo(BASE + "/blackboards"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.name").value("n"))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON).headers(etag("\"v1\"")));

    EtaggedResponse<Blackboard> resp = client.createBlackboard(new BlackboardConfig("n"));

    assertEquals("\"v1\"", resp.etag());
    assertEquals(URI.create("bb://x/1"), resp.body().id());
    assertEquals(BlackboardStatus.OPEN, resp.body().status());
    server.verify();
  }

  @Test
  void listBlackboardsParsesArray() throws Exception {
    String body =
        JSON.writeValueAsString(
            List.of(
                new Blackboard(URI.create("bb://x/1"), "a", null, BlackboardStatus.OPEN, null)));
    server
        .expect(requestTo(BASE + "/blackboards"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    List<Blackboard> list = client.listBlackboards();

    assertEquals(1, list.size());
    assertEquals("a", list.get(0).name());
    server.verify();
  }

  @Test
  void getBlackboardReturnsEtag() throws Exception {
    String body =
        JSON.writeValueAsString(
            new Blackboard(URI.create("bb://x/1"), "a", null, BlackboardStatus.OPEN, null));
    server
        .expect(requestTo(BASE + "/blackboards/1"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON).headers(etag("\"v2\"")));

    EtaggedResponse<Blackboard> resp = client.getBlackboard("1");

    assertEquals("\"v2\"", resp.etag());
    server.verify();
  }

  @Test
  void abortBlackboardPostsAbort() {
    server
        .expect(requestTo(BASE + "/blackboards/1/abort"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess());

    client.abortBlackboard("1");
    server.verify();
  }

  @Test
  void sealRequiresIfMatchAndReturnsEtag() throws Exception {
    String body =
        JSON.writeValueAsString(
            new Blackboard(URI.create("bb://x/1"), "a", null, BlackboardStatus.SEALED, null));
    server
        .expect(requestTo(BASE + "/blackboards/1/seal"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("If-Match", "\"v1\""))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON).headers(etag("\"v2\"")));

    EtaggedResponse<Blackboard> resp = client.seal("1", "\"v1\"");

    assertEquals(BlackboardStatus.SEALED, resp.body().status());
    assertEquals("\"v2\"", resp.etag());
    server.verify();
  }

  @Test
  void declarePanelsRequiresIfMatchAndReturnsEtag() throws Exception {
    PanelDeclaration body =
        new PanelDeclaration(
            List.of(
                new PanelDeclaration.PanelSpec(
                    "itip:Definition",
                    "definitions",
                    Map.of(
                        "ClaimContribution",
                        new PanelDeclaration.SlotSpec(
                            URI.create("bb://itip/x/Definition/ClaimContribution/v1"))))));
    String resp =
        JSON.writeValueAsString(
            new PanelTopology(
                List.of(
                    new PanelTopology.PanelView(
                        URI.create("bb://x/p/1"), "itip:Definition", "definitions", Map.of()))));
    server
        .expect(requestTo(BASE + "/blackboards/1/panels"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("If-Match", "\"v1\""))
        .andRespond(withSuccess(resp, MediaType.APPLICATION_JSON).headers(etag("\"v2\"")));

    EtaggedResponse<PanelTopology> r = client.declarePanels("1", body, "\"v1\"");

    assertEquals("\"v2\"", r.etag());
    assertEquals(1, r.body().panels().size());
    server.verify();
  }

  @Test
  void getPanelsReturnsTopology() throws Exception {
    String body =
        JSON.writeValueAsString(
            new PanelTopology(
                List.of(
                    new PanelTopology.PanelView(
                        URI.create("bb://x/p/1"), "itip:Definition", "d", Map.of()))));
    server
        .expect(requestTo(BASE + "/blackboards/1/panels"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    PanelTopology t = client.getPanels("1");

    assertEquals("itip:Definition", t.panels().get(0).name());
    server.verify();
  }

  @Test
  void postContributionPostsBodyAndDeserializesContribution() throws Exception {
    JsonNode payload = JsonNodeFactory.instance.objectNode().put("k", "v");
    Contribution returned =
        new Contribution(
            URI.create("bb://x/c/1"),
            URI.create("bb://x/p/1/s/1"),
            payload,
            null,
            Instant.parse("2026-01-01T00:00:00Z"));
    server
        .expect(requestTo(BASE + "/blackboards/1/contributions"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.panel").value("itip:Definition"))
        .andExpect(jsonPath("$.slot").value("X"))
        .andRespond(withSuccess(JSON.writeValueAsString(returned), MediaType.APPLICATION_JSON));

    Contribution got =
        client.postContribution("1", new ContributionPost("itip:Definition", "X", payload));

    assertEquals(returned.id(), got.id());
    assertNull(got.derivedContribution());
    server.verify();
  }

  @Test
  void listContributionsAddsQueryParamsWhenProvided() throws Exception {
    String body = JSON.writeValueAsString(new ContributionList(List.<Contribution>of()));
    server
        .expect(requestTo(BASE + "/blackboards/1/contributions?panel=p&slot=s"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    ContributionList r = client.listContributions("1", "p", "s");

    assertEquals(0, r.contributions().size());
    server.verify();
  }

  @Test
  void listContributionsOmitsQueryParamsWhenNull() throws Exception {
    String body = JSON.writeValueAsString(new ContributionList(List.<Contribution>of()));
    server
        .expect(requestTo(BASE + "/blackboards/1/contributions"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    ContributionList r = client.listContributions("1", null, null);

    assertEquals(0, r.contributions().size());
    server.verify();
  }

  @Test
  void getContributionByIdResolvesPath() throws Exception {
    Contribution c =
        new Contribution(
            URI.create("bb://x/c/9"),
            URI.create("bb://x/p/1/s/1"),
            JsonNodeFactory.instance.objectNode(),
            null,
            Instant.parse("2026-01-01T00:00:00Z"));
    server
        .expect(requestTo(BASE + "/blackboards/1/contributions/9"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(JSON.writeValueAsString(c), MediaType.APPLICATION_JSON));

    Contribution got = client.getContribution("1", "9");

    assertEquals(c.id(), got.id());
    server.verify();
  }

  @Test
  void getStateReturnsLiveView() throws Exception {
    String body = JSON.writeValueAsString(new BlackboardState(List.<Contribution>of()));
    server
        .expect(requestTo(BASE + "/blackboards/1/state"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    BlackboardState s = client.getState("1");

    assertEquals(0, s.contributions().size());
    server.verify();
  }

  @Test
  void getAuditReturnsRawJsonNode() {
    server
        .expect(requestTo(BASE + "/blackboards/1/audit"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("[{\"e\":1}]", MediaType.APPLICATION_JSON));

    JsonNode node = client.getAudit("1");

    assertEquals(1, node.size());
    server.verify();
  }

  private static HttpHeaders etag(String tag) {
    HttpHeaders h = new HttpHeaders();
    h.setETag(tag);
    return h;
  }
}
