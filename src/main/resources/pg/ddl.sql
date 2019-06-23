DROP TABLE IF EXISTS client_tokens;

CREATE TABLE client_tokens
(
    token VARCHAR(500) PRIMARY KEY,
    time  timestamp without time zone default now()
);