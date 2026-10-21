-- Create the database.
-- Run once on a fresh project database. This file does not delete existing data.
-- The demo import refuses nonempty tables. See Readme.txt for a separate setup.
-- All sample dates below are UTC; the app displays Eastern Time.
SET time_zone = '+00:00';
create database if not exists csx370_mb_platform;

-- Use the created database.
use csx370_mb_platform;

-- Create the user table.
create table if not exists user (
    userId int auto_increment,
    username varchar(255) not null,
    password varchar(255) not null,
    firstName varchar(255) not null,
    lastName varchar(255) not null,
    primary key (userId),
    unique (username),
    constraint userName_min_length check (char_length(trim(userName)) >= 2),
    constraint firstName_min_length check (char_length(trim(firstName)) >= 2),
    constraint lastName_min_length check (char_length(trim(lastName)) >= 2)
);

-- Create the post table.
create table if not exists post (
    postId int auto_increment,
    userId int not null,
    content varchar(400) not null,
    timestamp datetime not null,
    primary key (postId),
    foreign key (userId) references user(userId) on delete cascade,
    constraint content_min_length check (char_length(trim(content)) >= 1)
);

-- A normalized hashtag is shared by every post that uses it.
create table if not exists hashtag (
    hashtag varchar(100) not null,
    primary key (hashtag)
);

create table if not exists post_hashtag (
    postId int not null,
    hashtag varchar(100) not null,
    primary key (postId, hashtag),
    constraint post_hashtag_post_fk foreign key (postId) references post(postId)
        on delete cascade,
    constraint post_hashtag_hashtag_fk foreign key (hashtag) references hashtag(hashtag)
        on delete cascade
);

-- Relationships are directional: followerId follows followeeId.
create table if not exists user_follow (
    followerId int not null,
    followeeId int not null,
    followedAt timestamp not null default current_timestamp,
    primary key (followerId, followeeId),
    constraint user_follow_follower_fk foreign key (followerId) references user(userId)
        on delete cascade,
    constraint user_follow_followee_fk foreign key (followeeId) references user(userId)
        on delete cascade,
    constraint user_follow_not_self check (followerId <> followeeId)
);

-- Create the comment table.
create table if not exists comment (
    commentId int auto_increment,
    postId int not null,
    userId int not null,
    content varchar(400) not null,
    timestamp datetime not null,
    primary key (commentId),
    foreign key (postId) references post(postId) on delete cascade,
    foreign key (userId) references user(userId) on delete cascade,
    constraint comment_content_min_length check (char_length(trim(content)) >= 1)
);

-- Create the likes table.
create table if not exists likes (
    userId int not null,
    postId int not null,
    primary key (userId, postId),
    foreign key (userId) references user(userId) on delete cascade,
    foreign key (postId) references post(postId) on delete cascade
);

-- Create the bookmarks table.
create table if not exists bookmarks (
    userId int not null,
    postId int not null,
    primary key (userId, postId),
    foreign key (userId) references user(userId) on delete cascade,
    foreign key (postId) references post(postId) on delete cascade
);

-- Create notifications table; this is for additional feature
create table if not exists notifications (
     id int auto_increment primary key,
     recipient_id int not null,
     actor_id int not null,
     type varchar(400) not null,
     target_id int default null,
     is_read boolean default false,
     created_at timestamp default current_timestamp,
     foreign key (recipient_id) references user(userId) on delete cascade,
     foreign key (actor_id) references user(userId) on delete cascade
);

-- Demo data: final snapshot after the verified Chrome demonstration.
-- All accounts are fictional and use DemoPass123!; passwords are BCrypt hashes.
-- Export includes every row in every table, including read notification states.
-- Keep these inserts together: a failed import must not attach posts to old users.
DROP PROCEDURE IF EXISTS p2_load_demo_data;
DELIMITER //
CREATE PROCEDURE p2_load_demo_data()
BEGIN
    -- Undo every demo insert if any statement fails, then show the original error.
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    -- Fixed demo IDs are safe only when all project tables are empty.
    IF EXISTS (SELECT 1 FROM user)
        OR EXISTS (SELECT 1 FROM post)
        OR EXISTS (SELECT 1 FROM hashtag)
        OR EXISTS (SELECT 1 FROM post_hashtag)
        OR EXISTS (SELECT 1 FROM user_follow)
        OR EXISTS (SELECT 1 FROM comment)
        OR EXISTS (SELECT 1 FROM likes)
        OR EXISTS (SELECT 1 FROM bookmarks)
        OR EXISTS (SELECT 1 FROM notifications) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Demo setup requires empty tables. Existing rows were kept. See Readme.txt for a separate demo setup.';
    END IF;

