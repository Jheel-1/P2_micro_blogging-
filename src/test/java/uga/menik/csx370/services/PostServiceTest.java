package uga.menik.csx370.services;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import junit.framework.TestCase;
import uga.menik.csx370.models.Post;
import uga.menik.csx370.models.User;

/** JDBC-boundary tests; no external database or extra libraries required. */
public class PostServiceTest extends TestCase {
    private String sql;
    private final Map<Integer, String> bindings = new HashMap<>();
    private List<Map<String, Object>> rows = List.of();
    private List<Map<String, Object>> commentRows = List.of();
    private final DataSource source = proxy(DataSource.class, (object, method, args) -> {
        if (method.getName().equals("getConnection")) {
            return connection();
        }
        return null;
    });

    private PostService service() {
        UserService users = new UserService(source) {
            @Override public User getLoggedInUser() {
                return new User("7", "Test", "Viewer");
            }
        };
        return new PostService(source, users);
    }

    public void testSearchRequiresEveryTagAndNewestFirst() {
        service().searchHashtags(" #2025  #Fireworks #2025 ");
        assertTrue(sql.contains("REGEXP_LIKE(p.content, ?, 'i') AND REGEXP_LIKE(p.content, ?, 'i')"));
        assertTrue(sql.endsWith("ORDER BY p.timestamp DESC, p.postId DESC"));
        assertEquals("7", bindings.get(1));
        assertEquals("7", bindings.get(2));
        assertEquals(Hashtags.sqlPattern("#2025"), bindings.get(3));
        assertEquals(Hashtags.sqlPattern("#fireworks"), bindings.get(4));
        assertEquals(4, bindings.size());
    }

    public void testEmptySearchDoesNotQuery() {
        assertTrue(service().searchHashtags("  ").isEmpty());
        assertNull(sql);
    }

    public void testPostValidationRejectsBlankUnicodeWhitespaceAndOverlongContent() throws Exception {
        PostService posts = service();
        for (String content : new String[] {null, "", " \t\r\n", "\u00a0\u2003", "x".repeat(401)}) {
            try {
                posts.createPost(new User("7", "Test", "Viewer"), content);
                fail("Invalid content must be rejected before opening a database connection");
            } catch (IllegalArgumentException expected) {
                assertNull(sql);
            }
        }
    }

    public void testInvalidSearchIsRejected() {
        for (String input : List.of("fireworks", "#", "#tag OR 1=1", "#tag.*")) {
            try {
                service().searchHashtags(input);
                fail("Expected invalid hashtags to be rejected");
            } catch (IllegalArgumentException expected) {
                assertNull(sql);
            }
        }
    }

    public void testTagBoundaries() {
        // Translate MySQL's POSIX alnum class to Java's equivalent for boundary cases.
        String regex = Hashtags.sqlPattern("#fire").replace("[:alnum:]", "\\p{L}\\p{N}");
        var pattern = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE);
        assertTrue(pattern.matcher("Hello (#FIRE)!").find());
        assertFalse(pattern.matcher("#fireworks").find());
        assertFalse(pattern.matcher("#fire_alarm").find());
        assertFalse(pattern.matcher("word#fire").find());
        assertFalse(pattern.matcher("##fire").find());
    }

    public void testPostMappingIncludesCountsViewerFlagsAndFormattedDate() {
        Map<String, Object> row = new HashMap<>();
        row.put("postId", "12"); row.put("userId", "3");
        row.put("firstName", "Jane"); row.put("lastName", "Doe");
        row.put("content", "Hello #2025");
        row.put("timestamp", Timestamp.valueOf("2025-03-07 22:54:00"));
        row.put("likesCount", 4); row.put("commentsCount", 2);
        row.put("isLiked", true); row.put("isBookmarked", false);
        rows = List.of(row);
        Post post = service().getPosts("12").get(0);
        assertEquals("Mar 07, 2025, 10:54 PM", post.getPostDate());
        assertEquals("3", post.getUser().getUserId());
        assertEquals(4, post.getHeartsCount());
        assertEquals(2, post.getCommentsCount());
        assertTrue(post.getHearted()); assertFalse(post.isBookmarked());
        assertEquals("12", bindings.get(3));
    }

    public void testExpandedPostLoadsCommentsInChronologicalOrder() {
        Map<String, Object> post = new HashMap<>();
        post.put("postId", "12"); post.put("userId", "3");
        post.put("firstName", "Jane"); post.put("lastName", "Doe");
        post.put("content", "A post");
        post.put("timestamp", Timestamp.valueOf("2025-03-07 22:54:00"));
        post.put("likesCount", 0); post.put("commentsCount", 1);
        post.put("isLiked", false); post.put("isBookmarked", false);
        rows = List.of(post);
        Map<String, Object> comment = new HashMap<>(post);
        comment.put("commentId", "90"); comment.put("content", "A comment");
        commentRows = List.of(comment);
        var expanded = service().getExpandedPosts("12").get(0);
        assertTrue(sql.endsWith("ORDER BY c.timestamp ASC, c.commentId ASC"));
        assertEquals("12", bindings.get(1));
        assertEquals(1, expanded.getComments().size());
        assertEquals("90", expanded.getComments().get(0).getPostId());
        assertEquals("A comment", expanded.getComments().get(0).getContent());
        assertEquals("Mar 07, 2025, 10:54 PM", expanded.getComments().get(0).getPostDate());
    }

    public void testCommentValidationAndInsert() {
        PostService service = service();
        assertFalse(service.addComment("12", "7", " "));
        assertFalse(service.addComment("12", "7", "x".repeat(401)));
        assertFalse(service.addComment("12", "7", null));
        assertNull(sql);
        assertTrue(service.addComment("12", "7", " Hello! "));
        assertTrue(sql.startsWith("INSERT INTO comment"));
        assertEquals("12", bindings.get(1));
        assertEquals("7", bindings.get(2));
        assertEquals("Hello!", bindings.get(3));
    }

    public void testSavedStateIsScopedToUserAndPost() {
        PostService service = service();
        assertTrue(service.setLiked("12", "7", true));
        assertTrue(sql.startsWith("INSERT INTO likes"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(service.setLiked("12", "7", false));
        assertEquals("DELETE FROM likes WHERE postId = ? AND userId = ?", sql);
        assertEquals("12", bindings.get(1)); assertEquals("7", bindings.get(2));
        assertTrue(service.setBookmarked("12", "7", true));
        assertTrue(sql.startsWith("INSERT INTO bookmarks"));
        assertTrue(service.setBookmarked("12", "7", false));
        assertEquals("DELETE FROM bookmarks WHERE postId = ? AND userId = ?", sql);
    }

    private Connection connection() {
        return proxy(Connection.class, (object, method, args) -> {
            if (method.getName().equals("prepareStatement")) {
                sql = (String) args[0]; bindings.clear();
                return statement();
            }
            return null;
        });
    }

    private PreparedStatement statement() {
        return proxy(PreparedStatement.class, (object, method, args) -> {
            switch (method.getName()) {
                case "setString": bindings.put((Integer) args[0], (String) args[1]); return null;
                case "executeUpdate": return 1;
                case "executeQuery": return results();
                default: return null;
            }
        });
    }

    private ResultSet results() {
        List<Map<String, Object>> resultRows = sql.contains("FROM comment c JOIN") ? commentRows : rows;
        int[] index = {-1};
        return proxy(ResultSet.class, (object, method, args) -> {
            if (method.getName().equals("next")) return ++index[0] < resultRows.size();
            if (method.getName().startsWith("get")) return resultRows.get(index[0]).get(args[0]);
            return null;
        });
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
