package cloud.poesis.itip.sourcer.dto.blackboard;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Body of {@code POST /blackboards/{id}/panels}: client-declared panel topology.
 *
 * <p>Topology is frozen as soon as the first contribution is accepted on the Blackboard. {@code
 * If-Match} ETag CAS is required on the request.
 *
 * @param panels closed list of panels declared on the Blackboard
 */
public record PanelDeclaration(List<PanelSpec> panels) {

  public PanelDeclaration {
    panels = panels == null ? null : List.copyOf(panels);
  }

  @Override
  public List<PanelSpec> panels() {
    return panels == null ? null : Collections.unmodifiableList(panels);
  }

  /**
   * One panel within a {@link PanelDeclaration}.
   *
   * @param name client-namespaced panel name (e.g. {@code itip:Definition})
   * @param description mandatory free-form documentation (substrate enforces non-empty)
   * @param slots closed slot map: slot name → {@link SlotSpec}
   */
  public record PanelSpec(String name, String description, Map<String, SlotSpec> slots) {
    public PanelSpec {
      slots = slots == null ? null : Map.copyOf(slots);
    }

    @Override
    public Map<String, SlotSpec> slots() {
      return slots == null ? null : Collections.unmodifiableMap(slots);
    }
  }

  /**
   * One slot declaration.
   *
   * @param schemaUri JSON Schema URI used by the substrate to validate {@code Contribution.post}
   *     for this slot
   */
  public record SlotSpec(URI schemaUri) {}
}