START TRANSACTION;
INSERT INTO `user` (`userId`, `username`, `password`, `firstName`, `lastName`) VALUES (1,'alex','$2a$10$.4RX...B9Rw6msDG0AcifeZLaf8h7K3yEceXhSKi/pwx198juiSSy','Alex','Rivera');
INSERT INTO `user` (`userId`, `username`, `password`, `firstName`, `lastName`) VALUES (2,'maya','$2a$10$.4RX...B9Rw6msDG0AcifeZLaf8h7K3yEceXhSKi/pwx198juiSSy','Maya','Chen');
INSERT INTO `user` (`userId`, `username`, `password`, `firstName`, `lastName`) VALUES (3,'jordan','$2a$10$.4RX...B9Rw6msDG0AcifeZLaf8h7K3yEceXhSKi/pwx198juiSSy','Jordan','Brooks');
INSERT INTO `user` (`userId`, `username`, `password`, `firstName`, `lastName`) VALUES (4,'sam','$2a$10$.4RX...B9Rw6msDG0AcifeZLaf8h7K3yEceXhSKi/pwx198juiSSy','Sam','Patel');
INSERT INTO `user` (`userId`, `username`, `password`, `firstName`, `lastName`) VALUES (5,'demo_new_member','$2a$10$PH/RpfvtXpRCCqll8HdvROGQaK3OsMS5DwUjiGz1GE6RVFflQ4KPm','Taylor','Morgan');
INSERT INTO `post` (`postId`, `userId`, `content`, `timestamp`) VALUES (1,1,'Studying database keys at the library. #database #study','2026-10-18 16:00:00');
INSERT INTO `post` (`postId`, `userId`, `content`, `timestamp`) VALUES (2,1,'Thanks @maya for helping with our database diagram! #database #teamwork','2026-10-19 14:00:00');
INSERT INTO `post` (`postId`, `userId`, `content`, `timestamp`) VALUES (3,2,'Our study group finished the first database practice set. #database #study','2026-10-19 15:30:00');
INSERT INTO `post` (`postId`, `userId`, `content`, `timestamp`) VALUES (4,3,'A good morning walk around campus. #campus','2026-10-19 17:00:00');
INSERT INTO `post` (`postId`, `userId`, `content`, `timestamp`) VALUES (5,2,'Looking forward to the campus fireworks. #2025 #fireworks','2026-10-19 18:00:00');
INSERT INTO `post` (`postId`, `userId`, `content`, `timestamp`) VALUES (6,3,'Saved photos from last year. #2025 #memories','2026-10-19 18:00:00');
INSERT INTO `post` (`postId`, `userId`, `content`, `timestamp`) VALUES (7,1,'Reviewing joins before the next quiz. #database #study','2026-10-20 16:00:00');
INSERT INTO `post` (`postId`, `userId`, `content`, `timestamp`) VALUES (8,1,'Thanks @maya for the study notes! #database #study','2026-10-20 23:26:48');
INSERT INTO `hashtag` (`hashtag`) VALUES ('2025');
INSERT INTO `hashtag` (`hashtag`) VALUES ('campus');
INSERT INTO `hashtag` (`hashtag`) VALUES ('database');
INSERT INTO `hashtag` (`hashtag`) VALUES ('fireworks');
INSERT INTO `hashtag` (`hashtag`) VALUES ('memories');
INSERT INTO `hashtag` (`hashtag`) VALUES ('study');
INSERT INTO `hashtag` (`hashtag`) VALUES ('teamwork');
INSERT INTO `post_hashtag` (`postId`, `hashtag`) VALUES (5,'2025');
INSERT INTO `post_hashtag` (`postId`, `hashtag`) VALUES (6,'2025');
INSERT INTO `post_hashtag` (`postId`, `hashtag`) VALUES (4,'campus');
INSERT INTO `post_hashtag` (`postId`, `hashtag`) VALUES (1,'database');
INSERT INTO `post_hashtag` (`postId`, `hashtag`) VALUES (2,'database');
INSERT INTO `post_hashtag` (`postId`, `hashtag`) VALUES (3,'database');
INSERT INTO `post_hashtag` (`postId`, `hashtag`) VALUES (7,'database');
INSERT INTO `post_hashtag` (`postId`, `hashtag`) VALUES (8,'database');
INSERT INTO `post_hashtag` (`postId`, `hashtag`) VALUES (5,'fireworks');
INSERT INTO `post_hashtag` (`postId`, `hashtag`) VALUES (6,'memories');
INSERT INTO `post_hashtag` (`postId`, `hashtag`) VALUES (1,'study');
INSERT INTO `post_hashtag` (`postId`, `hashtag`) VALUES (3,'study');
INSERT INTO `post_hashtag` (`postId`, `hashtag`) VALUES (7,'study');
INSERT INTO `post_hashtag` (`postId`, `hashtag`) VALUES (8,'study');
INSERT INTO `post_hashtag` (`postId`, `hashtag`) VALUES (2,'teamwork');
INSERT INTO `user_follow` (`followerId`, `followeeId`, `followedAt`) VALUES (1,2,'2026-10-18 12:00:00');
INSERT INTO `user_follow` (`followerId`, `followeeId`, `followedAt`) VALUES (2,1,'2026-10-18 12:05:00');
INSERT INTO `user_follow` (`followerId`, `followeeId`, `followedAt`) VALUES (3,1,'2026-10-18 12:10:00');
INSERT INTO `user_follow` (`followerId`, `followeeId`, `followedAt`) VALUES (3,2,'2026-10-18 12:15:00');
INSERT INTO `user_follow` (`followerId`, `followeeId`, `followedAt`) VALUES (4,2,'2026-10-19 12:00:00');
INSERT INTO `comment` (`commentId`, `postId`, `userId`, `content`, `timestamp`) VALUES (1,1,2,'The examples with composite keys helped me too.','2026-10-18 16:05:00');
INSERT INTO `comment` (`commentId`, `postId`, `userId`, `content`, `timestamp`) VALUES (2,1,3,'I will join the study group tomorrow.','2026-10-18 16:10:00');
INSERT INTO `comment` (`commentId`, `postId`, `userId`, `content`, `timestamp`) VALUES (3,3,1,'Great work! Let us review foreign keys next.','2026-10-19 15:40:00');
INSERT INTO `comment` (`commentId`, `postId`, `userId`, `content`, `timestamp`) VALUES (4,5,1,'Looking forward to seeing everyone there!','2026-10-20 23:26:48');
INSERT INTO `likes` (`userId`, `postId`) VALUES (2,1);
INSERT INTO `likes` (`userId`, `postId`) VALUES (3,1);
INSERT INTO `likes` (`userId`, `postId`) VALUES (1,3);
INSERT INTO `likes` (`userId`, `postId`) VALUES (2,4);
INSERT INTO `likes` (`userId`, `postId`) VALUES (1,5);
INSERT INTO `bookmarks` (`userId`, `postId`) VALUES (2,1);
INSERT INTO `bookmarks` (`userId`, `postId`) VALUES (1,3);
INSERT INTO `bookmarks` (`userId`, `postId`) VALUES (3,3);
INSERT INTO `bookmarks` (`userId`, `postId`) VALUES (1,6);
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (1,2,1,'FOLLOW',1,1,'2026-10-18 12:00:00');
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (2,1,2,'FOLLOW',2,1,'2026-10-18 12:05:00');
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (3,1,3,'FOLLOW',3,1,'2026-10-18 12:10:00');
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (4,2,3,'FOLLOW',3,1,'2026-10-18 12:15:00');
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (5,1,2,'LIKE',1,0,'2026-10-18 16:02:00');
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (6,1,3,'LIKE',1,0,'2026-10-18 16:03:00');
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (7,1,2,'COMMENT',1,0,'2026-10-18 16:05:00');
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (8,1,3,'COMMENT',1,0,'2026-10-18 16:10:00');
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (9,2,4,'FOLLOW',4,1,'2026-10-19 12:00:00');
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (10,2,1,'MENTION',2,1,'2026-10-19 14:00:00');
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (11,2,1,'LIKE',3,1,'2026-10-19 15:35:00');
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (12,2,1,'COMMENT',3,1,'2026-10-19 15:40:00');
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (13,3,2,'LIKE',4,0,'2026-10-19 17:05:00');
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (14,3,1,'FOLLOW',1,0,'2026-10-20 23:26:47');
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (15,2,1,'LIKE',5,1,'2026-10-20 23:26:48');
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (16,2,1,'COMMENT',5,1,'2026-10-20 23:26:48');
INSERT INTO `notifications` (`id`, `recipient_id`, `actor_id`, `type`, `target_id`, `is_read`, `created_at`) VALUES (17,2,1,'MENTION',8,1,'2026-10-20 23:26:48');
COMMIT;
    SELECT 'Demo setup complete: 5 users and 8 posts loaded.' AS setup_result;
END//
DELIMITER ;
CALL p2_load_demo_data();
DROP PROCEDURE IF EXISTS p2_load_demo_data;
