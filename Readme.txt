MICROBLOGGING Project

GROUP MEMBERS AND RESPONSIBILITIES
----------------------------------
Emily Kim: Making posts as the logged-in user and the home feed, including
posts from followed users. Also added follow/unfollow work and Eastern Time
formatting.

Jheel Shah: Hashtag search and the features shown on each post: likes,
bookmarks, comments, counts, and post dates.

Vishaka Venkatraman: Database design and the new non-trivial feature,
notifications.

Thamim Parvin: Profile pages for the logged-in user and other users.

Jessica Li: People page and bookmarks page.


files
----------------
- src/main: application code and pages.
- database_setup.sql: nine tables, keys, constraints, and demo inserts.
- dml.sql: application queries with simple explanations, parameters, and URLs.
- src/test: unit tests and separate MySQL integration tests.


FIRST-TIME DATABASE SETUP
------------------------
Run commands from the folder containing pom.xml.

cd "C:\path\to\extracted-project"

Start Docker Desktop.

docker start mysql-server-x370

Copy the setup file into the container:
docker cp database_setup.sql mysql-server-x370:/tmp/database_setup.sql

Open MySQL:
docker exec -it mysql-server-x370 mysql -u root -p
enter mysqlpass.

At the mysql prompt run:
SOURCE /tmp/database_setup.sql;

This creates csx370_mb_platform and inserts the demo rows into every table.

USE csx370_mb_platform;
SHOW TABLES;
SELECT COUNT(*) AS number_of_posts FROM post;
exit;

Exit MySQL.

The connection settings are in src/main/resources/application.properties.
The supplied settings use localhost:33306, database csx370_mb_platform,
username root, and the local password mysqlpass.

COMPILE AND RUN
---------------
   mvn clean test

   mvn package

Start from Windows PowerShell:
   mvn spring-boot:run --% -Dspring-boot.run.arguments="--server.port=8081"

Start from Windows Command Prompt:
   mvn spring-boot:run -D"spring-boot.run.arguments=--server.port=8081"

Start from macOS/Linux:
   mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081

Open Chrome at http://localhost:8081/

register multiple credentials and interact with features if desired. 


Stop the app with Ctrl+C in its terminal. 




NEW FEATURE: NOTIFICATIONS!!
--------------------------
Open Notifications in the top navigation bar.

A user receives an alert when another user:
- follows them;
- likes one of their posts;
- comments on one of their posts; or
- mentions their @username in a new post.

Usernames are visible on the People page. To try mentions, log in as Alex
and write: Thanks @maya for the study notes!
Then log out and log in as Maya to see the new notification.

A number in the navigation bar shows the unread count. The Notifications
page can filter by activity type and by unread status. Each alert links to
the related post or follower's profile. Use Mark as read for one alert, or
Mark all as read for your entire notification list. Opening the page by
itself does not mark notifications read.

The feature includes each required application part:
UI:
  src/main/resources/templates/notifications_page.mustache
  src/main/resources/templates/fragments/top_bar.mustache
  src/main/resources/static/css/styles.css
Controller:
  src/main/java/uga/menik/csx370/controllers/NotificationController.java
Service:
  src/main/java/uga/menik/csx370/services/NotificationService.java
  PostService.java and PeopleService.java create alerts after user actions.
  components/GlobalControllerAdvice.java adds the navigation unread count.
SQL/schema:
  The notifications table in database_setup.sql.
  Notification SELECT, INSERT, COUNT and UPDATE queries in dml.sql.

The service uses joins, optional filters, sorting, and updates scoped to the
logged-in recipient. This makes the feature more than a static alert list.
Each action and its alert use one transaction: both save, or both roll back.
Repeated follow/like requests do not send duplicate alerts. A later unlike
and new like, or unfollow and new follow, is a new event. Self-actions do not
send alerts. A repeated @mention in the same post sends one alert per user.
Unknown usernames and email addresses do not send mention alerts.
Bookmarks are private and do not send alerts.

The stored target_id points to a post for LIKE, COMMENT and MENTION.
For FOLLOW it stores the follower's user ID. Because the target can belong
to different tables, this column has no foreign key in the current schema.
A missing target post shows as unavailable instead of a broken post link.






DEMO ACCOUNTS
-------------
All four fictional demo accounts use the password DemoPass123!
Usernames: alex, maya, jordan, sam.
The final snapshot also contains demo_new_member (Taylor Morgan), registered
during the demo with the same password. Some notifications are already read
because the demo tested the read controls.

Alex follows Maya. Alex's home page therefore contains Alex's and Maya's
posts. Jordan's posts are visible on Jordan's profile, but do not appear in
Alex's feed until Alex follows Jordan. Sam has no posts, so Sam's last post
date is Unknown.

Try these searches:
   #database #study
   #2025 #fireworks

Every requested hashtag must be in a result. For example, a post with #2025
but no #fireworks is excluded from the second search. Results show newest
posts first. Hashtags ignore letter case and must be separated by spaces.

Post and comment text cannot be empty and is limited to 400 characters.
The profile button shows your own posts. Clicking a person's name opens
that person's posts. Comments are shown oldest first. Bookmarks belong to
the logged-in user and are ordered by the post date, not the date saved.


DATABASE AND TIME
-----------------
The application sets each database connection's time zone to UTC.
DisplayTime converts values to America/New_York on every page, including
daylight saving time. The display looks like Mar 07, 2025, 10:54 PM.
The sample SQL dates are UTC. Older DATETIME values from another time-zone
convention are not automatically changed by this setup.

Primary keys identify rows. Foreign keys connect related rows. Pair keys
prevent duplicate likes, bookmarks, follows and post/hashtag links.
A comment has its own ID, so one person can comment several times on a post.
Deleting a user or post removes its dependent rows through ON DELETE CASCADE.
The application has no delete-post or delete-account screen.

TESTS
-----
mvn test runs the unit tests without MySQL.

For integration tests, create a separate, empty database named
csx370_profile_test in a test MySQL instance. Do not use project database.
Set these variables in PowerShell, adjusting the port and test credentials:
   $env:PROFILE_TEST_JDBC_URL='jdbc:mysql://127.0.0.1:33307/csx370_profile_test?useSSL=false&allowPublicKeyRetrieval=true'
   $env:PROFILE_TEST_USER='root'
   $env:PROFILE_TEST_PASSWORD='your-test-password'

Then run:
   mvn test "-Dtest=ProfileControllerTest,PostServiceTest,DisplayTimeTest,ProfileServiceIT,ApplicationFeaturesIT"

The tests check author filtering, feed rules, ordering, viewer-specific
states, hashtag searches, following, bookmarks, comments, notifications,
read permissions, duplicate requests, and rollback after failed writes.
They load the table definitions but not the demo seed rows. Use only an
isolated test database, since the profile tests reserve IDs 9001-9004.

RESOURCES CONSULTED
-------------------
Instructor-provided starter code and project instructions.
Spring Boot 3.1.4 database configuration:
https://docs.spring.io/spring-boot/docs/3.1.4/reference/html/data.html
MySQL time-zone behavior:
https://dev.mysql.com/doc/refman/8.0/en/time-zone-support.html
Java 17 date/time conversion:
https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZonedDateTime.html

