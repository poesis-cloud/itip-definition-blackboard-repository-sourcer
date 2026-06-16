package cloud.poesis.itip.sourcer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cloud.poesis.itip.sourcer.dto.RunSourcingRequest;
import cloud.poesis.itip.sourcer.dto.RunSourcingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class SourcingControllerTest {

  private final SourcingController controller = new SourcingController();

  @Test
  void startRunReturnsAcceptedWithGeneratedRunIdWhenNoBlackboardIdProvided() {
    ResponseEntity<RunSourcingResponse> response =
        controller.startRun(new RunSourcingRequest("git://repo", "rev", null));
    assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    assertNotNull(response.getBody());
    assertNotNull(response.getBody().runId());
    assertEquals("tbd", response.getBody().blackboardId());
  }

  @Test
  void startRunEchoesProvidedBlackboardId() {
    ResponseEntity<RunSourcingResponse> response =
        controller.startRun(new RunSourcingRequest("git://repo", "rev", "bb-42"));
    assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("bb-42", response.getBody().blackboardId());
  }
}
