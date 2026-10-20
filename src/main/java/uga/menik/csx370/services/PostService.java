package uga.menik.csx370.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import uga.menik.csx370.models.Post;
import uga.menik.csx370.models.User;
import uga.menik.csx370.models.ExpandedPost;
import uga.menik.csx370.models.Comment;

@Service 
public class PostService {
    private final DataSource dataSource;
    private final UserService userService;

    @Autowired
    public PostService(DataSource dataSource, UserService userService) {
        this.dataSource = dataSource;
        this.userService = userService;
    }

    /** Fetch one post, including counts and the current viewer's saved states. */
    public List<Post> getPosts(String postId) {
        return queryPosts("WHERE p.postId = ?", List.of(postId));
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
        if (comment == null || comment.isBlank()
                || comment.codePointCount(0, comment.length()) > 400) {
            return false;
        }
        final String sql = "INSERT INTO comment (postId, userId, content, timestamp) " +
                "VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, postId);
            pstmt.setString(2, userId);
            pstmt.setString(3, comment.strip());
            return pstmt.executeUpdate() == 1;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean setLiked(String postId, String userId, boolean isAdd) {
        return updateSavedState(postId, userId, isAdd, false);
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
