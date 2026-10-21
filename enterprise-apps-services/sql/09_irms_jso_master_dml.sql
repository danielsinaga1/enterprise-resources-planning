-- JSO Master Data Tables
-- Run this on db_nirms database

CREATE TABLE IF NOT EXISTS jso_leader_master (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(150) NOT NULL,
  npp VARCHAR(50) NULL,
  department VARCHAR(150) NULL,
  position VARCHAR(150) NULL,
  is_active TINYINT(1) DEFAULT 1,
  created_at DATETIME NULL,
  updated_at DATETIME NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS jso_member_master (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(150) NOT NULL,
  npp VARCHAR(50) NULL,
  department VARCHAR(150) NULL,
  position VARCHAR(150) NULL,
  is_active TINYINT(1) DEFAULT 1,
  created_at DATETIME NULL,
  updated_at DATETIME NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
