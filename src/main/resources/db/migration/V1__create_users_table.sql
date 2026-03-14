create table users (
    id bigint not null auto_increment,
    role varchar(20) not null,
    name varchar(100) not null,
    email varchar(255) not null,
    password_hash varchar(255) not null,
    phone_number varchar(30) null,
    postal_code varchar(20) null,
    created_at timestamp(6) not null default current_timestamp(6),
    updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
    constraint pk_users primary key (id),
    constraint uk_users_email unique (email),
    constraint uk_users_phone_number unique (phone_number)
);
