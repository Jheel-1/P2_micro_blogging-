P2 MICROBLOGGING PLATFORM

PROFILE-PAGE CONTRIBUTION
Thamim Parvin: database-backed own-user and selected-user profile pages,
profile query documentation, and profile tests.

Access the feature:
- Click Profile in the top navigation to view the logged-in user's posts.
  URL: http://localhost:8081/profile
- Click a user's name on a post, comment, or the People page to view that
  user's posts. Example URL: http://localhost:8081/profile/1
- Posts are ordered newest first. Equal timestamps are ordered by postId
  descending so the display is consistent.
- Like and comment counts come from the database. Like/bookmark icons reflect
  the logged-in viewer, even when viewing somebody else's profile.
- A profile with no posts displays the existing no-content message. Database
  failures display an error rather than pretending the profile is empty.

Code locations:
- Controller: src/main/java/uga/menik/csx370/controllers/ProfileController.java
- Service/query: src/main/java/uga/menik/csx370/services/ProfileService.java
- UI: existing templates/posts_page.mustache and fragments/post.mustache.
  Existing profile links in top_bar.mustache, post.mustache, comment.mustache,
  and followable_user.mustache are reused.
- SQL: the profile retrieval statement is documented in dml.sql, with page
  URLs and the order of its three prepared-statement parameters.
- Schema: this feature uses the existing user, post, comment, likes, and
  bookmarks tables. No schema changes or new dependencies are required.

RUNNING THE APPLICATION
1. Start the group's MySQL Docker container. Its MySQL port 3306 should be
   published as localhost:33306.
2. For a new database, run database_setup.sql in MySQL. Check that the
   connection settings in src/main/resources/application.properties match
   your local database, or supply SPRING_DATASOURCE_URL,
   SPRING_DATASOURCE_USERNAME, and SPRING_DATASOURCE_PASSWORD as environment
   variables. Do not replace an existing populated database for these tests.
3. Open PowerShell in the directory containing this pom.xml and run:
   mvn spring-boot:run --% -Dspring-boot.run.arguments="--server.port=8081"
4. Open Chrome at http://localhost:8081/, then register or log in.

Profile pages need real post rows for the selected user. Other unfinished
features may still use starter mock data; the profile implementation does not
depend on the mock lists or complete those other features.
In particular, HomeController and PeopleController currently supply mock
posts/users. Until those contributions are integrated, use a known real
/profile/{userId} URL or click the author on a real /post/{postId} page to
check profiles. The existing People-page profile links can be reused when
that page is connected to real users.

PROFILE TESTS
Run the five controller tests without a database:
  mvn test

The tests use the existing JUnit 3 dependency. They check own-user selection,
selected-user/viewer separation, empty profiles, database-error handling, and
invalid non-positive user IDs.

Optional MySQL integration tests:
1. Create a separate empty database named csx370_profile_test. Use a test
   database account with permission to create tables and insert test data.
2. In PowerShell, set these variables using your test server's connection
   details (the port below is just an example):
   $env:PROFILE_TEST_JDBC_URL = 'jdbc:mysql://localhost:33307/csx370_profile_test'
   $env:PROFILE_TEST_USER = '<test-database-user>'
   $env:PROFILE_TEST_PASSWORD = '<test-database-password>'
3. Run:
   mvn '-Dtest=ProfileServiceIT' test

ProfileServiceIT applies the project's actual table definitions to that test
database, inserts temporary fixture rows within a transaction, and rolls the
rows back after each test. It requires the separate database name to avoid
accidentally targeting the application database. The five checks cover author
filtering, ordering and timestamp ties, accurate counts, viewer-specific
states, empty profiles, and the required date display format.

MANUAL PROFILE CHECKS IN CHROME
- Use at least two users with posts at different times and one user without
  posts. Log in as one of the users with posts.
- Click Profile: only your posts should appear, newest first.
- Click another user's name: only that user's posts should appear.
- Give the viewer and profile owner different like/bookmark states on the
  same post. Verify that the profile page displays the viewer's states.
- Open the profile of the user without posts and verify the empty message.
- From a profile post, open its comments, then click a comment author's name
  to verify the link returns that author's profile.

GROUP DELIVERABLES STILL TO COMPLETE
The remaining members should add their names, contributions, queries, and
feature documentation as their work is integrated. The extra feature and the
final ER diagram are pending the team's final design. The final seed data and
demo video are separate group deliverables. This README documents the profile
contribution; it does not claim the entire assignment is complete.

RESOURCES FOR THIS CONTRIBUTION
The provided project instructions and the existing starter/group source code
and database schema were used. No additional libraries were introduced.
