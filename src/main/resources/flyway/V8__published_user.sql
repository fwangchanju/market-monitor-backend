INSERT INTO users (id, issuer, sub, role)
OVERRIDING SYSTEM VALUE
VALUES (900000, 'marketry-system', 'published', 'USER')
ON CONFLICT (id) DO NOTHING;
