USE hanye_take_out;

CREATE TABLE IF NOT EXISTS rider (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(16) NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1在线 0离线',
    max_load INT NOT NULL DEFAULT 2 COMMENT '最大并单数',
    speed_mps DECIMAL(6, 2) NOT NULL DEFAULT 4.00 COMMENT '速度 米/秒',
    current_node_id INT NULL COMMENT '当前所在节点',
    create_time DATETIME NOT NULL DEFAULT NOW(),
    update_time DATETIME NOT NULL DEFAULT NOW(),
    UNIQUE KEY uk_rider_phone (phone)
);

CREATE TABLE IF NOT EXISTS campus_node (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    lng DECIMAL(10, 6) NOT NULL,
    lat DECIMAL(10, 6) NOT NULL,
    node_type VARCHAR(16) NOT NULL COMMENT 'SHOP/DROPOFF/ROAD',
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT NOW(),
    update_time DATETIME NOT NULL DEFAULT NOW(),
    UNIQUE KEY uk_campus_node_name (name)
);

CREATE TABLE IF NOT EXISTS campus_edge (
    id INT PRIMARY KEY AUTO_INCREMENT,
    from_node_id INT NOT NULL,
    to_node_id INT NOT NULL,
    distance_m INT NOT NULL,
    cost_time_sec INT NOT NULL,
    bidirectional TINYINT NOT NULL DEFAULT 1 COMMENT '1双向 0单向',
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT NOW(),
    update_time DATETIME NOT NULL DEFAULT NOW(),
    UNIQUE KEY uk_campus_edge (from_node_id, to_node_id)
);

CREATE TABLE IF NOT EXISTS dispatch_task (
    id INT PRIMARY KEY AUTO_INCREMENT,
    order_id INT NOT NULL,
    rider_id INT NOT NULL,
    shop_node_id INT NOT NULL,
    dropoff_node_id INT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0已分配 1派送中 2已完成 3已取消',
    assign_score DECIMAL(10, 2) NULL,
    eta_sec INT NULL,
    route_node_ids TEXT NULL COMMENT 'JSON节点id数组',
    progress_index INT NOT NULL DEFAULT 0,
    assign_time DATETIME NOT NULL DEFAULT NOW(),
    update_time DATETIME NOT NULL DEFAULT NOW(),
    KEY idx_dispatch_order_id (order_id),
    KEY idx_dispatch_rider_id (rider_id)
);

SET @stmt = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'address_book'
          AND COLUMN_NAME = 'campus_node_id'
    ),
    'SELECT 1',
    'ALTER TABLE address_book ADD COLUMN campus_node_id INT NULL COMMENT ''绑定校园节点id'''
);
PREPARE s1 FROM @stmt;
EXECUTE s1;
DEALLOCATE PREPARE s1;

SET @stmt = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'address_book'
          AND COLUMN_NAME = 'is_public'
    ),
    'SELECT 1',
    'ALTER TABLE address_book ADD COLUMN is_public TINYINT NOT NULL DEFAULT 0 COMMENT ''0用户地址 1公共地址'''
);
PREPARE s2 FROM @stmt;
EXECUTE s2;
DEALLOCATE PREPARE s2;

