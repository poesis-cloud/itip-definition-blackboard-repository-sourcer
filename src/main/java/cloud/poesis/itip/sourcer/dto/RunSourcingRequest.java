package cloud.poesis.itip.sourcer.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to start a sourcing run over a code repository.
 *
 * @param repositoryUri the repository to source (git URL or local file URI)
 * @param revision the revision to index (typically a commit sha)
 * @param blackboardId optional pre-existing Blackboard id; if {@code null}, the sourcer creates a
 *     new Blackboard via the Definition Blackboard Manager
 */
public record RunSourcingRequest(
    @NotBlank String repositoryUri, @NotBlank String revision, String blackboardId) {}
