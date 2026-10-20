package uga.menik.csx370.services;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import junit.framework.TestCase;
import uga.menik.csx370.models.FollowableUser;
import uga.menik.csx370.models.Notification;
import uga.menik.csx370.models.Post;
import uga.menik.csx370.models.User;

/**
 * Opt-in real MySQL checks: -Dtest=ApplicationFeaturesIT with PROFILE_TEST_JDBC_URL,
 * PROFILE_TEST_USER and PROFILE_TEST_PASSWORD. Requires csx370_profile_test.
 * Mutations use independent connections; only this test's generated users are cleaned up.
 */
public class ApplicationFeaturesIT extends TestCase {
    private DataSource source;
    private final List<String> userIds = new ArrayList<>();
    private String prefix;
    private String author;
    private String viewer;
    private String other;
    private PostService posts;
    private PeopleService people;
    private NotificationService notifications;

    protected void setUp() throws Exception {
        String url = System.getenv("PROFILE_TEST_JDBC_URL");
        assertNotNull("Set PROFILE_TEST_JDBC_URL for the isolated database", url);
        assertTrue("Use the separate csx370_profile_test database",
                url.matches("jdbc:mysql://[^/]+/csx370_profile_test(?:\\?.*)?"));
        source = new DriverManagerDataSource(url, System.getenv("PROFILE_TEST_USER"),
                System.getenv("PROFILE_TEST_PASSWORD"));
        String schema = Files.readString(Path.of("database_setup.sql"))
                .replaceAll("(?im)^\\s*(?:create database if not exists|use)\\s+"
                        + "csx370_mb_platform\\s*;\\s*$", "");
        assertFalse(Pattern.compile("(?im)^\\s*(?:create\\s+database|use)\\b").matcher(schema).find());
        try (Connection conn = source.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));
        }
        prefix = "it" + UUID.randomUUID().toString().replace("-", "");
        author = addUser("author");
        viewer = addUser("viewer");
        other = addUser("other");
        posts = service(source, viewer);
        people = new PeopleService(source);
        notifications = new NotificationService(source);
    }

    protected void tearDown() throws Exception {
        if (source == null) return;
        for (String id : userIds) execute("DELETE FROM user WHERE userId = ?", id);
        if (prefix != null) execute("DELETE FROM hashtag WHERE hashtag LIKE ?", prefix + "%");
    }

    public void testFollowStateFeedAndLastPostDates() throws Exception {
        String old = addPost(author, "Older", "2025-03-01 10:00:00");
        String recent = addPost(author, "Recent", "2025-03-07 22:54:00");
        String own = addPost(viewer, "Own post", "2025-03-09 09:00:00");
        execute("INSERT INTO comment (postId,userId,content,timestamp) VALUES (?,?,'Later comment','2025-04-01 10:00:00')",
                old, author);
        assertEquals(List.of(own), ids(posts.getFeedForUser(viewer)));
        assertFalse(people.setFollowed(viewer, viewer, true));
        assertFalse(people.setFollowed(viewer, "not-an-id", true));
        assertFalse(people.setFollowed(viewer, "2147483647", true));
        assertTrue(people.setFollowed(viewer, author, true));
        assertTrue(people.setFollowed(viewer, author, true));
        assertEquals(List.of(own, recent, old), ids(posts.getFeedForUser(viewer)));
        List<FollowableUser> users = people.getFollowableUsers(viewer);
        assertFalse(users.stream().anyMatch(u -> u.getUserId().equals(viewer)));
        FollowableUser followed = users.stream().filter(u -> u.getUserId().equals(author)).findFirst().get();
        assertTrue(followed.isFollowed());
        assertEquals("Mar 07, 2025, 10:54 PM", followed.getLastActiveDate());
        assertEquals("Unknown", users.stream().filter(u -> u.getUserId().equals(other)).findFirst().get().getLastActiveDate());
        assertEquals(1, alerts(author, "FOLLOW").size());
        assertEquals("/profile/" + viewer, alerts(author, "FOLLOW").get(0).getTargetUrl());
        assertTrue(people.setFollowed(viewer, author, false));
        assertTrue(people.setFollowed(viewer, author, false));
        assertEquals(List.of(own), ids(posts.getFeedForUser(viewer)));
        assertFalse(people.getFollowableUsers(viewer).stream()
                .filter(u -> u.getUserId().equals(author)).findFirst().get().isFollowed());
    }

    public void testBookmarksArePrivateAndSortedByPostTime() throws Exception {
        String old = addPost(author, "Old", "2025-03-01 10:00:00");
        String newer = addPost(author, "Newer", "2025-03-07 22:54:00");
        String tie = addPost(other, "Same time", "2025-03-07 22:54:00");
        assertTrue(posts.setBookmarked(newer, viewer, true));
        assertTrue(posts.setBookmarked(old, viewer, true));
        assertTrue(posts.setBookmarked(tie, viewer, true));
        assertTrue(posts.setBookmarked(old, viewer, true));
        assertTrue(posts.setBookmarked(old, other, true));
        assertEquals(List.of(tie, newer, old), ids(posts.getBookmarkedPosts()));
        assertEquals(List.of(old), ids(service(source, other).getBookmarkedPosts()));
        assertTrue(posts.getBookmarkedPosts().stream().allMatch(Post::isBookmarked));
        assertEquals(0L, notifications.getUnreadCount(Long.parseLong(author)));
        assertTrue(posts.setBookmarked(old, viewer, false));
        assertTrue(posts.setBookmarked(old, viewer, false));
        assertEquals(List.of(tie, newer), ids(posts.getBookmarkedPosts()));
        assertEquals(List.of(old), ids(service(source, other).getBookmarkedPosts()));
    }

    public void testLikesCommentsCountsAndNotificationRecipients() throws Exception {
        String post = addPost(author, "A post", "2025-03-07 22:54:00");
        assertTrue(posts.setLiked(post, viewer, true));
        assertTrue(posts.setLiked(post, viewer, true));
        assertTrue(posts.setLiked(post, author, true));
        assertEquals(1, alerts(author, "LIKE").size());
        assertEquals("/post/" + post, alerts(author, "LIKE").get(0).getTargetUrl());
        assertEquals(viewer, alerts(author, "LIKE").get(0).getActor().getUserId());
        assertTrue(posts.addComment(post, viewer, "First comment"));
        assertTrue(posts.addComment(post, other, "Second comment"));
        assertTrue(posts.addComment(post, author, "Author reply"));
        assertEquals(2, alerts(author, "COMMENT").size());
        assertFalse(posts.addComment(post, viewer, " \n\t"));
        assertFalse(posts.addComment(post, viewer, "\u00a0\u2003"));
        assertFalse(posts.addComment(post, viewer, "x".repeat(401)));
        assertFalse(posts.addComment("2147483647", viewer, "Missing post"));
        var expanded = posts.getExpandedPosts(post).get(0);
        assertEquals(2, expanded.getHeartsCount());
        assertEquals(3, expanded.getCommentsCount());
        assertEquals(List.of("First comment", "Second comment", "Author reply"),
                expanded.getComments().stream().map(c -> c.getContent()).toList());
        assertTrue(posts.setLiked(post, viewer, false));
        assertEquals(1, posts.getPosts(post).get(0).getHeartsCount());
        assertFalse(posts.getPosts(post).get(0).getHearted());
        assertTrue(posts.setLiked(post, viewer, true));
        assertEquals(2, alerts(author, "LIKE").size());
        assertEquals(0L, notifications.getUnreadCount(Long.parseLong(viewer)));
    }

    public void testPostMentionsHashtagsAndLengthValidation() throws Exception {
        String content = "Hello @" + prefix + "author, @" + prefix + "author. @"
                + prefix + "viewer @" + prefix + "other @" + prefix + "missing "
                + "email@" + prefix + "other #" + prefix + "one #" + prefix + "two";
        posts.createPost(user(viewer), content);
        String post = Long.toString(scalar("SELECT MAX(postId) FROM post WHERE userId = ?", viewer));
        assertEquals(1, alerts(author, "MENTION").size());
        assertEquals(1, alerts(other, "MENTION").size());
        assertEquals(0L, notifications.getUnreadCount(Long.parseLong(viewer)));
        assertEquals("/post/" + post, alerts(author, "MENTION").get(0).getTargetUrl());
        posts.createPost(user(viewer), "email@" + prefix + "author #" + prefix + "one");
        assertEquals(1, alerts(author, "MENTION").size());
        assertEquals(List.of(post), ids(posts.searchHashtags("#" + prefix + "one #" + prefix + "two")));
        assertEquals(List.of(post), ids(posts.findPostsByHashtags("#" + prefix + "one #" + prefix + "two")));
        assertEquals(3L, scalar("SELECT COUNT(*) FROM post_hashtag ph JOIN post p ON p.postId=ph.postId WHERE p.userId=?", viewer));
        long before = scalar("SELECT COUNT(*) FROM post WHERE userId=?", viewer);
        for (String invalid : new String[] {null, "", " \n\t", "\u00a0\u2003", "x".repeat(401)}) {
            try { posts.createPost(user(viewer), invalid); fail("Invalid post was accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        assertEquals(before, scalar("SELECT COUNT(*) FROM post WHERE userId=?", viewer));
        posts.createPost(user(viewer), "\uD83D\uDE00".repeat(400));
        assertEquals(before + 1, scalar("SELECT COUNT(*) FROM post WHERE userId=?", viewer));
    }

    public void testNotificationFilteringReadScopeAndDeletedTargets() throws Exception {
        String post = addPost(author, "Target", "2025-03-07 22:54:00");
        assertTrue(posts.setLiked(post, viewer, true));
        assertTrue(posts.addComment(post, viewer, "Comment"));
        assertTrue(people.setFollowed(viewer, author, true));
        assertTrue(people.setFollowed(author, other, true));
        execute("UPDATE notifications SET created_at='2025-03-07 22:54:00' WHERE recipient_id=?", author);
        List<Notification> all = notifications.getNotificationsForUser(Long.parseLong(author));
        assertEquals(3, all.size());
        assertTrue(all.get(0).getId() > all.get(1).getId());
        assertEquals("Mar 07, 2025, 10:54 PM", all.get(0).getCreatedAt());
        long like = alerts(author, "LIKE").get(0).getId();
        notifications.markAsRead(Long.parseLong(viewer), like);
        assertEquals(3L, notifications.getUnreadCount(Long.parseLong(author)));
        notifications.markAsRead(Long.parseLong(author), like);
        assertTrue(notifications.getNotificationsForUser(Long.parseLong(author), true, "LIKE").isEmpty());
        assertEquals(1, alerts(author, "LIKE").size());
        assertEquals(2, notifications.getNotificationsForUser(Long.parseLong(author), true, "").size());
        execute("DELETE FROM post WHERE postId=?", post);
        assertEquals("", alerts(author, "LIKE").get(0).getTargetUrl());
        notifications.markAllAsRead(Long.parseLong(author));
        assertEquals(0L, notifications.getUnreadCount(Long.parseLong(author)));
        assertEquals(1L, notifications.getUnreadCount(Long.parseLong(other)));
        try {
            notifications.getNotificationsForUser(Long.parseLong(author), false, "' OR 1=1 --");
            fail("Invalid filter was accepted");
        } catch (IllegalArgumentException expected) { }
    }

    public void testFailedNotificationsRollBackTheirActions() throws Exception {
        String post = addPost(author, "Existing", "2025-03-07 22:54:00");
        DataSource failing = failingNotificationSource();
        PostService failedPosts = service(failing, viewer);
        assertFalse(failedPosts.setLiked(post, viewer, true));
        assertFalse(failedPosts.addComment(post, viewer, "Must roll back"));
        assertFalse(new PeopleService(failing).setFollowed(viewer, author, true));
        try {
            failedPosts.createPost(user(viewer), "Hello @" + prefix + "author #" + prefix + "rollback");
            fail("Post was saved without its notification");
        } catch (SQLException expected) { }
        assertEquals(0L, scalar("SELECT COUNT(*) FROM likes WHERE userId=?", viewer));
        assertEquals(0L, scalar("SELECT COUNT(*) FROM comment WHERE userId=?", viewer));
        assertEquals(0L, scalar("SELECT COUNT(*) FROM user_follow WHERE followerId=?", viewer));
        assertEquals(0L, scalar("SELECT COUNT(*) FROM post WHERE userId=?", viewer));
        assertEquals(0L, scalar("SELECT COUNT(*) FROM hashtag WHERE hashtag=?", prefix + "rollback"));
        assertEquals(0L, notifications.getUnreadCount(Long.parseLong(author)));
    }

    public void testConcurrentLikesAndFollowsDoNotDuplicateNotifications() throws Exception {
        String post = addPost(author, "Concurrent target", "2025-03-07 22:54:00");
        twiceConcurrently(() -> posts.setLiked(post, viewer, true));
        twiceConcurrently(() -> people.setFollowed(viewer, author, true));
        assertEquals(1L, scalar("SELECT COUNT(*) FROM likes WHERE postId=?", post));
        assertEquals(1L, scalar("SELECT COUNT(*) FROM user_follow WHERE followerId=?", viewer));
        assertEquals(1, alerts(author, "LIKE").size());
        assertEquals(1, alerts(author, "FOLLOW").size());
    }

    private void twiceConcurrently(Callable<Boolean> action) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> task = () -> { ready.countDown(); start.await(); return action.call(); };
        try {
            var first = executor.submit(task);
            var second = executor.submit(task);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(first.get(15, TimeUnit.SECONDS));
            assertTrue(second.get(15, TimeUnit.SECONDS));
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    private DataSource failingNotificationSource() {
        return new AbstractDataSource() {
            public Connection getConnection() throws SQLException {
                Connection actual = source.getConnection();
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                        new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                            && sql.startsWith("INSERT INTO notifications")) {
                        throw new SQLException("Simulated notification write failure");
                    }
                    try { return method.invoke(actual, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
            }
            public Connection getConnection(String username, String password) throws SQLException {
                return getConnection();
            }
        };
    }

    private List<Notification> alerts(String recipient, String type) {
        return notifications.getNotificationsForUser(Long.parseLong(recipient), false, type);
    }
    private List<String> ids(List<Post> result) {
        return result.stream().map(Post::getPostId).toList();
    }
    private User user(String id) { return new User(id, "Test", "User"); }
    private PostService service(DataSource dataSource, String viewerId) {
        return new PostService(dataSource, new UserService(dataSource) {
            public User getLoggedInUser() { return user(viewerId); }
        });
    }
    private String addUser(String suffix) throws SQLException {
        String id = insert("INSERT INTO user (username,password,firstName,lastName) VALUES (?,'unused-test-hash','Test',?)",
                prefix + suffix, suffix);
        userIds.add(id);
        return id;
    }
    private String addPost(String userId, String content, String date) throws SQLException {
        return insert("INSERT INTO post (userId,content,timestamp) VALUES (?,?,?)", userId, content, date);
    }
    private String insert(String sql, String... values) throws SQLException {
        try (Connection conn = source.getConnection();
                PreparedStatement statement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, values);
            statement.executeUpdate();
            try (var result = statement.getGeneratedKeys()) { result.next(); return result.getString(1); }
        }
    }
    private void execute(String sql, String... values) throws SQLException {
        try (Connection conn = source.getConnection(); PreparedStatement statement = conn.prepareStatement(sql)) {
            bind(statement, values);
            statement.executeUpdate();
        }
    }
    private long scalar(String sql, String... values) throws SQLException {
        try (Connection conn = source.getConnection(); PreparedStatement statement = conn.prepareStatement(sql)) {
            bind(statement, values);
            try (var result = statement.executeQuery()) { result.next(); return result.getLong(1); }
        }
    }
    private void bind(PreparedStatement statement, String... values) throws SQLException {
        for (int i = 0; i < values.length; i++) statement.setString(i + 1, values[i]);
    }
}

