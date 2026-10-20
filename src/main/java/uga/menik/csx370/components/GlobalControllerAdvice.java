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

@ControllerAdvice
public class GlobalControllerAdvice {
    private static final Logger logger = LoggerFactory.getLogger(GlobalControllerAdvice.class);

    private final NotificationService notificationService;
    private final UserService userService;

    @Autowired
    public GlobalControllerAdvice(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    } // GlobalControllerAdvice

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
            }
        } //if
    } //addGlobalAttributes
} //GlobalControllerAdvice
