-- Flyway V4: Non-blank token hashes and linear, same-user replacement chains.
-- Rotation still requires a backend transaction and concurrency control.

ALTER TABLE "RefreshTokens"
    ADD CONSTRAINT "UQ_RefreshTokens_Id_UserId"
        UNIQUE ("Id", "UserId"),
    -- Multiple NULLs remain allowed for tokens without a replacement.
    ADD CONSTRAINT "UQ_RefreshTokens_ReplacedByTokenId"
        UNIQUE ("ReplacedByTokenId"),
    ADD CONSTRAINT "CK_RefreshTokens_TokenHash_NotBlank"
        CHECK ("TokenHash" ~ '[^[:space:]]'),
    -- Every edge points to a larger identity value, so cycles are impossible
    -- even across concurrent statements. Rotation must insert a fresh token;
    -- do not reset the identity sequence or reuse an earlier token as a successor.
    ADD CONSTRAINT "CK_RefreshTokens_ReplacementMovesForward"
        CHECK ("ReplacedByTokenId" IS NULL OR "ReplacedByTokenId" > "Id");

ALTER TABLE "RefreshTokens"
    DROP CONSTRAINT "FK_RefreshTokens_ReplacedByToken",
    ADD CONSTRAINT "FK_RefreshTokens_ReplacedByToken"
        FOREIGN KEY ("ReplacedByTokenId", "UserId")
        REFERENCES "RefreshTokens" ("Id", "UserId")
        -- PostgreSQL 15+: retain the owner when the replacement is deleted.
        ON DELETE SET NULL ("ReplacedByTokenId"),
    ADD CONSTRAINT "CK_RefreshTokens_ReplacementRequiresRevocation"
        CHECK ("ReplacedByTokenId" IS NULL OR "RevokedAt" IS NOT NULL);
