-- Phase 1 baseline: enable the pgvector extension.
--
-- This must be the first migration. All subsequent migrations that create
-- tables with vector() columns depend on this extension being present.
--
-- pgvector/pgvector:pg16 Docker image pre-installs the shared library,
-- but the extension must still be explicitly enabled per database.
-- On RDS, the extension is available but requires superuser or rds_superuser role.

CREATE EXTENSION IF NOT EXISTS vector;

-- uuid-ossp: provides gen_random_uuid() for UUID primary keys.
-- PostgreSQL 14+ has gen_random_uuid() built-in, but the extension is
-- included for compatibility and explicit documentation of the dependency.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