INSERT INTO campus_node (id, name, lng, lat, node_type, status, create_time, update_time) VALUES
(1, '寒页餐厅', 113.264450, 23.129160, 'SHOP', 1, NOW(), NOW()),
(2, '南区1号宿舍', 113.265100, 23.128700, 'DROPOFF', 1, NOW(), NOW()),
(3, '南区2号宿舍', 113.266000, 23.128900, 'DROPOFF', 1, NOW(), NOW()),
(4, '北区1号宿舍', 113.264900, 23.130100, 'DROPOFF', 1, NOW(), NOW()),
(5, '图书馆路口', 113.265200, 23.129300, 'ROAD', 1, NOW(), NOW()),
(6, '操场路口', 113.264200, 23.129800, 'ROAD', 1, NOW(), NOW()),
(7, '教学楼T字口', 113.265760, 23.129540, 'ROAD', 1, NOW(), NOW()),
(8, '东门路口', 113.266520, 23.129340, 'ROAD', 1, NOW(), NOW()),
(9, '西门路口', 113.263560, 23.129560, 'ROAD', 1, NOW(), NOW()),
(10, '实验楼路口', 113.265860, 23.130050, 'ROAD', 1, NOW(), NOW()),
(11, '北区2号宿舍', 113.265560, 23.130520, 'DROPOFF', 1, NOW(), NOW()),
(12, '东区生活区', 113.266920, 23.129680, 'DROPOFF', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
lng = VALUES(lng),
lat = VALUES(lat),
node_type = VALUES(node_type),
status = VALUES(status),
update_time = NOW();

INSERT INTO campus_edge (id, from_node_id, to_node_id, distance_m, cost_time_sec, bidirectional, status, create_time, update_time) VALUES
(1, 1, 5, 220, 60, 1, 1, NOW(), NOW()),
(2, 5, 2, 300, 80, 1, 1, NOW(), NOW()),
(3, 5, 3, 420, 110, 1, 1, NOW(), NOW()),
(4, 1, 6, 200, 55, 1, 1, NOW(), NOW()),
(5, 6, 4, 280, 75, 1, 1, NOW(), NOW()),
(6, 5, 6, 180, 50, 1, 1, NOW(), NOW()),
(7, 5, 7, 210, 58, 1, 1, NOW(), NOW()),
(8, 7, 8, 190, 52, 1, 1, NOW(), NOW()),
(9, 8, 3, 260, 70, 1, 1, NOW(), NOW()),
(10, 7, 10, 230, 62, 1, 1, NOW(), NOW()),
(11, 10, 11, 210, 56, 1, 1, NOW(), NOW()),
(12, 8, 12, 170, 46, 1, 1, NOW(), NOW()),
(13, 6, 9, 250, 68, 1, 1, NOW(), NOW()),
(14, 9, 2, 330, 90, 1, 1, NOW(), NOW()),
(15, 6, 7, 260, 72, 1, 1, NOW(), NOW()),
(16, 10, 3, 310, 84, 1, 1, NOW(), NOW()),
(17, 1, 9, 240, 65, 1, 1, NOW(), NOW()),
(18, 7, 11, 320, 88, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
distance_m = VALUES(distance_m),
cost_time_sec = VALUES(cost_time_sec),
bidirectional = VALUES(bidirectional),
status = VALUES(status),
update_time = NOW();

INSERT INTO rider (id, name, phone, status, max_load, speed_mps, current_node_id, create_time, update_time) VALUES
(1, '骑手-阿东', '13810000001', 1, 2, 4.30, 5, NOW(), NOW()),
(2, '骑手-小白', '13810000002', 1, 2, 4.05, 7, NOW(), NOW()),
(3, '骑手-阿成', '13810000003', 1, 3, 4.15, 9, NOW(), NOW()),
(4, '骑手-阿良', '13810000004', 1, 2, 3.95, 10, NOW(), NOW()),
(5, '骑手-阿泽', '13810000005', 1, 2, 4.00, 8, NOW(), NOW()),
(6, '骑手-阿宁', '13810000006', 1, 2, 4.10, 11, NOW(), NOW()),
(7, '骑手-阿越', '13810000007', 0, 2, 4.00, 6, NOW(), NOW())
ON DUPLICATE KEY UPDATE
name = VALUES(name),
status = VALUES(status),
max_load = VALUES(max_load),
speed_mps = VALUES(speed_mps),
current_node_id = VALUES(current_node_id),
update_time = NOW();

INSERT INTO address_book
(user_id, consignee, phone, gender, province_code, province_name, city_code, city_name, district_code, district_name, detail, label, is_default, campus_node_id, is_public)
SELECT
    0,
    '校园收货点',
    '13800000000',
    1,
    '440000',
    '广东省',
    '440100',
    '广州市',
    '440106',
    '番禺区',
    n.name,
    '校园节点',
    0,
    n.id,
    1
FROM campus_node n
WHERE n.node_type = 'DROPOFF'
  AND n.status = 1
  AND NOT EXISTS (
      SELECT 1
      FROM address_book ab
      WHERE ab.is_public = 1
        AND ab.campus_node_id = n.id
  );

-- 清理历史冲突地址：要求默认地址只能来自校园路网节点
DELETE ab
FROM address_book ab
LEFT JOIN campus_node n ON n.id = ab.campus_node_id
WHERE ab.is_public = 1
  AND (
      ab.campus_node_id IS NULL
      OR n.id IS NULL
      OR n.node_type <> 'DROPOFF'
      OR n.status <> 1
  );

DELETE ab
FROM address_book ab
LEFT JOIN campus_node n ON n.id = ab.campus_node_id
WHERE ab.is_public = 0
  AND (
      ab.campus_node_id IS NULL
      OR n.id IS NULL
      OR n.node_type <> 'DROPOFF'
      OR n.status <> 1
  );
