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
        List<Post> posts = new ArrayList<>();

        final String sql = 
        "SELECT p.postId, p.userId, p.content, p.timestamp, u.firstName, u.lastName, " +
        "(SELECT COUNT(*) FROM likes l WHERE l.postId = p.postId) AS likesCount, " +
        "(SELECT COUNT(*) FROM comment c WHERE c.postId = p.postId) AS commentsCount, " +
        "EXISTS(SELECT 1 FROM likes l WHERE l.postId = p.postId AND l.userId = ?) AS isLiked, " +
        "EXISTS(SELECT 1 FROM bookmarks b WHERE b.postId = p.postId AND b.userId = ?) AS isBookmarked " + 
        "FROM post p JOIN user u ON p.userId = u.userId " + 
        "WHERE p.postId = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userService.getLoggedInUser().getUserId());
            pstmt.setString(2, userService.getLoggedInUser().getUserId());
            pstmt.setString(3, postId);

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
            e.printStackTrace();
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
            e.printStackTrace();
        } // catch

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
