-- Flyway V2: Required roles for User & Identity Service
-- Source: UserServiceDB.sql; original seed data preserved.

INSERT INTO "Roles" ("Name")
VALUES
    ('READER'),
    ('LIBRARIAN'),
    ('ADMIN');
