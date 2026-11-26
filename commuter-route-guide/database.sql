-- Optional: the Java server creates all of this automatically on first start.
-- You can also import this file in phpMyAdmin (http://localhost/phpmyadmin).
CREATE DATABASE IF NOT EXISTS routeph CHARACTER SET utf8mb4;
USE routeph;

CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL,
  salt VARCHAR(64) NOT NULL,
  verified TINYINT(1) NOT NULL DEFAULT 0,
  seen_advisory INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS otp_codes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(150) NOT NULL,
  code_hash VARCHAR(64) NOT NULL,
  expires_at DATETIME NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX (email)
);

CREATE TABLE IF NOT EXISTS comments (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  destination VARCHAR(50) NOT NULL,
  route_no INT NOT NULL,
  text TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS replies (
  id INT AUTO_INCREMENT PRIMARY KEY,
  comment_id INT NOT NULL,
  user_id INT NOT NULL,
  text TEXT NOT NULL,
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS search_history (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  origin VARCHAR(255),
  destination VARCHAR(50) NOT NULL,
  route_no INT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS comment_likes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  comment_id INT NOT NULL,
  user_id INT NOT NULL,
  is_read TINYINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_like (comment_id, user_id),
  FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Service advisories shown in the yellow "Latest update" banner and in Notifications.
-- To post a new one, just INSERT a row here (phpMyAdmin).
CREATE TABLE IF NOT EXISTS advisories (
  id INT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(150) NOT NULL,
  body TEXT NOT NULL,            -- short line shown on the notification card
  details TEXT NULL,             -- full text shown when the advisory is opened
  routes VARCHAR(255) NULL,      -- affected routes, e.g. 'Bulacan:1,Caloocan:2'
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- If the tables already existed from an older version, add the newer columns (safe to run many times on XAMPP/MariaDB).
ALTER TABLE users ADD COLUMN IF NOT EXISTS seen_advisory INT NOT NULL DEFAULT 0;
ALTER TABLE advisories ADD COLUMN IF NOT EXISTS details TEXT NULL;
ALTER TABLE advisories ADD COLUMN IF NOT EXISTS routes VARCHAR(255) NULL;

INSERT INTO advisories(title, body, details, routes) SELECT 'Road Closure Advisory', 'Route Closure Advisory: Road Closure and Alternative Routes Due to Ongoing Road Repairs', 'Attention, users Please be advised that Route 1 in Meycauayan, Bulacan, will be temporarily unavailable due to ongoing road repairs. Drivers are advised to use alternative routes until further notice.', 'Bulacan:1' WHERE NOT EXISTS (SELECT 1 FROM advisories);
UPDATE advisories SET details='Attention, users Please be advised that Route 1 in Meycauayan, Bulacan, will be temporarily unavailable due to ongoing road repairs. Drivers are advised to use alternative routes until further notice.', routes='Bulacan:1' WHERE details IS NULL;
