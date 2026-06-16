package cloud.poesis.itip.sourcer.type;

/**
 * The 3 stages of the sourcing DAG.
 *
 * <p>Stages map 1-1 to the 3 blackboard panels declared in {@code def/components.puml}:
 *
 * <ul>
 *   <li>{@link #IDENTIFICATION} writes {@code itip:Definition.*Identity}
 *   <li>{@link #ARCHETYPING} writes {@code itip:Archetype.*Archetype}
 *   <li>{@link #STATEMENT} writes {@code itip:Statement.*Statement}
 * </ul>
 */
public enum Stage {
  IDENTIFICATION("itip:Definition", "Identity"),
  ARCHETYPING("itip:Archetype", "Archetype"),
  STATEMENT("itip:Statement", "Statement");

  private final String panel;
  private final String slotSuffix;

  Stage(String panel, String slotSuffix) {
    this.panel = panel;
    this.slotSuffix = slotSuffix;
  }

  public String panel() {
    return panel;
  }

  public String slotSuffix() {
    return slotSuffix;
  }
}
