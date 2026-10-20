package uga.menik.csx370.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import uga.menik.csx370.models.Post;
import uga.menik.csx370.models.User;
import uga.menik.csx370.models.ExpandedPost;
import uga.menik.csx370.models.Comment;

@Service 
public class PostService {
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#([\\p{L}\\p{N}_]{1,100})");
    private static final Pattern MENTION_PATTERN = Pattern.compile(
            "(?<![\\p{L}\\p{N}_@])@([\\p{L}\\p{N}_](?:[\\p{L}\\p{N}_.-]*[\\p{L}\\p{N}_])?)");
    private static final DateTimeFormatter POST_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy, hh:mm a", Locale.US);

    private final DataSource dataSource;
    private final UserService userService;

    @Autowired
    public PostService(DataSource dataSource, UserService userService) {
        this.dataSource = dataSource;
        this.userService = userService;
    }

    /** Create a post and persist its normalized hashtags in the same transaction. */
    public void createPost(User user, String content) throws SQLException {
        if (content == null || content.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new IllegalArgumentException("Post content cannot be empty.");
        }
        if (content.codePointCount(0, content.length()) > 400) {
            throw new IllegalArgumentException("Posts must contain at most 400 characters.");
        }
        String insertPostSql = "INSERT INTO post (userId, content, timestamp) "
                + "VALUES (?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int postId;
                try (PreparedStatement stmt = conn.prepareStatement(
                        insertPostSql, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, user.getUserId());
                    stmt.setString(2, content);
                    stmt.executeUpdate();
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Creating the post did not return an ID.");
                        }
                        postId = keys.getInt(1);
                    }
                }

                try (PreparedStatement tagStmt = conn.prepareStatement(
                        "INSERT IGNORE INTO hashtag (hashtag) VALUES (?)");
                        PreparedStatement linkStmt = conn.prepareStatement(
                                "INSERT INTO post_hashtag (postId, hashtag) VALUES (?, ?)")) {
                    for (String hashtag : extractHashtags(content)) {
                        tagStmt.setString(1, hashtag);
                        tagStmt.executeUpdate();
                        linkStmt.setInt(1, postId);
                        linkStmt.setString(2, hashtag);
                        linkStmt.executeUpdate();
                    }
                }
                notifyMentions(conn, user, content, postId);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void notifyMentions(Connection conn, User author, String content, int postId) throws SQLException {
        Set<Long> recipients = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        try (PreparedStatement statement = conn.prepareStatement("SELECT userId FROM user WHERE username = ?")) {
            while (matcher.find()) {
                statement.setString(1, matcher.group(1));
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) recipients.add(result.getLong("userId"));
                }
            }
        }
        for (long recipient : recipients) {
            NotificationService.insert(conn, recipient, Long.parseLong(author.getUserId()), "MENTION", postId);
        }
    }

    /** Load posts from the given user and accounts they follow. */
    public List<Post> getFeedForUser(String userId) throws SQLException {
        String sql = "SELECT p.postId, p.content, p.timestamp, "
                + "u.userId, u.firstName, u.lastName "
                + "FROM post p "
                + "JOIN user u ON u.userId = p.userId "
                + "WHERE p.userId = ? "
                + "OR EXISTS (SELECT 1 FROM user_follow f "
                + "WHERE f.followerId = ? AND f.followeeId = p.userId) "
                + "ORDER BY p.timestamp DESC, p.postId DESC";
        List<Post> posts = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapPost(rs));
                }
            }
        }
        return posts;
    }

    /** Find posts containing every hashtag in the search string. */
    public List<Post> findPostsByHashtags(String rawHashtags) throws SQLException {
        Set<String> hashtags = extractHashtags(rawHashtags);
        if (hashtags.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(hashtags.size(), "?"));
        String sql = "SELECT p.postId, p.content, p.timestamp, "
                + "u.userId, u.firstName, u.lastName "
                + "FROM post p "
                + "JOIN user u ON u.userId = p.userId "
                + "JOIN post_hashtag ph ON ph.postId = p.postId "
                + "WHERE ph.hashtag IN (" + placeholders + ") "
                + "GROUP BY p.postId, p.content, p.timestamp, u.userId, u.firstName, u.lastName "
                + "HAVING COUNT(DISTINCT ph.hashtag) = ? "
                + "ORDER BY p.timestamp DESC, p.postId DESC";

        List<Post> posts = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            int index = 1;
            for (String hashtag : hashtags) {
                stmt.setString(index++, hashtag);
            }
            stmt.setInt(index, hashtags.size());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapPost(rs));
                }
            }
        }
        return posts;
    }

    private Post mapPost(ResultSet rs) throws SQLException {
        Timestamp timestamp = rs.getTimestamp("timestamp");
        User author = new User(rs.getString("userId"), rs.getString("firstName"), rs.getString("lastName"));
        return new Post(rs.getString("postId"), rs.getString("content"),
                timestamp.toLocalDateTime().format(POST_DATE_FORMAT), author, 0, 0, false, false);
    }

    private Set<String> extractHashtags(String text) {
        Set<String> hashtags = new LinkedHashSet<>();
        if (text == null) {
            return hashtags;
        }
        Matcher matcher = HASHTAG_PATTERN.matcher(text);
        while (matcher.find()) {
            hashtags.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return hashtags;
    }

    /** Fetch one post, including counts and the current viewer's saved states. */
    public List<Post> getPosts(String postId) {
        return queryPosts("WHERE p.postId = ?", List.of(postId));
    }

    public List<Post> getBookmarkedPosts() {
        return queryPosts("WHERE EXISTS (SELECT 1 FROM bookmarks saved "
                + "WHERE saved.postId = p.postId AND saved.userId = ?) "
                + "ORDER BY p.timestamp DESC, p.postId DESC",
                List.of(userService.getLoggedInUser().getUserId()));
    }

    /** Refresh the posts selected by another page without changing that page's selection rules. */
    public List<Post> refreshPosts(List<Post> selectedPosts) {
        if (selectedPosts.isEmpty()) {
            return List.of();
        }
        List<String> ids = selectedPosts.stream().map(Post::getPostId).distinct().toList();
        String slots = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Post> loaded = queryPosts("WHERE p.postId IN (" + slots + ")", ids);
        java.util.Map<String, Post> byId = new java.util.HashMap<>();
        loaded.forEach(post -> byId.put(post.getPostId(), post));
        List<Post> result = new ArrayList<>();
        for (Post selected : selectedPosts) {
            Post actual = byId.get(selected.getPostId());
            if (actual != null) {
                result.add(actual);
            }
        }
        return result;
    }

    /** Require all distinct hashtag tokens and sort newest first. */
    public List<Post> searchHashtags(String input) {
        List<String> tags = Hashtags.parse(input);
        if (tags.isEmpty()) {
            return List.of();
        }
        String conditions = String.join(" AND ", java.util.Collections.nCopies(
                tags.size(), "REGEXP_LIKE(p.content, ?, 'i')"));
        List<String> patterns = tags.stream().map(Hashtags::sqlPattern).toList();
        return queryPosts("WHERE " + conditions +
                " ORDER BY p.timestamp DESC, p.postId DESC", patterns);
    }

    private static final String POST_SELECT =
        "SELECT p.postId, p.userId, p.content, p.timestamp, u.firstName, u.lastName, " +
        "(SELECT COUNT(*) FROM likes l WHERE l.postId = p.postId) AS likesCount, " +
        "(SELECT COUNT(*) FROM comment c WHERE c.postId = p.postId) AS commentsCount, " +
        "EXISTS(SELECT 1 FROM likes l WHERE l.postId = p.postId AND l.userId = ?) AS isLiked, " +
        "EXISTS(SELECT 1 FROM bookmarks b WHERE b.postId = p.postId AND b.userId = ?) AS isBookmarked " + 
        "FROM post p JOIN user u ON p.userId = u.userId ";



    private List<Post> queryPosts(String filter, List<String> parameters) {
        List<Post> posts = new ArrayList<>();
        final String sql = POST_SELECT + filter;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userService.getLoggedInUser().getUserId());
            pstmt.setString(2, userService.getLoggedInUser().getUserId());
            for (int i = 0; i < parameters.size(); i++) {
                pstmt.setString(i + 3, parameters.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String postIds = rs.getString("postId");
                    String userId = rs.getString("userId");
                    String firstName = rs.getString("firstName");
                    String lastName = rs.getString("lastName");
                    User author = new User(userId, firstName, lastName);
                    String content = rs.getString("content");
                    Timestamp createdAt = rs.getTimestamp("timestamp");
                    String timestamp = createdAt.toLocalDateTime().format(
                        DateTimeFormatter.ofPattern("MMM dd, yyyy, hh:mm a", Locale.US)
                    );
                    int likes = rs.getInt("likesCount");
                    int comments = rs.getInt("commentsCount");
                    boolean isLiked = rs.getBoolean("isLiked");
                    boolean isBookmarked = rs.getBoolean("isBookmarked");


                    posts.add(new Post(postIds, content, timestamp, author, likes, comments, isLiked, isBookmarked));
                }
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load posts", e);
        }

        return posts;
    }

    public List<ExpandedPost> getExpandedPosts(String postId) {
        List<Post> posts = getPosts(postId);

        if (posts.isEmpty()) {
            return List.of(); // Return an empty list if no posts are found
        } // if

        List<Comment> comments = new ArrayList<>();
        final String sqlComments = 
        "SELECT c.commentId, c.postId, c.userId, c.content, u.firstName, u.lastName, c.timestamp " +
        "FROM comment c JOIN user u ON c.userId = u.userId " +
        "WHERE c.postId = ? "+
        "ORDER BY c.timestamp ASC, c.commentId ASC";
       
        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sqlComments)) {

            pstmt.setString(1, postId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String commentId = rs.getString("commentId");
                    String userId = rs.getString("userId");
                    String firstName = rs.getString("firstName");
                    String lastName = rs.getString("lastName");
                    User author = new User(userId, firstName, lastName);
                    String content = rs.getString("content");
                    Timestamp createdAt = rs.getTimestamp("timestamp");
                    String timestamp = createdAt.toLocalDateTime().format(
                        DateTimeFormatter.ofPattern("MMM dd, yyyy, hh:mm a", Locale.US)
                    );
                    comments.add(new Comment(commentId, content, timestamp, author));

                } // while
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load comments", e);
        }

        Post post = posts.get(0); // Assuming only one post is returned for the given postId

        ExpandedPost expandedPost = new ExpandedPost(
            post.getPostId(),
            post.getContent(),
            post.getPostDate(),
            post.getUser(),
            post.getHeartsCount(),
            post.getCommentsCount(),
            post.getHearted(),
            post.isBookmarked(),
            comments
        );
        return List.of(expandedPost);
    }

    /** Insert a validated comment. The database supplies its creation time. */
    public boolean addComment(String postId, String userId, String comment) {
        if (comment == null || comment.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
                || comment.codePointCount(0, comment.length()) > 400) {
            return false;
        }
        final String sql = "INSERT INTO comment (postId, userId, content, timestamp) " +
                "VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, postId);
                    pstmt.setString(2, userId);
                    pstmt.setString(3, comment.strip());
                    pstmt.executeUpdate();
                }
                NotificationService.notifyPostOwner(conn, postId, userId, "COMMENT");
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean setLiked(String postId, String userId, boolean isAdd) {
        if (!isAdd) return updateSavedState(postId, userId, false, false);
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                boolean inserted = false;
                try (PreparedStatement statement = conn.prepareStatement(
                        "INSERT INTO likes (postId, userId) VALUES (?, ?)")) {
                    statement.setString(1, postId);
                    statement.setString(2, userId);
                    statement.executeUpdate();
                    inserted = true;
                } catch (SQLException e) {
                    if (e.getErrorCode() != 1062) throw e;
                }
                if (inserted) NotificationService.notifyPostOwner(conn, postId, userId, "LIKE");
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                return false;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean setBookmarked(String postId, String userId, boolean isAdd) {
        return updateSavedState(postId, userId, isAdd, true);
    }

    /** Repeated adds/removes leave the requested state unchanged. */
    private boolean updateSavedState(String postId, String userId, boolean isAdd, boolean bookmark) {
        // Table names come only from these constants, never from browser input.
        String table = bookmark ? "bookmarks" : "likes";
        String sql = isAdd
                ? "INSERT INTO " + table + " (postId, userId) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE userId = userId"
                : "DELETE FROM " + table + " WHERE postId = ? AND userId = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, postId);
            pstmt.setString(2, userId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
