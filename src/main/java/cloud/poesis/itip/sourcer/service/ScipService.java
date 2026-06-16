package cloud.poesis.itip.sourcer.service;

import org.springframework.stereotype.Service;

/**
 * Owns the parsed SCIP index (documents, symbols, occurrences, external symbols). Stateful,
 * in-memory, lazy-prime. Re-derivable from {@code provenance.indexedRevision} + {@code
 * provenance.tools[]}; never persisted.
 */
@Service
public class ScipService {}
