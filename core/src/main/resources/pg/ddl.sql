DROP TABLE IF EXISTS bot_token;
DROP TABLE IF EXISTS clip;
DROP TABLE IF EXISTS clip_released;
DROP TABLE IF EXISTS release;
DROP TABLE IF EXISTS youtube_channel;
DROP TABLE IF EXISTS kv;

-- Таблица с токенами для бота
CREATE TABLE bot_token
(
    token VARCHAR(500) PRIMARY KEY,
    time  timestamp without time zone default now()
);

-- таблица клипов
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

CREATE table youtube_channel
(
    chan_name VARCHAR(500) PRIMARY KEY
);

CREATE table release
(
    youtube_video_id VARCHAR(500) PRIMARY KEY,
    youtube_link     VARCHAR(500),
    producer_version VARCHAR(500),
    youtube_chan     VARCHAR(500) NOT NULL REFERENCES youtube_channel (chan_name),
    time             timestamp without time zone default now()
);

CREATE TABLE clip_released
(
    clip_id             VARCHAR(500) PRIMARY KEY REFERENCES clip (clip_id),
    included_in_release VARCHAR(500) NOT NULL REFERENCES release (youtube_video_id)
);

ALTER TABLE clip
    ADD COLUMN title VARCHAR(500);

CREATE TABLE kv
(
    id    VARCHAR(500),
    key   VARCHAR(1000),
    value bytea,
    PRIMARY KEY (id, key)
);

-- Таблица пользователей
CREATE TABLE service_users(
    id SERIAL PRIMARY KEY,
    twitch_nick_name VARCHAR(255)
);

-- Токены пользователей
CREATE TABLE user_token(
    token VARCHAR(255) PRIMARY KEY ,
    relates_to_user SERIAL REFERENCES service_users(id)
);