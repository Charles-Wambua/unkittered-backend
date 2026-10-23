-- Step 2 of scripts/seed-test-users.py, run on the VM:
--   sudo -u postgres psql unkittered -f seed-test-users.sql
-- Approves the test accounts' verification selfies and gives two of them paid
-- tiers. Only touches @unkittered.test accounts.
\set ON_ERROR_STOP on
BEGIN;
UPDATE verifications SET status = 'verified', reviewed_at = now()
 WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@unkittered.test');
UPDATE profiles SET is_verified = true
 WHERE user_id IN (SELECT u.id FROM users u JOIN verifications v ON v.user_id = u.id
                    WHERE u.email LIKE '%@unkittered.test');
UPDATE users SET subscription_tier = 'gold' WHERE email = 'alex@unkittered.test';
UPDATE users SET subscription_tier = 'plus' WHERE email = 'daniel@unkittered.test';
COMMIT;
SELECT u.email, u.subscription_tier, p.is_verified
  FROM users u LEFT JOIN profiles p ON p.user_id = u.id
 WHERE u.email LIKE '%@unkittered.test' ORDER BY u.email;
