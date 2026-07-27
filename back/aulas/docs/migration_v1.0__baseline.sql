-- MySQL dump 10.13  Distrib 8.4.9, for Win64 (x86_64)
--
-- Host: localhost    Database: test_aulas
-- ------------------------------------------------------
-- Server version	8.4.9

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `classroom_resources`
--

DROP TABLE IF EXISTS `classroom_resources`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `classroom_resources` (
  `quantity` int NOT NULL,
  `classroom_id` bigint NOT NULL,
  `resource_id` int NOT NULL,
  PRIMARY KEY (`classroom_id`,`resource_id`),
  KEY `fk_classroom_resources_resource` (`resource_id`),
  CONSTRAINT `fk_classroom_resources_resource` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKa32h9xyoubo2bn07v1jqay37c` FOREIGN KEY (`classroom_id`) REFERENCES `classrooms` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `classrooms`
--

DROP TABLE IF EXISTS `classrooms`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `classrooms` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `capacity` bigint NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `name` varchar(255) NOT NULL,
  `uuid` binary(16) NOT NULL,
  `linked_room_id` bigint DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `type` enum('AUDITORIO','AULA','LABORATORIO','SALA_SEMINARIOS') NOT NULL,
  `room_image_url` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7yey8vi13fi8vuxrarkj0ortc` (`name`),
  UNIQUE KEY `UK6m2xmvcjl6iriqge6eq3f6o25` (`uuid`),
  KEY `FKcqcdndcettchhmt5hnxerkxfc` (`linked_room_id`),
  CONSTRAINT `FKcqcdndcettchhmt5hnxerkxfc` FOREIGN KEY (`linked_room_id`) REFERENCES `classrooms` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reserv_instances`
--

DROP TABLE IF EXISTS `reserv_instances`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reserv_instances` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `date` date NOT NULL,
  `status` enum('CANCELLED_BY_ADMIN','CANCELLED_BY_USER','ACTIVE') DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `classroom_id` bigint NOT NULL,
  `group_id` bigint NOT NULL,
  `attendee_count` int NOT NULL,
  `reassigned` bit(1) NOT NULL,
  `title` varchar(150) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKm7m0q36fsesghli0hs6smv3dm` (`uuid`),
  KEY `FKapl8gv473y8s92g5qasg1000` (`classroom_id`),
  KEY `idx_reserv_instances_group_status` (`group_id`,`status`),
  KEY `idx_reserv_instances_status_date_group` (`status`,`date`,`group_id`),
  CONSTRAINT `FKapl8gv473y8s92g5qasg1000` FOREIGN KEY (`classroom_id`) REFERENCES `classrooms` (`id`),
  CONSTRAINT `FKoqg3f0uctg5y878fnubkt06q5` FOREIGN KEY (`group_id`) REFERENCES `reservation_groups` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reserv_slots`
--

DROP TABLE IF EXISTS `reserv_slots`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reserv_slots` (
  `classroom_id` bigint NOT NULL,
  `date` date NOT NULL,
  `user_id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `time_slot_id` int NOT NULL,
  PRIMARY KEY (`instance_id`,`time_slot_id`),
  UNIQUE KEY `uk_reserv_slots_classroom_time` (`classroom_id`,`date`,`time_slot_id`),
  UNIQUE KEY `uk_reserv_slots_user_time` (`user_id`,`date`,`time_slot_id`),
  KEY `FKje7flad1bk8fpub372qm9p3vy` (`time_slot_id`),
  CONSTRAINT `FKa0o0psbsbfu12fvtx2m8aospc` FOREIGN KEY (`instance_id`) REFERENCES `reserv_instances` (`id`),
  CONSTRAINT `FKje7flad1bk8fpub372qm9p3vy` FOREIGN KEY (`time_slot_id`) REFERENCES `time_slots` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reservation_group_days`
--

DROP TABLE IF EXISTS `reservation_group_days`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reservation_group_days` (
  `group_id` bigint NOT NULL,
  `day_of_week` enum('FRIDAY','MONDAY','SATURDAY','SUNDAY','THURSDAY','TUESDAY','WEDNESDAY') DEFAULT NULL,
  KEY `FKjvxnu7s7yeq4ouotgy4i0vyr4` (`group_id`),
  CONSTRAINT `FKjvxnu7s7yeq4ouotgy4i0vyr4` FOREIGN KEY (`group_id`) REFERENCES `reservation_groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reservation_groups`
--

DROP TABLE IF EXISTS `reservation_groups`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reservation_groups` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` enum('ACTIVE','CANCELLED') DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `semester_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKeqwrwkbp6wm2gvdri1xeyfc` (`uuid`),
  KEY `FKkhsnb1l4nfleukuspvk862cev` (`semester_id`),
  KEY `FKqutwax177x05m0sk34ygchbdn` (`user_id`),
  CONSTRAINT `FKkhsnb1l4nfleukuspvk862cev` FOREIGN KEY (`semester_id`) REFERENCES `semesters` (`id`),
  CONSTRAINT `FKqutwax177x05m0sk34ygchbdn` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reservation_history`
--

DROP TABLE IF EXISTS `reservation_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reservation_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `details` varchar(500) DEFAULT NULL,
  `event_type` enum('CANCELLED_BY_ADMIN','CANCELLED_BY_USER','CREATED','REASSIGNED','UPDATED') NOT NULL,
  `uuid` binary(16) NOT NULL,
  `group_id` bigint DEFAULT NULL,
  `instance_id` bigint DEFAULT NULL,
  `performed_by_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKsna7h9a86ve2jppnr7c0282wi` (`uuid`),
  KEY `FKf29u7grppe0fkxp3yv1bggno5` (`group_id`),
  KEY `FKr1wcy9d89ap0ng1kiud91b2qg` (`instance_id`),
  KEY `FK620k259iixpgpw6fs692wayjb` (`performed_by_user_id`),
  CONSTRAINT `FK620k259iixpgpw6fs692wayjb` FOREIGN KEY (`performed_by_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKf29u7grppe0fkxp3yv1bggno5` FOREIGN KEY (`group_id`) REFERENCES `reservation_groups` (`id`),
  CONSTRAINT `FKr1wcy9d89ap0ng1kiud91b2qg` FOREIGN KEY (`instance_id`) REFERENCES `reserv_instances` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `resources`
--

DROP TABLE IF EXISTS `resources`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `resources` (
  `id` int NOT NULL AUTO_INCREMENT,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  `uuid` binary(16) NOT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKl85pqajoc7v2drqv3tj3rcmpq` (`name`),
  UNIQUE KEY `uk_resources_uuid` (`uuid`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `roles`
--

DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKofx66keruapi6vyqpv6f2or37` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `roles` WRITE;
INSERT INTO `roles` (`id`, `name`, `description`, `created_at`, `updated_at`) VALUES
    (1, 'ADMIN', 'System administrator with full access', NOW(), NOW()),
    (2, 'TEACHER', 'Teacher with academic management permissions', NOW(), NOW());
UNLOCK TABLES;
--
-- Table structure for table `semesters`
--

DROP TABLE IF EXISTS `semesters`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `semesters` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `end_date` date NOT NULL,
  `name` varchar(255) NOT NULL,
  `start_date` date NOT NULL,
  `uuid` varbinary(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKci1s5s8npb7j044md3s0wdhsh` (`name`),
  UNIQUE KEY `UK1upm499a7f3o462ap6fpqxy7w` (`uuid`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `time_slots`
--

DROP TABLE IF EXISTS `time_slots`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `time_slots` (
  `id` int NOT NULL,
  `end_time` time NOT NULL,
  `start_time` time NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `time_slots` WRITE;
INSERT INTO `time_slots` (`id`, `start_time`, `end_time`) VALUES
    (1,  '07:00:00', '07:30:00'),
    (2,  '07:30:00', '08:00:00'),
    (3,  '08:00:00', '08:30:00'),
    (4,  '08:30:00', '09:00:00'),
    (5,  '09:00:00', '09:30:00'),
    (6,  '09:30:00', '10:00:00'),
    (7,  '10:00:00', '10:30:00'),
    (8,  '10:30:00', '11:00:00'),
    (9,  '11:00:00', '11:30:00'),
    (10, '11:30:00', '12:00:00'),
    (11, '12:00:00', '12:30:00'),
    (12, '12:30:00', '13:00:00'),
    (13, '13:00:00', '13:30:00'),
    (14, '13:30:00', '14:00:00'),
    (15, '14:00:00', '14:30:00'),
    (16, '14:30:00', '15:00:00'),
    (17, '15:00:00', '15:30:00'),
    (18, '15:30:00', '16:00:00'),
    (19, '16:00:00', '16:30:00'),
    (20, '16:30:00', '17:00:00'),
    (21, '17:00:00', '17:30:00'),
    (22, '17:30:00', '18:00:00'),
    (23, '18:00:00', '18:30:00'),
    (24, '18:30:00', '19:00:00');
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `email` varchar(150) NOT NULL,
  `first_name` varchar(100) NOT NULL,
  `last_names` varchar(100) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `username` varchar(100) NOT NULL,
  `uuid` binary(16) NOT NULL,
  `role_id` bigint NOT NULL,
  `institutional_id` varchar(20) NOT NULL,
  `extension` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`),
  UNIQUE KEY `UK6km2m9i3vjuy36rnvkgj1l61s` (`uuid`),
  KEY `FKp56c1712k691lhsyewcssf40f` (`role_id`),
  CONSTRAINT `FKp56c1712k691lhsyewcssf40f` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'test_aulas'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-24 14:18:29
