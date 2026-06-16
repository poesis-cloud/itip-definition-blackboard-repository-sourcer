package cloud.poesis.itip.sourcer.controller;

import cloud.poesis.itip.sourcer.dto.RunSourcingRequest;
import cloud.poesis.itip.sourcer.dto.RunSourcingResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Control-plane REST API for triggering and inspecting sourcing runs.
 *
 * <p>Skeleton endpoint surface; orchestration over the 18 spine KSs is intentionally deferred to a
 * later iteration.
 */
@RestController
@RequestMapping("/api/v1/sourcing")
public class SourcingController {

  /** Accept a sourcing-run request. Returns synchronously; the run executes asynchronously. */
  @PostMapping("/runs")
  public ResponseEntity<RunSourcingResponse> startRun(@Valid @RequestBody RunSourcingRequest req) {
    String runId = UUID.randomUUID().toString();
    String blackboardId = req.blackboardId() == null ? "tbd" : req.blackboardId();
    return ResponseEntity.accepted().body(new RunSourcingResponse(runId, blackboardId));
  }
}
