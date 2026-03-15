create table products (
    id bigint not null auto_increment,
    seller_id bigint not null,
    category_id bigint not null,
    name varchar(150) not null,
    price bigint not null,
    stock_quantity int not null,
    status varchar(20) not null,
    created_at timestamp(6) not null default current_timestamp(6),
    updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
    constraint pk_products primary key (id),
    constraint fk_products_seller foreign key (seller_id) references users (id),
    constraint fk_products_category foreign key (category_id) references categories (id),
    constraint chk_products_price_non_negative check (price >= 0),
    constraint chk_products_stock_non_negative check (stock_quantity >= 0)
);

create table product_images (
    id bigint not null auto_increment,
    product_id bigint not null,
    type varchar(20) not null,
    sort_order int not null,
    s3_key varchar(500) not null,
    created_at timestamp(6) not null default current_timestamp(6),
    updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
    constraint pk_product_images primary key (id),
    constraint fk_product_images_product foreign key (product_id) references products (id) on delete cascade,
    constraint uk_product_images_product_type_sort_order unique (product_id, type, sort_order),
    constraint uk_product_images_s3_key unique (s3_key),
    constraint chk_product_images_sort_order_positive check (sort_order >= 1)
);
