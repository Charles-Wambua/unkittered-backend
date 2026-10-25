-- After a confirmed date, each person can say privately how it went. Only
-- they see their answer; a thumbs-down offers the report/block tools.
CREATE TABLE plan_feedback (
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    match_id   UUID        NOT NULL,
    plan_id    VARCHAR(64) NOT NULL,
    rating     VARCHAR(8)  NOT NULL CHECK (rating IN ('good', 'bad')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, plan_id)
);
CREATE INDEX idx_plan_feedback_match ON plan_feedback(match_id);
