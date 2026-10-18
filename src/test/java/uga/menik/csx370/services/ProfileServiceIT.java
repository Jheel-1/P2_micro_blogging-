package uga.menik.csx370.services;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import junit.framework.TestCase;
import uga.menik.csx370.models.Post;

/**
 * Opt-in checks against MySQL, using only the existing project dependencies.
 * Run with -Dtest=ProfileServiceIT and the PROFILE_TEST_* environment variables
 * described in Readme.txt. Every test rolls its fixture data back.
 */
public class ProfileServiceIT extends TestCase {

    private Connection connection;
    private ProfileService profiles;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv("PROFILE_TEST_JDBC_URL");
        assertNotNull("Set PROFILE_TEST_JDBC_URL for the isolated test database", url);
        assertTrue("Use a separate database named csx370_profile_test",
                url.matches("jdbc:mysql://[^/]+/csx370_profile_test(?:\\?.*)?"));

        connection = DriverManager.getConnection(url,
                System.getenv("PROFILE_TEST_USER"), System.getenv("PROFILE_TEST_PASSWORD"));
        try {
            // Apply the real project table definitions to the separate test DB.
            String schema = Files.readString(Path.of("database_setup.sql"))
                    .replaceAll("(?im)^\\s*(?:create database if not exists|use)\\s+"
                            + "csx370_mb_platform\\s*;\\s*$", "");
            assertFalse("Test setup must not create or switch to another database",
                    Pattern.compile("(?im)^\\s*(?:create\\s+database|use)\\b")
                            .matcher(schema).find());
            ScriptUtils.executeSqlScript(connection,
                    new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));

            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO user "
                        + "(userId, username, password, firstName, lastName) VALUES "
                        + "(9001, 'profile_it_author', 'unused-test-hash', 'Ada', 'Author'), "
                        + "(9002, 'profile_it_viewer', 'unused-test-hash', 'Victor', 'Viewer'), "
                        + "(9003, 'profile_it_other', 'unused-test-hash', 'Other', 'Reader'), "
                        + "(9004, 'profile_it_empty', 'unused-test-hash', 'Empty', 'Profile')");
                statement.executeUpdate("INSERT INTO post (postId, userId, content, timestamp) VALUES "
                        + "(8101, 9001, 'Older author post', '2025-03-01 09:00:00'), "
                        + "(8102, 9001, 'First post at the same time', '2025-03-07 22:54:00'), "
                        + "(8103, 9001, 'Second post at the same time', '2025-03-07 22:54:00'), "
                        + "(8200, 9002, 'Another author has a newer post', '2025-04-01 12:00:00')");
                statement.executeUpdate("INSERT INTO likes (userId, postId) VALUES "
                        + "(9002, 8103), (9003, 8103), (9001, 8102)");
                statement.executeUpdate("INSERT INTO bookmarks (userId, postId) VALUES "
                        + "(9001, 8103), (9002, 8102)");
                statement.executeUpdate("INSERT INTO comment "
                        + "(commentId, postId, userId, content, timestamp) VALUES "
                        + "(8301, 8103, 9002, 'First comment', '2025-03-08 10:00:00'), "
                        + "(8302, 8103, 9002, 'Another comment by the same user', '2025-03-08 10:01:00'), "
                        + "(8303, 8103, 9001, 'Author reply', '2025-03-08 10:02:00')");
            }
            // Keep service queries in the fixture transaction; service close()
            // calls must not close this physical connection before rollback.
            profiles = new ProfileService(new SingleConnectionDataSource(connection, true));
        } catch (Exception | AssertionError failure) {
            try {
                if (!connection.getAutoCommit()) {
                    connection.rollback();
                }
            } finally {
                connection.close();
            }
            throw failure;
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            try {
                connection.rollback();
            } finally {
                connection.close();
            }
        }
    }

    public void testFiltersAuthorAndSortsNewestFirstWithStableTies() throws Exception {
        List<Post> posts = profiles.getPostsForUser(9001, 9002);
        assertEquals(List.of("8103", "8102", "8101"),
                posts.stream().map(Post::getPostId).toList());
        for (Post post : posts) {
            assertEquals("9001", post.getUser().getUserId());
            assertEquals("Ada", post.getUser().getFirstName());
        }
    }

    public void testCountsAreAccurateWithMultipleLikesAndComments() throws Exception {
        List<Post> posts = profiles.getPostsForUser(9001, 9002);
        assertEquals(3, posts.size());
        assertEquals(2, posts.get(0).getHeartsCount());
        assertEquals(3, posts.get(0).getCommentsCount());
        assertEquals(0, posts.get(2).getHeartsCount());
        assertEquals(0, posts.get(2).getCommentsCount());
    }

    public void testLikeAndBookmarkStatesBelongToViewer() throws Exception {
        List<Post> visitorView = profiles.getPostsForUser(9001, 9002);
        assertTrue(visitorView.get(0).getHearted());
        assertFalse(visitorView.get(0).isBookmarked());
        assertFalse(visitorView.get(1).getHearted());
        assertTrue(visitorView.get(1).isBookmarked());

        List<Post> authorView = profiles.getPostsForUser(9001, 9001);
        assertFalse(authorView.get(0).getHearted());
        assertTrue(authorView.get(0).isBookmarked());
        assertTrue(authorView.get(1).getHearted());
        assertFalse(authorView.get(1).isBookmarked());
    }

    public void testUsersWithoutPostsReturnAnEmptyList() throws Exception {
        assertTrue(profiles.getPostsForUser(9004, 9002).isEmpty());
        assertTrue(profiles.getPostsForUser(99999, 9002).isEmpty());
    }

    public void testDateUsesRequiredDisplayFormat() throws Exception {
        Post post = profiles.getPostsForUser(9001, 9002).get(0);
        assertEquals("Mar 07, 2025, 10:54 PM", post.getPostDate());
    }
}
