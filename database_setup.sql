-- Create the database.
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
     id int auto_increment primary key.
     recipient_id int not null,
     actor_id int not null,
     type varchar(400) not null,
     target_id int default null,
     is_read boolean default false,
     created_at timestamp default current_timestamp,
     foreign key (recipient_id) references users(id) on delete cascade,
     foreign key (actor_id) references users(id) on delete cascade
);
