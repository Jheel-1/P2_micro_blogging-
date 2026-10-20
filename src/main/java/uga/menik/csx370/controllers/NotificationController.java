package uga.menik.csx370.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import uga.menik.csx370.models.Notification;
import uga.menik.csx370.models.User;
import uga.menik.csx370.services.NotificationService;
import uga.menik.csx370.utility.Utility;

import java.util.List;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Autowired
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    } // NotificationController

    @GetMapping
    public String getNotificationsPage(Model model) {
        User loggedInUser = Utility.getLoggedInUser();
        if (loggedInUser == null) {
            return "redirect:/login";
        } //if

        List<Notification> notifications = notificationService.getNotificationsForUser(loggedInUser.getId());
        notificationService.markAllAsRead(loggedInUser.getId());

        model.addAttribute("notifications", notifications);
        model.addAttribute("isLoggedIn", true);
        model.addAttribute("loggedUser", loggedInUser);
        
        return "notifications_page";
    } // getNotificationsPage
} // NotificationController
