-- Application SQL
-- A ? is a value supplied by a Java PreparedStatement. Do not run this file
-- directly in the MySQL prompt. Use database_setup.sql to create the demo.
-- URLs below use the normal app port, 8081. Browser checks used port 18081.
-- Current user IDs come from the login session, not a user ID submitted by a form.
-- For changing rows, the POST endpoint is followed by the page shown afterward.

-- Set each pooled connection to UTC. All app pages display Eastern Time.
-- Used when the app opens a database connection (all database-backed URLs).
SET time_zone = '+00:00';

-- Register an account. Java hashes the password before supplying it here.
-- Page: http://localhost:8081/register
-- Values: username, password hash, first name, last name.
INSERT INTO user (username, password, firstName, lastName) VALUES (?, ?, ?, ?);

-- Find the account during login. Java compares the supplied password to its hash.
-- Page: http://localhost:8081/login
SELECT * FROM user WHERE username = ?;

-- Save a post. The author ID is the logged-in user. Java reads the generated ID.
-- POST http://localhost:8081/createpost -> page http://localhost:8081/
INSERT INTO post (userId, content, timestamp) VALUES (?, ?, CURRENT_TIMESTAMP);

-- Store each lowercase hashtag once, even when several posts use it.
-- POST http://localhost:8081/createpost -> page http://localhost:8081/
INSERT IGNORE INTO hashtag (hashtag) VALUES (?);

-- Connect the newly created post to each of its distinct hashtags.
-- POST http://localhost:8081/createpost -> page http://localhost:8081/
INSERT INTO post_hashtag (postId, hashtag) VALUES (?, ?);

-- Find the recipient of an @username mention in a new post.
-- POST http://localhost:8081/createpost -> page http://localhost:8081/
SELECT userId FROM user WHERE username = ?;

-- Select the logged-in user's posts and posts from people they follow.
-- Page: http://localhost:8081/
-- Values: logged-in user ID twice.
SELECT p.postId, p.content, p.timestamp, u.userId, u.firstName, u.lastName
FROM post p JOIN user u ON u.userId = p.userId
WHERE p.userId = ?
 OR EXISTS (SELECT 1 FROM user_follow f WHERE f.followerId = ? AND f.followeeId = p.userId)
ORDER BY p.timestamp DESC, p.postId DESC;

-- Load counts and viewer states for the home page's selected posts.
-- Page: http://localhost:8081/
-- Values: viewer ID twice, then each selected post ID.
-- IN has one ? for each selected ID. Java keeps the feed's original order.
SELECT p.postId, p.userId, p.content, p.timestamp, u.firstName, u.lastName,
 (SELECT COUNT(*) FROM likes l WHERE l.postId = p.postId) AS likesCount,
 (SELECT COUNT(*) FROM comment c WHERE c.postId = p.postId) AS commentsCount,
 EXISTS(SELECT 1 FROM likes l WHERE l.postId = p.postId AND l.userId = ?) AS isLiked,
 EXISTS(SELECT 1 FROM bookmarks b WHERE b.postId = p.postId AND b.userId = ?) AS isBookmarked
FROM post p JOIN user u ON p.userId = u.userId
WHERE p.postId IN (?, ?, ?);

-- Load only one user's posts, with the logged-in viewer's like/bookmark states.
-- Pages: http://localhost:8081/profile and http://localhost:8081/profile/2
-- Values: viewer ID twice, then profile author's ID.
SELECT p.postId, p.userId, p.content, p.timestamp, u.firstName, u.lastName,
 (SELECT COUNT(*) FROM likes l WHERE l.postId = p.postId) AS likesCount,
 (SELECT COUNT(*) FROM comment c WHERE c.postId = p.postId) AS commentsCount,
 EXISTS(SELECT 1 FROM likes l WHERE l.postId = p.postId AND l.userId = ?) AS isLiked,
 EXISTS(SELECT 1 FROM bookmarks b WHERE b.postId = p.postId AND b.userId = ?) AS isBookmarked
FROM post p JOIN user u ON p.userId = u.userId
WHERE p.userId = ?
ORDER BY p.timestamp DESC, p.postId DESC;

-- Show every other user, their follow state, and their most recent post time.
-- Page: http://localhost:8081/people
-- Values: logged-in user ID twice. No post time becomes Unknown in Java.
SELECT u.userId, u.username, u.firstName, u.lastName,
 (SELECT MAX(p.timestamp) FROM post p WHERE p.userId = u.userId) AS lastPost,
 EXISTS(SELECT 1 FROM user_follow f WHERE f.followerId = ? AND f.followeeId = u.userId) AS isFollowed
