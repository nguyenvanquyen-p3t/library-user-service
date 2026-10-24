-- Owner decision: apply only when RefreshTokens is empty. Never delete/revoke
-- existing tokens or invent sessions as part of this migration.
DO $$
BEGIN
    LOCK TABLE "RefreshTokens" IN ACCESS EXCLUSIVE MODE;
    IF EXISTS (SELECT 1 FROM "RefreshTokens") THEN
        RAISE EXCEPTION 'V6 requires an empty RefreshTokens table; no existing tokens were modified';
    END IF;
END;
$$;

CREATE TABLE "UserSessions" (
    "Id" UUID PRIMARY KEY,
    "UserId" INTEGER NOT NULL REFERENCES "Users" ("Id") ON DELETE CASCADE,
    "CreatedAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "ExpiresAt" TIMESTAMPTZ NOT NULL,
    "LastSeenAt" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "RevokedAt" TIMESTAMPTZ,
    "RevocationReason" VARCHAR(50),
    "DeviceName" VARCHAR(150),
    "ClientType" VARCHAR(20),
    "UserAgent" VARCHAR(1024),
    CONSTRAINT "UQ_UserSessions_Id_UserId" UNIQUE ("Id", "UserId"),
    CONSTRAINT "CK_UserSessions_ExpiresAt" CHECK ("ExpiresAt" > "CreatedAt"),
    CONSTRAINT "CK_UserSessions_LastSeenAt" CHECK ("LastSeenAt" >= "CreatedAt"),
    CONSTRAINT "CK_UserSessions_RevokedAt" CHECK ("RevokedAt" IS NULL OR "RevokedAt" >= "CreatedAt"),
    CONSTRAINT "CK_UserSessions_Reason" CHECK (
        ("RevokedAt" IS NULL AND "RevocationReason" IS NULL) OR
        ("RevokedAt" IS NOT NULL AND "RevocationReason" IS NOT NULL
         AND "RevocationReason" ~ '[^[:space:]]')),
    CONSTRAINT "CK_UserSessions_ClientType" CHECK ("ClientType" IN ('WEB', 'ANDROID'))
);

CREATE INDEX "IDX_UserSessions_UserId_CreatedAt" ON "UserSessions" ("UserId", "CreatedAt" DESC, "Id");
CREATE INDEX "IDX_UserSessions_ExpiresAt" ON "UserSessions" ("ExpiresAt");

ALTER TABLE "RefreshTokens"
    ADD COLUMN "SessionId" UUID NOT NULL,
    ADD CONSTRAINT "FK_RefreshTokens_SessionOwner"
        FOREIGN KEY ("SessionId", "UserId") REFERENCES "UserSessions" ("Id", "UserId"),
    ADD CONSTRAINT "UQ_RefreshTokens_Id_UserId_SessionId" UNIQUE ("Id", "UserId", "SessionId");

-- Keep the original same-user FK, and additionally enforce same-session rotation.
ALTER TABLE "RefreshTokens"
    ADD CONSTRAINT "FK_RefreshTokens_ReplacementSession"
        FOREIGN KEY ("ReplacedByTokenId", "UserId", "SessionId")
        REFERENCES "RefreshTokens" ("Id", "UserId", "SessionId")
        ON DELETE SET NULL ("ReplacedByTokenId");

CREATE INDEX "IDX_RefreshTokens_SessionId" ON "RefreshTokens" ("SessionId");

-- A token cannot be moved to a different session later.
CREATE FUNCTION enforce_refresh_token_session() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW."SessionId" IS NULL THEN
        RAISE EXCEPTION 'New refresh tokens require SessionId' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW."SessionId" IS DISTINCT FROM OLD."SessionId" THEN
        RAISE EXCEPTION 'Refresh token session is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER "TR_RefreshTokens_Session"
BEFORE INSERT OR UPDATE ON "RefreshTokens"
FOR EACH ROW EXECUTE FUNCTION enforce_refresh_token_session();
