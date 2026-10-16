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