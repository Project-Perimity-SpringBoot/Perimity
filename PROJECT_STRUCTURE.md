# Perimity Project Architecture & Directory Structure Guide

This document provides a comprehensive overview of the **Perimity** codebase, detailing the folder structure, microservices, infrastructure components, CI/CD pipelines, and the purpose of every configuration file in the project.

---

## 🗂️ Project Root Directory Overview

```
Perimity/
├── .github/                      # GitHub Actions CI/CD Workflows
│   ├── PULL_REQUEST_TEMPLATE.md  # Standard template for Pull Request submissions
│   └── workflows/
│       ├── deploy.yml            # AWS ECR & EC2 Auto Scaling Deployment Pipeline
│       └── docker-build.yml      # CI Build verification & Campus-Agnostic Guard Rails
├── auth-service/                 # Spring Boot Microservice: Auth, JWT, OTP, Passwords
├── user-service/                 # Spring Boot Microservice: Student/Faculty/Guard Profiles
├── gatepass-service/             # Spring Boot Microservice: Gatepass Workflows & Approvals
├── campus-service/               # Spring Boot Microservice: Campus Metadata & Admin Rules
├── guard-service/                # Spring Boot Microservice: Gate Scanner & Mongo Entry Logs
├── qr-service/                   # Spring Boot Microservice: AES-256 QR Tokens & PDF Generation
├── discovery-server/             # Spring Boot Eureka Registry Server
├── frontend/                     # React + Vite + TypeScript Web Application
│   ├── Dockerfile                # Multi-stage production NGINX build for Frontend
│   └── nginx.conf                # NGINX SPA router & Microservice API reverse proxy
├── docker/                       # Infrastructure Initialization Scripts
│   └── postgres/
│       └── init-databases.sql    # Automatically creates all service PostgreSQL databases
├── scripts/                      # AWS & EC2 Automation Scripts
│   └── ec2-userdata.sh           # Auto-scaling startup script for EC2 instances
├── .env                          # Local Environment Variables (Ignored in Git)
├── .env.example                  # Environment Variables Template
├── docker-compose.yml            # Local Development Stack (Postgres, Mongo, Redis, RabbitMQ, MailHog)
├── docker-compose.prod.yml       # Production AWS Deployment Stack (ECR images + Infra)
├── COMPLETED_SETUP.md            # Summary of AWS Infrastructure & Security Groups setup
├── NEXT_STEPS_AND_ENV_GUIDE.md   # Guide for Production .env Configuration & AWS Deployment
└── PROJECT_STRUCTURE.md          # Complete Directory & File Architecture Guide (This file)
```

---

## 🧩 Comprehensive Component & File Descriptions

### 1. Root Configuration & Deployment Files

