package cloud.poesis.itip.sourcer.ks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class KnowledgeSourceTest {

  @Test
  void abstractContractIsImplementable() {
    KnowledgeSource ks =
        new KnowledgeSource() {
          @Override
          public String getFqn() {
            return "test.Ks@1";
          }

          @Override
          public Set<String> getSourceContributionSlots() {
            return Set.of("in");
          }

          @Override
          public Set<String> getTargetContributionSlots() {
            return Set.of("out");
          }

          @Override
          public boolean isContributableBlackboard() {
            return true;
          }

          @Override
          public void contributeToBlackboard() {
            // no-op
          }
        };

    assertEquals("test.Ks@1", ks.getFqn());
    assertEquals(Set.of("in"), ks.getSourceContributionSlots());
    assertEquals(Set.of("out"), ks.getTargetContributionSlots());
    assertTrue(ks.isContributableBlackboard());
    ks.contributeToBlackboard();
    assertFalse(ks.getFqn().isEmpty());
  }
}
