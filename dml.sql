-- Shared post details: http://localhost:8081/post/12
-- Parameters: viewer ID, viewer ID, requested post ID.
SELECT p.postId, p.userId, p.content, p.timestamp, u.firstName, u.lastName,
 (SELECT COUNT(*) FROM likes l WHERE l.postId = p.postId) AS likesCount,
 (SELECT COUNT(*) FROM comment c WHERE c.postId = p.postId) AS commentsCount,
 EXISTS(SELECT 1 FROM likes l WHERE l.postId = p.postId AND l.userId = ?) AS isLiked,
 EXISTS(SELECT 1 FROM bookmarks b WHERE b.postId = p.postId AND b.userId = ?) AS isBookmarked
FROM post p JOIN user u ON p.userId = u.userId
WHERE p.postId = ?;

-- Shared post lists: http://localhost:8081/ and /profile/3 and /bookmarks
-- IDs are supplied by the page's selection; refreshPosts preserves their order.
SELECT p.postId, p.userId, p.content, p.timestamp, u.firstName, u.lastName,
 (SELECT COUNT(*) FROM likes l WHERE l.postId = p.postId) AS likesCount,
 (SELECT COUNT(*) FROM comment c WHERE c.postId = p.postId) AS commentsCount,
 EXISTS(SELECT 1 FROM likes l WHERE l.postId = p.postId AND l.userId = ?) AS isLiked,
 EXISTS(SELECT 1 FROM bookmarks b WHERE b.postId = p.postId AND b.userId = ?) AS isBookmarked
FROM post p JOIN user u ON p.userId = u.userId
WHERE p.postId IN (?, ?, ?);
-- The number of placeholders follows the number of selected IDs.

-- Comments oldest first: http://localhost:8081/post/12
SELECT c.commentId, c.postId, c.userId, c.content, u.firstName, u.lastName, c.timestamp
FROM comment c JOIN user u ON c.userId = u.userId
WHERE c.postId = ? ORDER BY c.timestamp ASC, c.commentId ASC;

-- Submit a comment: http://localhost:8081/post/12/comment (POST)
INSERT INTO comment (postId, userId, content, timestamp)
VALUES (?, ?, ?, CURRENT_TIMESTAMP);

-- Like: http://localhost:8081/post/12/heart/true (POST)
INSERT INTO likes (postId, userId) VALUES (?, ?)
ON DUPLICATE KEY UPDATE userId = userId;

-- Unlike: http://localhost:8081/post/12/heart/false (POST)
DELETE FROM likes WHERE postId = ? AND userId = ?;

-- Bookmark: http://localhost:8081/post/12/bookmark/true (POST)
INSERT INTO bookmarks (postId, userId) VALUES (?, ?)
ON DUPLICATE KEY UPDATE userId = userId;

-- Remove bookmark: http://localhost:8081/post/12/bookmark/false (POST)
DELETE FROM bookmarks WHERE postId = ? AND userId = ?;

-- Search all supplied tags: http://localhost:8081/hashtagsearch?hashtags=%232025+%23fireworks
SELECT p.postId, p.userId, p.content, p.timestamp, u.firstName, u.lastName,
 (SELECT COUNT(*) FROM likes l WHERE l.postId = p.postId) AS likesCount,
 (SELECT COUNT(*) FROM comment c WHERE c.postId = p.postId) AS commentsCount,
 EXISTS(SELECT 1 FROM likes l WHERE l.postId = p.postId AND l.userId = ?) AS isLiked,
 EXISTS(SELECT 1 FROM bookmarks b WHERE b.postId = p.postId AND b.userId = ?) AS isBookmarked
FROM post p JOIN user u ON p.userId = u.userId
WHERE REGEXP_LIKE(p.content, ?, 'i') AND REGEXP_LIKE(p.content, ?, 'i')
ORDER BY p.timestamp DESC, p.postId DESC;
-- One AND predicate per distinct tag. Pattern parameters for this example:
-- (^|[^[:alnum:]_#])#2025([^[:alnum:]_]|$)
-- (^|[^[:alnum:]_#])#fireworks([^[:alnum:]_]|$)
