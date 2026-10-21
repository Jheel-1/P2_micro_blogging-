package uga.menik.csx370.components;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import uga.menik.csx370.models.User;
import uga.menik.csx370.services.NotificationService;
import uga.menik.csx370.services.UserService;

/**
 * Global controller advice that intercepts controller methods. Provides 
 * shared attributes across all views in the application (ex: user notification 
 * counts and status flags)
 */
@ControllerAdvice
public class GlobalControllerAdvice {
    private static final Logger logger = LoggerFactory.getLogger(GlobalControllerAdvice.class);

    private final NotificationService notificationService;
    private final UserService userService;

    /**
     * Constructs new GlobalControllerAdvice with required services
     *
     * @param notificationService  service used to retrieve and manage user notifications
     * @param userService          service used to retrieve information about current user
     */
    @Autowired
    public GlobalControllerAdvice(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    } // GlobalControllerAdvice

    /**
     * Adds global attributes before any controller method is executed. If a user is logged
     * in, the method populates model with unread notification count and indicator flag.
     * Otherwise, default safe values are applied.
     *
     * @param model  model instance to which global attributes are added
     */
    @ModelAttribute
    public void addGlobalAttributes(Model model) {
        model.addAttribute("unreadNotificationCount", 0L);
        model.addAttribute("hasUnreadNotifications", false);
        User loggedInUser = userService.getLoggedInUser();
        if (loggedInUser != null) {
            try {
                long unreadCount = notificationService.getUnreadCount(Long.parseLong(loggedInUser.getUserId()));
                model.addAttribute("unreadNotificationCount", unreadCount);
                model.addAttribute("hasUnreadNotifications", unreadCount > 0);
            } catch (IllegalStateException e) {
                logger.warn("Unable to load the notification badge", e);
            } //try-catch
        } //if
    } //addGlobalAttributes
} //GlobalControllerAdvice
