package cloud.poesis.itip.sourcer.dto.blackboard;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Read view of a Blackboard's panel topology returned by {@code GET /blackboards/{id}/panels}.
 *
 * @param panels declared panels with their slot maps
 */
public record PanelTopology(List<PanelView> panels) {

  public PanelTopology {
    panels = panels == null ? null : List.copyOf(panels);
  }

  @Override
  public List<PanelView> panels() {
    return panels == null ? null : Collections.unmodifiableList(panels);
  }

  /**
   * Server view of one panel.
   *
   * @param id server-minted panel URI
   * @param name client-namespaced panel name
   * @param description panel description
   * @param slots slot name → {@link SlotView}
   */
  public record PanelView(URI id, String name, String description, Map<String, SlotView> slots) {
    public PanelView {
      slots = slots == null ? null : Map.copyOf(slots);
    }

    @Override
    public Map<String, SlotView> slots() {
      return slots == null ? null : Collections.unmodifiableMap(slots);
    }
  }

  /**
   * Server view of one slot.
   *
   * @param id server-minted slot URI
   * @param schemaUri JSON Schema URI used to validate contributions
   */
  public record SlotView(URI id, URI schemaUri) {}
}
