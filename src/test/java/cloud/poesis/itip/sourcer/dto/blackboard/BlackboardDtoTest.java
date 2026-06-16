package cloud.poesis.itip.sourcer.dto.blackboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlackboardDtoTest {

  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

  @Test
  void blackboardConfigSingleArgConstructorOmitsAuth() {
    BlackboardConfig c = new BlackboardConfig("n");
    assertEquals("n", c.name());
    assertNull(c.auth());
  }

  @Test
  void blackboardConfigCarriesNameAndAuth() {
    BlackboardConfig c = new BlackboardConfig("n", "a");
    assertEquals("n", c.name());
    assertEquals("a", c.auth());
  }

  @Test
  void blackboardCarriesAllFields() {
    Instant t = Instant.parse("2026-01-01T00:00:00Z");
    Blackboard b = new Blackboard(URI.create("bb://x/1"), "n", t, BlackboardStatus.SEALED, t);
    assertEquals(URI.create("bb://x/1"), b.id());
    assertEquals("n", b.name());
    assertEquals(t, b.timestamp());
    assertEquals(BlackboardStatus.SEALED, b.status());
    assertEquals(t, b.sealTimestamp());
  }

  @Test
  void blackboardStatusEnumValuesArePresent() {
    assertEquals(4, BlackboardStatus.values().length);
    assertEquals(BlackboardStatus.OPEN, BlackboardStatus.valueOf("OPEN"));
  }

  @Test
  void panelDeclarationCarriesPanelsAndSlots() {
    PanelDeclaration.SlotSpec slot =
        new PanelDeclaration.SlotSpec(URI.create("bb://itip/x/P/S/v1"));
    PanelDeclaration.PanelSpec p =
        new PanelDeclaration.PanelSpec("itip:Definition", "d", Map.of("S", slot));
    PanelDeclaration decl = new PanelDeclaration(List.of(p));
    assertEquals(1, decl.panels().size());
    assertEquals("itip:Definition", decl.panels().get(0).name());
    assertEquals("d", decl.panels().get(0).description());
    assertEquals(URI.create("bb://itip/x/P/S/v1"), slot.schemaUri());
  }

  @Test
  void panelTopologyCarriesPanelsAndSlotViews() {
    PanelTopology.SlotView sv =
        new PanelTopology.SlotView(URI.create("bb://x/s/1"), URI.create("bb://x/sch/1"));
    PanelTopology.PanelView pv =
        new PanelTopology.PanelView(
            URI.create("bb://x/p/1"), "itip:Definition", "d", Map.of("S", sv));
    PanelTopology t = new PanelTopology(List.of(pv));
    assertEquals(1, t.panels().size());
    assertEquals(URI.create("bb://x/p/1"), t.panels().get(0).id());
    assertEquals(URI.create("bb://x/sch/1"), sv.schemaUri());
    assertEquals(URI.create("bb://x/s/1"), sv.id());
  }

  @Test
  void contributionPostShortConstructorOmitsDerivedContribution() {
    JsonNode n = JsonNodeFactory.instance.objectNode().put("k", "v");
    ContributionPost p = new ContributionPost("itip:Definition", "X", n);
    assertEquals("itip:Definition", p.panel());
    assertEquals("X", p.slot());
    assertEquals(n, p.post());
    assertNull(p.derivedContribution());
  }

  @Test
  void contributionPostFullConstructorCarriesDerivedContribution() {
    JsonNode n = JsonNodeFactory.instance.objectNode();
    URI parent = URI.create("bb://x/c/1");
    ContributionPost p = new ContributionPost("P", "S", n, List.of(parent));
    assertEquals(List.of(parent), p.derivedContribution());
  }

  @Test
  void contributionRecordCarriesAllFields() {
    Instant t = Instant.parse("2026-01-01T00:00:00Z");
    JsonNode n = JsonNodeFactory.instance.objectNode();
    Contribution c =
        new Contribution(
            URI.create("bb://x/c/1"),
            URI.create("bb://x/p/1/s/1"),
            n,
            List.of(URI.create("bb://x/c/0")),
            t);
    assertEquals(URI.create("bb://x/c/1"), c.id());
    assertEquals(URI.create("bb://x/p/1/s/1"), c.contributionSlot());
    assertEquals(n, c.post());
    assertEquals(1, c.derivedContribution().size());
    assertEquals(t, c.timestamp());
  }

  @Test
  void contributionListAndStateAreThinWrappers() {
    ContributionList l = new ContributionList(List.of());
    BlackboardState s = new BlackboardState(List.of());
    assertNotNull(l.contributions());
    assertNotNull(s.contributions());
  }

  @Test
  void etaggedResponseCarriesBodyAndEtag() {
    EtaggedResponse<String> e = new EtaggedResponse<>("body", "\"v1\"");
    assertEquals("body", e.body());
    assertEquals("\"v1\"", e.etag());
  }

  @Test
  void blackboardConfigJsonOmitsNullAuth() throws Exception {
    String s = JSON.writeValueAsString(new BlackboardConfig("n"));
    assertEquals("{\"name\":\"n\"}", s);
  }
}
