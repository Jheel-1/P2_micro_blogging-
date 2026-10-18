package uga.menik.csx370.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import uga.menik.csx370.models.Post;
import uga.menik.csx370.models.User;

/** Loads a user's profile posts with the logged-in viewer's interaction states. */
@Service
public class ProfileService {

    private static final DateTimeFormatter POST_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy, hh:mm a", Locale.US);

    private final DataSource dataSource;

    @Autowired
    public ProfileService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * @param profileUserId the author whose posts should be displayed
     * @param viewerId the logged-in user whose like/bookmark states should be shown
     * @throws SQLException when posts cannot be loaded, rather than treating a
     *         database failure as an empty profile
     */
    public List<Post> getPostsForUser(int profileUserId, int viewerId) throws SQLException {
        final String sql =
                "SELECT p.postId, p.userId, p.content, p.timestamp, u.firstName, u.lastName, " +
                "(SELECT COUNT(*) FROM likes l WHERE l.postId = p.postId) AS likesCount, " +
                "(SELECT COUNT(*) FROM comment c WHERE c.postId = p.postId) AS commentsCount, " +
                "EXISTS(SELECT 1 FROM likes l WHERE l.postId = p.postId AND l.userId = ?) AS isLiked, " +
                "EXISTS(SELECT 1 FROM bookmarks b WHERE b.postId = p.postId AND b.userId = ?) AS isBookmarked " +
                "FROM post p JOIN user u ON u.userId = p.userId " +
                "WHERE p.userId = ? " +
                "ORDER BY p.timestamp DESC, p.postId DESC";

        List<Post> posts = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, viewerId);
            statement.setInt(2, viewerId);
            statement.setInt(3, profileUserId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    User author = new User(result.getString("userId"),
                            result.getString("firstName"), result.getString("lastName"));
                    String postDate = result.getTimestamp("timestamp").toLocalDateTime()
                            .format(POST_DATE_FORMAT);
                    posts.add(new Post(result.getString("postId"), result.getString("content"),
                            postDate, author, result.getInt("likesCount"),
                            result.getInt("commentsCount"), result.getBoolean("isLiked"),
                            result.getBoolean("isBookmarked")));
                }
            }
        }
        return posts;
    }
}
