package cloud.poesis.itip.sourcer.dto.blackboard;

import java.util.Collections;
import java.util.List;

/**
 * Result of {@code GET /blackboards/{id}/contributions}.
 *
 * <p>While the Blackboard is {@link BlackboardStatus#OPEN}, the order is unspecified; once {@link
 * BlackboardStatus#SEALED}, the substrate guarantees the byte-stable ordering by {@code (panel,
 * slot, timestamp, id)} (see SKILL §5).
 *
 * @param contributions returned contributions (unpaginated wrapper; pagination semantics are
 *     substrate-defined and not asserted here)
 */
public record ContributionList(List<Contribution> contributions) {
  public ContributionList {
    contributions = contributions == null ? null : List.copyOf(contributions);
  }

  @Override
  public List<Contribution> contributions() {
    return contributions == null ? null : Collections.unmodifiableList(contributions);
  }
}
