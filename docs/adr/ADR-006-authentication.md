# ADR-006: Authentication — API Key over JWT

**Status:** Accepted  
**Date:** 2026-06-24

## Context

Phase 10 adds authentication to the RAG API. Two options were evaluated:

**JWT (RS256, short-lived access tokens)**
- Industry-standard for user-facing APIs
- Requires a token issuance endpoint (`POST /auth/token`) or an external IdP (Keycloak, Auth0, Cognito)
- Supports fine-grained claims (userId, roles, expiry)
- Well-suited when a UI or multiple client types need stateful sessions

**API Key (`X-API-Key` header)**
- Pre-issued key stored in Secrets Manager; no issuance infrastructure needed
- Stateless validation: check if key is in configured set
- Supports key rotation without downtime (multiple valid keys simultaneously)
- Well-suited for machine-to-machine or developer API access

## Decision

Use **API key authentication** for Phase 10.

`ApiKeyAuthFilter` (Spring Security `OncePerRequestFilter`) reads the `X-API-Key` header and validates against `app.security.api-keys` (comma-separated, loaded from `${API_KEYS:}` environment variable — set via Secrets Manager in production).

**Dev-mode pass-through:** when `api-keys` is empty (the default), the filter passes all requests through. This means local development and all CI tests work without any key configuration.

**Key rotation:** add both old and new keys to the comma-separated list. Deploy. Remove the old key. No downtime.

**Swagger + Actuator:** permitted without auth — `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`, `/actuator/prometheus`, `/actuator/info`.

## Why Not JWT Now

1. **No UI, no user sessions.** Knowledge Assistant is a backend API. The primary consumers are developers or internal services. API keys are the conventional credential for this access pattern.

2. **No issuance infrastructure.** JWT requires either a `/auth` endpoint (who authenticates to get the token?) or an external IdP. Adding an IdP (Keycloak, Auth0) is a significant dependency that belongs in its own ADR and phase.

3. **Simpler security surface.** JWT validation requires key management (RS256 public key distribution), token expiry handling, refresh token rotation. API keys have none of this complexity.

## Upgrade Path to JWT

When a UI or multi-user access is needed:

1. Add an identity provider (Keycloak or Auth0) to the infrastructure
2. Replace `ApiKeyAuthFilter` with `JwtAuthFilter` (RS256 token validation via Spring Security OAuth2 Resource Server)
3. Add `spring-boot-starter-oauth2-resource-server` to `pom.xml`
4. Configure `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` in `application-prod.yml`

The `SecurityConfig` filter chain structure stays the same — only the filter changes.

## Consequences

**Good:**
- Zero new infrastructure (no IdP to run or pay for)
- Existing tests and local dev need no changes (dev-mode pass-through)
- Key rotation is operationally trivial
- Adding Swagger/Actuator to the permit list is straightforward

**Bad:**
- API keys don't expire automatically — rotation must be done manually
- No fine-grained claims — cannot distinguish which key belongs to which caller without out-of-band tracking
- Not suitable for end-user authentication (can't issue per-user keys at scale)
