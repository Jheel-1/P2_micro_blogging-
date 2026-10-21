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

/**
 * Service component responsible for business logic related to user notifications,
 * including creating, retrieving, counting, and updating read statuses.
 */
@Service
public class NotificationService {
    public static final List<String> TYPES = List.of("LIKE", "COMMENT", "FOLLOW", "MENTION");
    private final DataSource dataSource;

    /**
     * Constructs new NotificationService with specified database DataSource.
     *
     * @param dataSource the JDBC data source used for database connectivity
     */
    public NotificationService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 
     * Inserts new notification record into database using caller's transaction 
     * so that action and notification succeed together.
     *
     * @param conn         active database connection and transaction
     * @param recipientId  user ID who will receive the notification
     * @param actorId      user ID who performed the action
     * @param type         notification type (must be one of {@link #TYPES})
     * @param targetId     target entity identifier (e.g., post ID)
     * @throws SQLException if a database access error occurs
     */
    static void insert(Connection conn, long recipientId, long actorId, String type, long targetId)
            throws SQLException {
        if (recipientId == actorId) return; //if
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Unknown notification type."); //if
        try (PreparedStatement statement = conn.prepareStatement(
                "INSERT INTO notifications (recipient_id, actor_id, type, target_id) VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, recipientId);
            statement.setLong(2, actorId);
            statement.setString(3, type);
            statement.setLong(4, targetId);
            statement.executeUpdate();
        } //try
    } //throw exception

    /** 
     * Finds owner of specified post and creates notification for them
     *
     * @param conn     active database connection
     * @param postId   identifier of the post being acted upon
     * @param actorId  identifier of the user performing action
     * @param type     notification type
     * @throws SQLException if post no longer exists or a database error occurs
     */
    static void notifyPostOwner(Connection conn, String postId, String actorId, String type)
            throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("SELECT userId FROM post WHERE postId = ?")) {
            statement.setString(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("The post no longer exists."); //if
                insert(conn, result.getLong("userId"), Long.parseLong(actorId), type, Long.parseLong(postId));
            } //try
        } //try
    } //throw exception

    /**
     * Creates new notification for recipient using independent database connection.
     *
     * @param recipientId  user ID receiving the notification
     * @param actorId      user ID triggering the notification
     * @param type         notification type
     * @param targetId     target entity ID
     * @throws IllegalStateException if saving the notification fails
     */
    public void createNotification(long recipientId, long actorId, String type, long targetId) {
        try (Connection conn = dataSource.getConnection()) {
            insert(conn, recipientId, actorId, type, targetId);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to save notification", e);
        } //try-catch
    } //createNotification

    /**
     * Retrieves all notifications for specified user, sorted by creation date descending.
     *
     * @param userId  identifier of user whose notifications are retrieved
     * @return a list of {@link Notification} objects
     */
    public List<Notification> getNotificationsForUser(long userId) {
        return getNotificationsForUser(userId, false, "");
    } //getNotificationsForUSer

    /**
     * Retrieves filtered notifications for specified user based on read status and notification type.
     *
     * @param userId      identifier of the user whose notifications are retrieved
     * @param unreadOnly if true, returns only unread notifications
     * @param type       optional filter for a specific notification type (empty string for all)
     * @return a filtered list of {@link Notification} objects
     * @throws IllegalArgumentException if an invalid notification type is provided
     * @throws IllegalStateException    if loading notifications fails
     */
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
                } //while
            } //try
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load notifications", e);
        } //try-catch
        return notifications;
    } //getNotificationsForUSer

     /**
     * Returns count of unread notifications for a specified user.
     *
     * @param userId  identifier of the user
     * @return  number of unread notifications
     * @throws IllegalStateException if counting notifications fails
     */
    public long getUnreadCount(long userId) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement statement = conn.prepareStatement(
                        "SELECT COUNT(*) FROM notifications WHERE recipient_id = ? "
                        + "AND COALESCE(is_read, FALSE) = FALSE")) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            } //try
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to count notifications", e);
        } //try-catch
    } //getUnreadCount

    /**
     * Marks specific notification as read for given user.
     *
     * @param userId          identifier of the user owning the notification
     * @param notificationId  identifier of the notification to mark as read
     */
    public void markAsRead(long userId, long notificationId) {
        updateReadState(userId, notificationId);
    } //markAsRead

    /**
     * Marks all notifications as read for a given user.
     *
     * @param userId  identifier of the user
     */
    public void markAllAsRead(long userId) {
        updateReadState(userId, null);
    } //markAllAsRead

    /**
     * Helper method to update the read state of a single notification or all notifications for a user.
     *
     * @param userId          identifier of the user
     * @param notificationId  specific notification ID to update, or null to update all for the user
     * @throws IllegalStateException if updating the read state fails
     */
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
        } //try-catch
    } //updateReadState
} //NotificationService
