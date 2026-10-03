CREATE TABLE user_profile (
    user_id BIGINT NOT NULL,
    nickname VARCHAR(12),
    image BYTEA,
    image_updated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_profile PRIMARY KEY (user_id),
    CONSTRAINT fk_user_profile_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX uk_user_profile_nickname_lower ON user_profile (lower(nickname));