FROM user u WHERE u.userId <> ?
ORDER BY u.firstName, u.lastName, u.userId;

-- Follow a user. A duplicate pair is treated as an already completed follow.
-- POST http://localhost:8081/people/2/follow/true -> page http://localhost:8081/people
-- Values: logged-in follower ID, selected followee ID.
INSERT INTO user_follow (followerId, followeeId) VALUES (?, ?);

-- Unfollow a user; a missing pair is already in the requested state.
-- POST http://localhost:8081/people/2/follow/false -> page http://localhost:8081/people
DELETE FROM user_follow WHERE followerId = ? AND followeeId = ?;

-- List only this viewer's bookmarked posts, newest post first.
-- Page: http://localhost:8081/bookmarks
-- Values: logged-in viewer ID three times.
SELECT p.postId, p.userId, p.content, p.timestamp, u.firstName, u.lastName,
 (SELECT COUNT(*) FROM likes l WHERE l.postId = p.postId) AS likesCount,
 (SELECT COUNT(*) FROM comment c WHERE c.postId = p.postId) AS commentsCount,
 EXISTS(SELECT 1 FROM likes l WHERE l.postId = p.postId AND l.userId = ?) AS isLiked,
 EXISTS(SELECT 1 FROM bookmarks b WHERE b.postId = p.postId AND b.userId = ?) AS isBookmarked
FROM post p JOIN user u ON p.userId = u.userId
WHERE EXISTS (SELECT 1 FROM bookmarks saved WHERE saved.postId = p.postId AND saved.userId = ?)
ORDER BY p.timestamp DESC, p.postId DESC;

-- Load one post, its counts, and the current viewer's interaction states.
-- Page: http://localhost:8081/post/1
-- Values: viewer ID twice, then post ID.
SELECT p.postId, p.userId, p.content, p.timestamp, u.firstName, u.lastName,
 (SELECT COUNT(*) FROM likes l WHERE l.postId = p.postId) AS likesCount,
 (SELECT COUNT(*) FROM comment c WHERE c.postId = p.postId) AS commentsCount,
 EXISTS(SELECT 1 FROM likes l WHERE l.postId = p.postId AND l.userId = ?) AS isLiked,
 EXISTS(SELECT 1 FROM bookmarks b WHERE b.postId = p.postId AND b.userId = ?) AS isBookmarked
FROM post p JOIN user u ON p.userId = u.userId
WHERE p.postId = ?;

-- Load a post's comments from oldest to newest.
-- Page: http://localhost:8081/post/1
SELECT c.commentId, c.postId, c.userId, c.content, u.firstName, u.lastName, c.timestamp
FROM comment c JOIN user u ON c.userId = u.userId
WHERE c.postId = ? ORDER BY c.timestamp ASC, c.commentId ASC;

-- Save a nonempty comment. Values: post ID, session user ID, comment text.
-- POST http://localhost:8081/post/1/comment -> page http://localhost:8081/post/1
INSERT INTO comment (postId, userId, content, timestamp) VALUES (?, ?, ?, CURRENT_TIMESTAMP);

-- Like a post. A duplicate key means it is already liked; no second alert is sent.
-- POST http://localhost:8081/post/1/heart/true -> page http://localhost:8081/post/1
INSERT INTO likes (postId, userId) VALUES (?, ?);

-- Remove only the logged-in user's like.
-- POST http://localhost:8081/post/1/heart/false -> page http://localhost:8081/post/1
DELETE FROM likes WHERE postId = ? AND userId = ?;

-- Bookmark a post. Repeating the action keeps one row.
-- POST http://localhost:8081/post/1/bookmark/true -> page http://localhost:8081/post/1
INSERT INTO bookmarks (postId, userId) VALUES (?, ?)
ON DUPLICATE KEY UPDATE userId = userId;

-- Remove only the logged-in user's bookmark.
-- POST http://localhost:8081/post/1/bookmark/false -> page http://localhost:8081/post/1
DELETE FROM bookmarks WHERE postId = ? AND userId = ?;

