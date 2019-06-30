DROP TABLE IF EXISTS client_token;
DROP TABLE IF EXISTS clip;

CREATE TABLE client_token
(
    token VARCHAR(500) PRIMARY KEY,
    time  timestamp without time zone default now()
);

CREATE TABLE clip
(
    clip_id        VARCHAR(500) PRIMARY KEY,
    streamer_name  VARCHAR(500),
    broadcaster_id VARCHAR(500),
    full_link      VARCHAR(500),
    time           timestamp without time zone default now()
);

ALTER TABLE clip
    ADD COLUMN app_version VARCHAR(500);