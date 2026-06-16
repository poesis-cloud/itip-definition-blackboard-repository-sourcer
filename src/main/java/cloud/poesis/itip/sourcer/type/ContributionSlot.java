package cloud.poesis.itip.sourcer.type;

import java.util.Objects;

/**
 * A typed reference to one of the 18 blackboard slots ({@link Stage} × {@link Subject}).
 *
 * <p>The canonical wire form is {@code <panel>.<SubjectPascal><SlotSuffix>}, e.g. {@code
 * itip:Definition.StructureIdentity}.
 */
public record ContributionSlot(Stage stage, Subject subject) {

  public ContributionSlot {
    Objects.requireNonNull(stage, "stage");
    Objects.requireNonNull(subject, "subject");
  }

  /** Canonical slot identifier as exchanged with the Definition Blackboard Manager. */
  public String qualifiedName() {
    return stage.panel() + "." + subjectPascal() + stage.slotSuffix();
  }

  private String subjectPascal() {
    String name = subject.name();
    StringBuilder out = new StringBuilder(name.length());
    boolean capitalizeNext = true;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '_') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        out.append(c);
        capitalizeNext = false;
      } else {
        out.append(Character.toLowerCase(c));
      }
    }
    return out.toString();
  }
}