| File | Purpose / Description |
|---|---|
| [`docker-compose.yml`](file:///g:/perimity/Perimity/docker-compose.yml) | Defines the local development stack. Runs PostgreSQL, MongoDB, RabbitMQ, Redis, MailHog, and local microservices with live volume reloads. |
| [`docker-compose.prod.yml`](file:///g:/perimity/Perimity/docker-compose.prod.yml) | Production Docker Compose stack used by EC2 instances. Pulls production images from AWS ECR, configures container JVM heap limits (`-Xms128m -Xmx256m`), and mounts production volumes. |
| [`.env.example`](file:///g:/perimity/Perimity/.env.example) | Template file detailing all required environment variables (`POSTGRES_USER`, `JWT_SECRET`, `INTERNAL_API_KEY`, `QR_AES_KEY`, `MAIL_*`, etc.). Committed safely without real secrets. |
| [`.env`](file:///g:/perimity/Perimity/.env) | Local environment file containing machine-specific secret values. Never committed to Git. |
| [`COMPLETED_SETUP.md`](file:///g:/perimity/Perimity/COMPLETED_SETUP.md) | Documentation summarizing created AWS resources (Security Groups, ECR Repositories, Launch Template, ALB, Auto Scaling Group). |
| [`NEXT_STEPS_AND_ENV_GUIDE.md`](file:///g:/perimity/Perimity/NEXT_STEPS_AND_ENV_GUIDE.md) | Reference guide explaining how `.env` works in production and how to launch services on EC2. |

---

### 2. CI/CD & Automation (`.github/` and `scripts/`)

| File / Folder | Purpose / Description |
|---|---|
| [`.github/workflows/docker-build.yml`](file:///g:/perimity/Perimity/.github/workflows/docker-build.yml) | Continuous Integration pipeline. Runs **Guard Rails** (ensures no committed `.env` or hardcoded institution names) and verifies Docker builds on PRs. |
| [`.github/workflows/deploy.yml`](file:///g:/perimity/Perimity/.github/workflows/deploy.yml) | Continuous Deployment pipeline. Automatically builds Docker images for all 7 microservices on push to `main`, pushes them to AWS ECR, and triggers zero-downtime ASG refresh. |
| [`scripts/ec2-userdata.sh`](file:///g:/perimity/Perimity/scripts/ec2-userdata.sh) | UserData script attached to AWS Launch Template (`perimity-lt`). Automatically installs Docker, configures 4GB Swap space, logs into AWS ECR, and boots production containers. |

---

### 3. Frontend Web Application (`frontend/`)

Built with **React, Vite, TypeScript, and TailwindCSS / Vanilla CSS**.

| File / Subfolder | Purpose / Description |
|---|---|
| [`frontend/Dockerfile`](file:///g:/perimity/Perimity/frontend/Dockerfile) | Multi-stage Dockerfile: Stage 1 builds static assets using `node:20-alpine`, Stage 2 serves assets using lightweight `nginx:alpine`. |
| [`frontend/nginx.conf`](file:///g:/perimity/Perimity/frontend/nginx.conf) | Production NGINX web server configuration. Handles React SPA client-side routing (`try_files`) and reverse-proxies `/api/*` endpoints to backend microservices. |
| `frontend/src/features/` | Modular UI features grouped by user domain: |
| ├── `auth/` | Login, OTP Verification, Password Reset, Role-based Route Protection. |
| ├── `student/` | Student Dashboard, Apply for Gatepass, Pass History, QR Pass View. |
| ├── `faculty/` | Faculty Approval Dashboard, Student Request Reviews, Department Stats. |
| ├── `guard/` | Guard QR Scanner UI, Manual Entry Log, Verification Results. |
| ├── `campus-admin/` | Campus Settings, User Management, Blocklists, System Audits. |
| └── `visitor/` | Visitor Gatepass Application, Host Approval Status. |
| [`frontend/vite.config.ts`](file:///g:/perimity/Perimity/frontend/vite.config.ts) | Vite bundler configuration, path aliases (`@/`), and development API proxy rules. |

---

### 4. Backend Microservices Architecture

Each backend service is an independent Spring Boot application containerized with its own `Dockerfile` and Maven `pom.xml`.

#### A. Auth Service (`auth-service/`)
- **Port**: `8081` | **Database**: PostgreSQL (`authdb`) & Redis
- **Responsibilities**: User authentication, JWT issuance/validation, OTP generation & email dispatch via SMTP, password policy enforcement, password reset workflows.

#### B. User Service (`user-service/`)
- **Port**: `8082` | **Database**: PostgreSQL (`userdb`)
- **Responsibilities**: Student, Faculty, Guard, and Admin profiles, bulk student onboarding (XLSX/CSV import), Google Drive integration for passport photo sync, photo storage management.

#### C. Gatepass Service (`gatepass-service/`)
- **Port**: `8083` | **Database**: PostgreSQL (`gatepassdb`) & RabbitMQ
- **Responsibilities**: Gatepass creation, multi-level approval workflows (Faculty -> Warden -> Admin), status transitions (`PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`), visitor pass management. Publishes QR generation events to RabbitMQ.

#### D. Campus Service (`campus-service/`)
- **Port**: `8084` | **Database**: PostgreSQL (`campusdb`)
- **Responsibilities**: Campus structure management (Hostels, Blocks, Departments, Gate locations), institutional curfew rules, emergency lock-down rules.

#### E. Guard Service (`guard-service/`)
- **Port**: `8085` | **Database**: MongoDB (`entrylogdb`)
- **Responsibilities**: Fast gatekeeper verification engine. Receives scanned QR payload from guard device, calls `qr-service` for decryption, validates pass status, and logs entry/exit records in MongoDB.

#### F. QR Service (`qr-service/`)
- **Port**: `8086` | **Database**: PostgreSQL (`qrdb`) & RabbitMQ
- **Responsibilities**: Generates AES-256 encrypted QR tokens, renders PDF Gatepasses with embedded QR codes, listens to RabbitMQ events, and emails PDF passes to approved users via SMTP.

#### G. Discovery Server (`discovery-server/`)
- **Port**: `8761`
- **Responsibilities**: Spring Cloud Netflix Eureka registry for dynamic service discovery.

---

### 5. Database & Messaging Infrastructure (`docker/`)

| Path | Purpose / Description |
|---|---|
| [`docker/postgres/init-databases.sql`](file:///g:/perimity/Perimity/docker/postgres/init-databases.sql) | SQL initialization script executed by PostgreSQL on first boot. Automatically creates required databases: `authdb`, `userdb`, `gatepassdb`, `campusdb`, `qrdb`. |
| **MongoDB** | Used by `guard-service` for high-throughput, unstructured entry/exit gate log storage. |
| **RabbitMQ** | Asynchronous event broker used for decoupled communication (e.g. `user.created`, `qr.generate.request`). |
| **Redis** | High-speed caching for JWT blacklists, OTP sessions, and rate-limiting. |

---

## 🛠️ Summary Matrix

| Service / Layer | Technology Stack | Port | Storage / Engine |
|---|---|---|---|
| **Frontend** | React, Vite, TypeScript, NGINX | `80` | Client-Side SPA |
| **Auth Service** | Spring Boot, Spring Security | `8081` | PostgreSQL (`authdb`), Redis |
| **User Service** | Spring Boot, JPA | `8082` | PostgreSQL (`userdb`), Local Storage |
| **Gatepass Service** | Spring Boot, JPA, RabbitMQ | `8083` | PostgreSQL (`gatepassdb`) |
| **Campus Service** | Spring Boot, JPA | `8084` | PostgreSQL (`campusdb`) |
| **Guard Service** | Spring Boot, MongoDB | `8085` | MongoDB (`entrylogdb`) |
| **QR Service** | Spring Boot, iText PDF, AES-256 | `8086` | PostgreSQL (`qrdb`) |
| **Discovery Server** | Spring Cloud Eureka | `8761` | In-Memory Registry |
| **CI/CD** | GitHub Actions, AWS ECR | N/A | AWS Cloud |
| **Hosting & Elasticity** | AWS EC2 (`t3.large`), ALB, ASG | `80 / 443` | AWS Auto Scaling |
