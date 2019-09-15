DROP TABLE IF EXISTS chat_message;

CREATE table chat_message
(
    text                text                        NOT NULL,
    twitch_channel_name VARCHAR(255)                NOT NULL,
    author              VARCHAR(255)                NOT NULL,
    received_at         timestamp without time zone NOT NULL,
    is_highlight        boolean                     NOT NULL,
    record_session      VARCHAR(255)                NOT NULL
);