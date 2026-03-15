create table categories (
    id bigint not null auto_increment,
    name varchar(100) not null,
    created_by_user_id bigint not null,
    updated_by_user_id bigint not null,
    created_at timestamp(6) not null default current_timestamp(6),
    updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
    constraint pk_categories primary key (id),
    constraint uk_categories_name unique (name),
    constraint fk_categories_created_by_user foreign key (created_by_user_id) references users (id),
    constraint fk_categories_updated_by_user foreign key (updated_by_user_id) references users (id)
);
