package uga.menik.csx370.components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import uga.menik.csx370.models.User;
import uga.menik.csx370.services.NotificationService;
import uga.menik.csx370.utility.Utility;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final NotificationService notificationService;

    @Autowired
    public GlobalControllerAdvice(NotificationService notificationService) {
        this.notificationService = notificationService;
    } // GlobalControllerAdvice

    @ModelAttribute
    public void addGlobalAttributes(Model model) {
        User loggedInUser = Utility.getLoggedInUser();
        if (loggedInUser != null) {
            long unreadCount = notificationService.getUnreadCount(loggedInUser.getId());
            model.addAttribute("unreadNotificationCount", unreadCount);
            model.addAttribute("hasUnreadNotifications", unreadCount > 0);
        } //if
    } //addGlobalAttributes
} //GlobalControllerAdvice
