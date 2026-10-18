package uga.menik.csx370.controllers;

import java.sql.SQLException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import junit.framework.TestCase;
import uga.menik.csx370.models.Post;
import uga.menik.csx370.models.User;
import uga.menik.csx370.services.ProfileService;
import uga.menik.csx370.services.UserService;

/** Route behavior without a running database or additional test libraries. */
public class ProfileControllerTest extends TestCase {

    private StubProfileService profiles;
    private ProfileController controller;

    @Override
    protected void setUp() {
        profiles = new StubProfileService();
        controller = new ProfileController(new LoggedInUserService(), profiles);
    }

    public void testOwnProfileUsesLoggedInUserAsAuthorAndViewer() {
        controller.profileOfLoggedInUser();
        assertEquals(7, profiles.authorId);
        assertEquals(7, profiles.viewerId);
    }

    public void testSelectedProfileKeepsAuthorSeparateFromViewer() {
        profiles.posts = List.of(new Post("101", "Selected user's post",
                "Mar 07, 2025, 10:54 PM", new User("42", "Selected", "User"),
                2, 3, true, false));

        ModelAndView view = controller.profileOfSpecificUser(42);

        assertEquals(42, profiles.authorId);
        assertEquals(7, profiles.viewerId);
        assertEquals("posts_page", view.getViewName());
        assertSame(profiles.posts, view.getModel().get("posts"));
        assertEquals(Boolean.FALSE, view.getModel().get("isNoContent"));
        assertFalse(view.getModel().containsKey("errorMessage"));
    }

    public void testEmptyProfileShowsNoContentMessage() {
        ModelAndView view = controller.profileOfSpecificUser(42);
        assertEquals(List.of(), view.getModel().get("posts"));
        assertEquals(Boolean.TRUE, view.getModel().get("isNoContent"));
        assertFalse(view.getModel().containsKey("errorMessage"));
    }

    public void testDatabaseFailureShowsErrorInsteadOfEmptyProfile() {
        profiles.failure = new SQLException("Private database connection details");

        ModelAndView view = controller.profileOfSpecificUser(42);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, view.getStatus());
        assertEquals(List.of(), view.getModel().get("posts"));
        assertEquals(Boolean.FALSE, view.getModel().get("isNoContent"));
        String error = (String) view.getModel().get("errorMessage");
        assertNotNull(error);
        assertFalse(error.contains("Private database"));
    }

    public void testNonPositiveUserIdsAreRejectedBeforeDatabaseAccess() {
        for (int id : new int[] { 0, -1 }) {
            try {
                controller.profileOfSpecificUser(id);
                fail("Expected an invalid profile ID to be rejected");
            } catch (ResponseStatusException exception) {
                assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
            }
        }
        assertFalse(profiles.called);
    }

    private static class LoggedInUserService extends UserService {
        LoggedInUserService() {
            super(null);
        }

        @Override
        public User getLoggedInUser() {
            return new User("7", "Current", "Viewer");
        }
    }

    private static class StubProfileService extends ProfileService {
        int authorId;
        int viewerId;
        boolean called;
        List<Post> posts = List.of();
        SQLException failure;

        StubProfileService() {
            super(null);
        }

        @Override
        public List<Post> getPostsForUser(int profileUserId, int currentViewerId) throws SQLException {
            called = true;
            authorId = profileUserId;
            viewerId = currentViewerId;
            if (failure != null) {
                throw failure;
            }
            return posts;
        }
    }
}
