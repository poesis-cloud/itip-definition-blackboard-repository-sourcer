package cloud.poesis.itip.sourcer.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DtoTest {

  @Test
  void runSourcingRequestExposesComponents() {
    RunSourcingRequest req = new RunSourcingRequest("git@example.com:repo.git", "abc123", "bb-1");
    assertEquals("git@example.com:repo.git", req.repositoryUri());
    assertEquals("abc123", req.revision());
    assertEquals("bb-1", req.blackboardId());
  }

  @Test
  void runSourcingRequestAcceptsNullBlackboardId() {
    RunSourcingRequest req = new RunSourcingRequest("uri", "rev", null);
    assertNull(req.blackboardId());
  }

  @Test
  void runSourcingResponseExposesComponents() {
    RunSourcingResponse rsp = new RunSourcingResponse("run-1", "bb-1");
    assertEquals("run-1", rsp.runId());
    assertEquals("bb-1", rsp.blackboardId());
  }
}
