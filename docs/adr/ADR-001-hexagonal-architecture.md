# ADR-001: Hexagonal Architecture (Ports & Adapters)

**Status:** Accepted  
**Date:** 2026-06-10

## Context

We need an architecture that:
1. Allows swapping LLM providers without touching business logic
2. Keeps domain logic testable without standing up Spring context
3. Prevents framework code from leaking into the core domain

## Decision

Adopt Hexagonal Architecture with strict layer boundaries enforced by ArchUnit:

```
domain/           ← pure Java, zero framework imports
application/      ← use cases, may import domain + Spring @Service/@Transactional only
api/              ← Spring MVC controllers, DTOs, filters
infrastructure/   ← Spring beans, JPA entities, AWS SDK, HTTP clients
```

Ports (interfaces) live in `domain/port/`. Adapters (implementations) live in `infrastructure/`.

## Consequences

**Good:**
- Domain tests run in milliseconds — no Spring context needed
- Adding a new LLM provider = implement `LlmPort`, add `@ConditionalOnProperty` — zero domain changes
- ArchUnit enforces boundaries in CI — architectural drift is caught automatically

**Bad:**
- More files and indirection than a layered architecture
- Junior engineers need orientation — the "which layer does this go in?" question comes up frequently
- DTOs at boundaries create mapping boilerplate; mitigated by keeping records flat (no deep object graphs)

## Alternatives Considered

- **Traditional layered architecture:** Simpler, but framework bleeds into service layer. LLM provider swap requires touching ChatService.
- **CQRS:** Overkill for this scale. Worth considering if read/write load diverges significantly.
