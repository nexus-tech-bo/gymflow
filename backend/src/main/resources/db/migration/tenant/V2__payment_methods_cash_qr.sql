ALTER TABLE ${flyway:defaultSchema}.payments DROP CONSTRAINT IF EXISTS payments_method_check;

UPDATE ${flyway:defaultSchema}.payments SET method = 'QR' WHERE method IN ('YAPE', 'PLIN', 'CARD');

ALTER TABLE ${flyway:defaultSchema}.payments
    ADD CONSTRAINT payments_method_check CHECK (method IN ('CASH', 'QR'));