-- Find posts with ALL requested hashtags, using whole tokens and ignoring case.
-- Page: http://localhost:8081/hashtagsearch?hashtags=%232025%20%23fireworks
-- Values: viewer ID twice, then one regular expression per distinct hashtag.
-- This example has two tags; the Java query adds one AND condition per tag.
-- Patterns: (^|[^[:alnum:]_#])#2025([^[:alnum:]_]|$)
--           (^|[^[:alnum:]_#])#fireworks([^[:alnum:]_]|$)
SELECT p.postId, p.userId, p.content, p.timestamp, u.firstName, u.lastName,
 (SELECT COUNT(*) FROM likes l WHERE l.postId = p.postId) AS likesCount,
 (SELECT COUNT(*) FROM comment c WHERE c.postId = p.postId) AS commentsCount,
 EXISTS(SELECT 1 FROM likes l WHERE l.postId = p.postId AND l.userId = ?) AS isLiked,
 EXISTS(SELECT 1 FROM bookmarks b WHERE b.postId = p.postId AND b.userId = ?) AS isBookmarked
FROM post p JOIN user u ON p.userId = u.userId
WHERE REGEXP_LIKE(p.content, ?, 'i') AND REGEXP_LIKE(p.content, ?, 'i')
ORDER BY p.timestamp DESC, p.postId DESC;

-- Find the post's author before sending a like or comment notification.
-- POST http://localhost:8081/post/1/heart/true or /post/1/comment
-- Page after either action: http://localhost:8081/post/1
SELECT userId FROM post WHERE postId = ?;

-- Save a notification together with the action that caused it.
-- Trigger pages: http://localhost:8081/, http://localhost:8081/people,
-- and http://localhost:8081/post/1. Display page: http://localhost:8081/notifications
-- Values: recipient ID, actor ID, type, target ID.
-- Types are LIKE, COMMENT, FOLLOW and MENTION. Bookmarks are private.
-- Self-actions and repeated likes/follows do not create another notification.
INSERT INTO notifications (recipient_id, actor_id, type, target_id) VALUES (?, ?, ?, ?);

-- List the current user's notifications and find whether a target post still exists.
-- Page: http://localhost:8081/notifications
-- Value: logged-in recipient ID.
SELECT n.*, u.firstName, u.lastName, p.postId AS existingPostId
FROM notifications n JOIN user u ON u.userId = n.actor_id
LEFT JOIN post p ON p.postId = n.target_id AND n.type IN ('LIKE','COMMENT','MENTION')
WHERE n.recipient_id = ?
ORDER BY n.created_at DESC, n.id DESC;

-- Apply both notification filters. Java includes each AND only when selected.
-- Page: http://localhost:8081/notifications?type=LIKE&unread=true
-- The unread-only URL omits type. The type-only URL omits unread=true.
-- Values: logged-in recipient ID, selected activity type (if included).
SELECT n.*, u.firstName, u.lastName, p.postId AS existingPostId
FROM notifications n JOIN user u ON u.userId = n.actor_id
LEFT JOIN post p ON p.postId = n.target_id AND n.type IN ('LIKE','COMMENT','MENTION')
WHERE n.recipient_id = ?
 AND COALESCE(n.is_read, FALSE) = FALSE
 AND n.type = ?
ORDER BY n.created_at DESC, n.id DESC;

-- Count unread notifications for the navigation badge on every logged-in page.
-- Example page: http://localhost:8081/notifications
SELECT COUNT(*) FROM notifications WHERE recipient_id = ?
 AND COALESCE(is_read, FALSE) = FALSE;

-- Mark one notification read only if it belongs to the current user.
-- POST http://localhost:8081/notifications/5/read -> page http://localhost:8081/notifications
-- Values: session recipient ID, notification ID.
UPDATE notifications SET is_read = TRUE WHERE recipient_id = ? AND id = ?;

-- Mark all of the current user's notifications read.
-- POST http://localhost:8081/notifications/read-all -> page http://localhost:8081/notifications
UPDATE notifications SET is_read = TRUE WHERE recipient_id = ?;

-- Additional helper kept from the post-creation contribution:
-- findPostsByHashtags uses stored hashtag rows. The active /hashtagsearch page
-- uses the whole-token query above, so older post content can still be searched.
-- Values: normalized tag names without #, then the number of distinct tags.
SELECT p.postId, p.content, p.timestamp, u.userId, u.firstName, u.lastName
FROM post p JOIN user u ON u.userId = p.userId
JOIN post_hashtag ph ON ph.postId = p.postId
WHERE ph.hashtag IN (?, ?)
GROUP BY p.postId, p.content, p.timestamp, u.userId, u.firstName, u.lastName
HAVING COUNT(DISTINCT ph.hashtag) = ?
ORDER BY p.timestamp DESC, p.postId DESC;

-- Transaction control is done in Java with setAutoCommit(false), commit()
-- and rollback(). A post, its hashtag links and mention alerts save together.
-- A new follow, like or comment and its alert also save together.
