package uga.menik.csx370.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;
import uga.menik.csx370.models.Notification;
import uga.menik.csx370.models.User;
import uga.menik.csx370.utility.DisplayTime;

@Service
public class NotificationService {
    public static final List<String> TYPES = List.of("LIKE", "COMMENT", "FOLLOW", "MENTION");
    private final DataSource dataSource;

    public NotificationService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Use the caller's transaction so the action and notification succeed together. */
    static void insert(Connection conn, long recipientId, long actorId, String type, long targetId)
            throws SQLException {
        if (recipientId == actorId) return;
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Unknown notification type.");
        try (PreparedStatement statement = conn.prepareStatement(
                "INSERT INTO notifications (recipient_id, actor_id, type, target_id) VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, recipientId);
            statement.setLong(2, actorId);
            statement.setString(3, type);
            statement.setLong(4, targetId);
            statement.executeUpdate();
        }
    }

    static void notifyPostOwner(Connection conn, String postId, String actorId, String type)
            throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("SELECT userId FROM post WHERE postId = ?")) {
            statement.setString(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("The post no longer exists.");
                insert(conn, result.getLong("userId"), Long.parseLong(actorId), type, Long.parseLong(postId));
            }
        }
    }

    public void createNotification(long recipientId, long actorId, String type, long targetId) {
        try (Connection conn = dataSource.getConnection()) {
            insert(conn, recipientId, actorId, type, targetId);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to save notification", e);
        }
    }

    public List<Notification> getNotificationsForUser(long userId) {
        return getNotificationsForUser(userId, false, "");
    }

    public List<Notification> getNotificationsForUser(long userId, boolean unreadOnly, String type) {
        if (!type.isEmpty() && !TYPES.contains(type)) {
            throw new IllegalArgumentException("Choose a valid notification type.");
        }
        String sql = "SELECT n.*, u.firstName, u.lastName, p.postId AS existingPostId "
                + "FROM notifications n JOIN user u ON u.userId = n.actor_id "
                + "LEFT JOIN post p ON p.postId = n.target_id AND n.type IN ('LIKE','COMMENT','MENTION') "
                + "WHERE n.recipient_id = ? "
                + (unreadOnly ? "AND COALESCE(n.is_read, FALSE) = FALSE " : "")
                + (!type.isEmpty() ? "AND n.type = ? " : "")
                + "ORDER BY n.created_at DESC, n.id DESC";
        List<Notification> notifications = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setLong(1, userId);
            if (!type.isEmpty()) statement.setString(2, type);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    User actor = new User(result.getString("actor_id"),
                            result.getString("firstName"), result.getString("lastName"));
                    LocalDateTime createdAt = result.getObject("created_at", LocalDateTime.class);
                    notifications.add(new Notification(result.getLong("id"), userId, actor,
                            result.getString("type"), result.getLong("target_id"),
                            result.getBoolean("is_read"),
                            DisplayTime.format(createdAt),
                            result.getObject("existingPostId") != null));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load notifications", e);
        }
        return notifications;
    }

    public long getUnreadCount(long userId) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement statement = conn.prepareStatement(
                        "SELECT COUNT(*) FROM notifications WHERE recipient_id = ? "
                        + "AND COALESCE(is_read, FALSE) = FALSE")) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to count notifications", e);
        }
    }

    public void markAsRead(long userId, long notificationId) {
        updateReadState(userId, notificationId);
    }

    public void markAllAsRead(long userId) {
        updateReadState(userId, null);
    }

    private void updateReadState(long userId, Long notificationId) {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE recipient_id = ?"
                + (notificationId == null ? "" : " AND id = ?");
        try (Connection conn = dataSource.getConnection();
                PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setLong(1, userId);
            if (notificationId != null) statement.setLong(2, notificationId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to update notifications", e);
        }
    }
}
