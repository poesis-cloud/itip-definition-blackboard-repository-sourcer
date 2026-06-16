package cloud.poesis.itip.sourcer.type;

/**
 * The 6 GSM subjects sourced into a Definition Blackboard.
 *
 * <p>One enum constant per slot row in {@code def/components.puml}. The 18 spine KSs are organised
 * as 6 subjects × 3 stages.
 */
public enum Subject {
  STRUCTURE,
  MECHANISM,
  DATA_ARCHETYPE,
  EFFECTOR,
  RECEPTOR,
  INTERACTION
}
