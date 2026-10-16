-- Flyway V3: Canonical identifiers and non-blank user/authentication fields.
-- The backend must apply the same normalization before inserts and lookups.
-- Existing conflicting or invalid data causes this migration to fail rather
-- than silently merging accounts or removing whitespace inside identifiers.

UPDATE "Users"
SET "UserName" = lower(regexp_replace("UserName", '^[[:space:]]+|[[:space:]]+$', '', 'g')),
    "Email" = lower(regexp_replace("Email", '^[[:space:]]+|[[:space:]]+$', '', 'g')),
    "FullName" = regexp_replace("FullName", '^[[:space:]]+|[[:space:]]+$', '', 'g');

UPDATE "VerificationCodes"
SET "Email" = lower(regexp_replace("Email", '^[[:space:]]+|[[:space:]]+$', '', 'g'));

UPDATE "Roles"
SET "Name" = upper(regexp_replace("Name", '^[[:space:]]+|[[:space:]]+$', '', 'g'));

UPDATE "UserIdentities"
SET "Provider" = upper(regexp_replace("Provider", '^[[:space:]]+|[[:space:]]+$', '', 'g'));

-- Existing UNIQUE constraints remain sufficient because identifiers must
-- now be stored in lowercase. Whitespace follows PostgreSQL's POSIX class.
ALTER TABLE "Users"
    ADD CONSTRAINT "CK_Users_UserName_Lowercase"
        CHECK ("UserName" = lower("UserName")),
    ADD CONSTRAINT "CK_Users_UserName_NotEmpty_NoWhitespace"
        CHECK ("UserName" <> '' AND "UserName" !~ '[[:space:]]'),
    ADD CONSTRAINT "CK_Users_UserName_NoControlCharacters"
        CHECK ("UserName" !~ '[[:cntrl:]]'),
    -- A shared login field uses '@' to distinguish email from username.
    ADD CONSTRAINT "CK_Users_UserName_NoAtSign"
        CHECK (position('@' IN "UserName") = 0),
    ADD CONSTRAINT "CK_Users_Email_Lowercase"
        CHECK ("Email" = lower("Email")),
    ADD CONSTRAINT "CK_Users_Email_NotEmpty_NoWhitespace"
        CHECK ("Email" <> '' AND "Email" !~ '[[:space:]]'),
    ADD CONSTRAINT "CK_Users_Email_NoControlCharacters"
        CHECK ("Email" !~ '[[:cntrl:]]'),
    ADD CONSTRAINT "CK_Users_FullName_NotBlank"
        CHECK ("FullName" ~ '[^[:space:]]'),
    ADD CONSTRAINT "CK_Users_FullName_Trimmed"
        CHECK ("FullName" !~ '^[[:space:]]|[[:space:]]$'),
    ADD CONSTRAINT "CK_Users_FullName_NoControlCharacters"
        CHECK ("FullName" !~ '[[:cntrl:]]'),
    ADD CONSTRAINT "CK_Users_PasswordHash_NotBlank"
        CHECK ("PasswordHash" IS NULL OR "PasswordHash" ~ '[^[:space:]]');

ALTER TABLE "Roles"
    ADD CONSTRAINT "CK_Roles_Name_Uppercase"
        CHECK ("Name" = upper("Name")),
    ADD CONSTRAINT "CK_Roles_Name_NotEmpty_NoWhitespace"
        CHECK ("Name" <> '' AND "Name" !~ '[[:space:]]'),
    ADD CONSTRAINT "CK_Roles_Name_NoControlCharacters"
        CHECK ("Name" !~ '[[:cntrl:]]');

-- ProviderUserId is an opaque, case-sensitive external identifier.
-- Validate presence only; never trim or lowercase it, or any stored hash.
ALTER TABLE "UserIdentities"
    ADD CONSTRAINT "CK_UserIdentities_Provider_Uppercase"
        CHECK ("Provider" = upper("Provider")),
    ADD CONSTRAINT "CK_UserIdentities_Provider_NotEmpty_NoWhitespace"
        CHECK ("Provider" <> '' AND "Provider" !~ '[[:space:]]'),
    ADD CONSTRAINT "CK_UserIdentities_Provider_NoControlCharacters"
        CHECK ("Provider" !~ '[[:cntrl:]]'),
    ADD CONSTRAINT "CK_UserIdentities_ProviderUserId_NotBlank"
        CHECK ("ProviderUserId" ~ '[^[:space:]]');

ALTER TABLE "VerificationCodes"
    ADD CONSTRAINT "CK_VerificationCodes_Email_Lowercase"
        CHECK ("Email" = lower("Email")),
    ADD CONSTRAINT "CK_VerificationCodes_Email_NotEmpty_NoWhitespace"
        CHECK ("Email" <> '' AND "Email" !~ '[[:space:]]'),
    ADD CONSTRAINT "CK_VerificationCodes_Email_NoControlCharacters"
        CHECK ("Email" !~ '[[:cntrl:]]'),
    ADD CONSTRAINT "CK_VerificationCodes_CodeHash_NotBlank"
        CHECK ("CodeHash" ~ '[^[:space:]]');
