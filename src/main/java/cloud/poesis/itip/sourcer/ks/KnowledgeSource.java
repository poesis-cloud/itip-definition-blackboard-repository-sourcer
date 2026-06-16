package cloud.poesis.itip.sourcer.ks;

import java.util.Set;

/**
 * Abstract contract for every Knowledge Source hosted by the sourcer JVM.
 *
 * <p>Mirrors the {@code KnowledgeSource} class on {@code def/components.puml}. Every one of the 18
 * spine KSs (6 subjects × 3 stages) extends this class.
 */
public abstract class KnowledgeSource {

  /** Fully qualified KS identifier ({@code <package>.<ClassName>@<version>}). */
  public abstract String getFqn();

  /** Upstream contribution slots this KS reads. */
  public abstract Set<String> getSourceContributionSlots();

  /** Contribution slots this KS writes to. */
  public abstract Set<String> getTargetContributionSlots();

  /** Gating predicate: whether the current blackboard state allows this KS to contribute now. */
  public abstract boolean isContributableBlackboard();

  /** Produce contributions and POST them to the blackboard via the substrate-facing service. */
  public abstract void contributeToBlackboard();
}
