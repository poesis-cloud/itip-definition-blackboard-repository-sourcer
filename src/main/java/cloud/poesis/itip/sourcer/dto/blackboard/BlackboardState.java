package cloud.poesis.itip.sourcer.dto.blackboard;

import java.util.Collections;
import java.util.List;

/**
 * Live (pre-seal) view of posted contributions returned by {@code GET /blackboards/{id}/state}.
 *
 * <p>Identical wire shape as {@link ContributionList}; kept as a distinct type so callers can't
 * accidentally treat a live view as the byte-stable sealed stream.
 *
 * @param contributions contributions accepted so far
 */
public record BlackboardState(List<Contribution> contributions) {
  public BlackboardState {
    contributions = contributions == null ? null : List.copyOf(contributions);
  }

  @Override
  public List<Contribution> contributions() {
    return contributions == null ? null : Collections.unmodifiableList(contributions);
  }
}
