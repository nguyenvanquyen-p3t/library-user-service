-- Flyway V5: Valid audit timestamps and database-managed Users.UpdatedAt.
-- Inserts retain the DEFAULT CURRENT_TIMESTAMP from V1.

ALTER TABLE "RefreshTokens"
    ADD CONSTRAINT "CK_RefreshTokens_RevokedAt_NotBeforeCreatedAt"
        CHECK ("RevokedAt" IS NULL OR "RevokedAt" >= "CreatedAt");

ALTER TABLE "VerificationCodes"
    ADD CONSTRAINT "CK_VerificationCodes_UsedAt_NotBeforeCreatedAt"
        CHECK ("UsedAt" IS NULL OR "UsedAt" >= "CreatedAt");

CREATE FUNCTION update_users_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    -- Ignore caller-supplied timestamps, including timestamp-only updates.
    NEW."UpdatedAt" := OLD."UpdatedAt";

    -- Compare every other column, including nullable fields and future fields.
    IF NEW IS DISTINCT FROM OLD THEN
        -- Use the statement time so separate updates in one transaction can
        -- receive distinct timestamps, unlike CURRENT_TIMESTAMP.
        NEW."UpdatedAt" := statement_timestamp();
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER "TR_Users_UpdateUpdatedAt"
BEFORE UPDATE ON "Users"
FOR EACH ROW
EXECUTE FUNCTION update_users_updated_at();
