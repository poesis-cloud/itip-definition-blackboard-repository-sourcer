package cloud.poesis.itip.sourcer.dto.blackboard;

/**
 * Server-owned Blackboard lifecycle state.
 *
 * <p>Mirrors {@code BlackboardStatus} from {@code
 * sie/sie-definition-blackboard-manager/def/blackboard/blackboard.puml}.
 */
public enum BlackboardStatus {
  OPEN,
  SEALING,
  SEALED,
  ABORTED
}
