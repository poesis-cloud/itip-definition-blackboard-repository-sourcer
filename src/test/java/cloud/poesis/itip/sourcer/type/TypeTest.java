package cloud.poesis.itip.sourcer.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TypeTest {

  @Test
  void subjectEnumExposesSixConstants() {
    assertEquals(6, Subject.values().length);
    assertNotNull(Subject.valueOf("STRUCTURE"));
    assertNotNull(Subject.valueOf("INTERACTION"));
  }

  @Test
  void stageEnumExposesPanelAndSlotSuffix() {
    assertEquals(3, Stage.values().length);
    assertEquals("itip:Definition", Stage.IDENTIFICATION.panel());
    assertEquals("Identity", Stage.IDENTIFICATION.slotSuffix());
    assertEquals("itip:Archetype", Stage.ARCHETYPING.panel());
    assertEquals("Archetype", Stage.ARCHETYPING.slotSuffix());
    assertEquals("itip:Statement", Stage.STATEMENT.panel());
    assertEquals("Statement", Stage.STATEMENT.slotSuffix());
    assertNotNull(Stage.valueOf("STATEMENT"));
  }

  @Test
  void contributionSlotProducesCanonicalName() {
    assertEquals(
        "itip:Definition.StructureIdentity",
        new ContributionSlot(Stage.IDENTIFICATION, Subject.STRUCTURE).qualifiedName());
    assertEquals(
        "itip:Archetype.DataArchetypeArchetype",
        new ContributionSlot(Stage.ARCHETYPING, Subject.DATA_ARCHETYPE).qualifiedName());
    assertEquals(
        "itip:Statement.InteractionStatement",
        new ContributionSlot(Stage.STATEMENT, Subject.INTERACTION).qualifiedName());
  }

  @Test
  void contributionSlotRejectsNulls() {
    assertThrows(NullPointerException.class, () -> new ContributionSlot(null, Subject.STRUCTURE));
    assertThrows(
        NullPointerException.class, () -> new ContributionSlot(Stage.IDENTIFICATION, null));
  }

  @Test
  void contributionSlotAccessorsReturnComponents() {
    ContributionSlot slot = new ContributionSlot(Stage.STATEMENT, Subject.MECHANISM);
    assertEquals(Stage.STATEMENT, slot.stage());
    assertEquals(Subject.MECHANISM, slot.subject());
  }
}
