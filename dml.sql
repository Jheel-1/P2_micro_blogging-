-- Profile page: fetch only the selected author's posts, newest first, including
-- like/comment counts and the logged-in viewer's like/bookmark states.
-- Pages: http://localhost:8081/profile and http://localhost:8081/profile/1
-- /profile uses the logged-in user's ID as the author; /profile/{userId} uses
-- the ID in the URL. Prepared parameters: viewerId, viewerId, profileUserId.
SELECT p.postId, p.userId, p.content, p.timestamp, u.firstName, u.lastName,
       (SELECT COUNT(*) FROM likes l WHERE l.postId = p.postId) AS likesCount,
       (SELECT COUNT(*) FROM comment c WHERE c.postId = p.postId) AS commentsCount,
       EXISTS(SELECT 1 FROM likes l
              WHERE l.postId = p.postId AND l.userId = ?) AS isLiked,
       EXISTS(SELECT 1 FROM bookmarks b
              WHERE b.postId = p.postId AND b.userId = ?) AS isBookmarked
FROM post p
JOIN user u ON u.userId = p.userId
WHERE p.userId = ?
ORDER BY p.timestamp DESC, p.postId DESC;
