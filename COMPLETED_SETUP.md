# Perimity AWS Infrastructure & Deployment — Completed Setup Report

This document records the complete architecture and resources configured for deploying **Perimity** microservices to **AWS EC2 with Elasticity and CI/CD**.

---

## 1. AWS Cloud Resources Created

### Security Groups
- **`perimity-alb-sg`**:
  - **Inbound**: HTTP (Port 80) and HTTPS (Port 443) from `0.0.0.0/0` (Public Internet).
  - **Outbound**: All traffic (`0.0.0.0/0`).
- **`perimity-ec2-sg`**:
  - **Inbound**: HTTP (Port 80) **ONLY** from `perimity-alb-sg`, SSH (Port 22) from Administrator IP (`106.213.86.250/32`).
  - **Outbound**: All traffic (`0.0.0.0/0`).

### Amazon Elastic Container Registry (AWS ECR) Repositories
Created 7 private image repositories in region `us-east-1` (Account ID: `682975283868`):
1. `682975283868.dkr.ecr.us-east-1.amazonaws.com/perimity/auth-service`
2. `682975283868.dkr.ecr.us-east-1.amazonaws.com/perimity/user-service`
3. `682975283868.dkr.ecr.us-east-1.amazonaws.com/perimity/gatepass-service`
4. `682975283868.dkr.ecr.us-east-1.amazonaws.com/perimity/campus-service`
5. `682975283868.dkr.ecr.us-east-1.amazonaws.com/perimity/guard-service`
6. `682975283868.dkr.ecr.us-east-1.amazonaws.com/perimity/qr-service`
7. `682975283868.dkr.ecr.us-east-1.amazonaws.com/perimity/frontend`

### IAM Role
- **`EC2-ECR-SSM-Role`**: Attached policies `AmazonEC2ContainerRegistryReadOnly` and `AmazonSSMManagedInstanceCore`.

### EC2 Launch Template (`perimity-lt`)
- **OS**: Amazon Linux 2023 AMI.
- **Instance Type**: `t3.large` (2 vCPU, 8 GB RAM).
- **Security Group**: `perimity-ec2-sg`.
- **IAM Instance Profile**: `EC2-ECR-SSM-Role`.
- **User Data**: Automates Docker & Docker Compose installation, ECR login, and container startup.

### Load Balancing & Elasticity
- **Target Group (`perimity-tg`)**: Protocol HTTP, Port 80, Health check path `/`.
- **Application Load Balancer (`perimity-alb`)**: Internet-facing Load Balancer providing constant Public DNS endpoint.
- **Auto Scaling Group (`perimity-asg`)**:
  - Min: 1 instance | Desired: 1 instance | Max: 3 instances.
  - Scaling Policy: Target tracking policy on Average CPU Utilization at 70%.

---

## 2. Project Source Files Created

- [`frontend/nginx.conf`](file:///g:/perimity/Perimity/frontend/nginx.conf): NGINX routing for React SPA and reverse proxy for `/api/*` microservices.
- [`frontend/Dockerfile`](file:///g:/perimity/Perimity/frontend/Dockerfile): Multi-stage Docker build for React Vite frontend.
- [`docker-compose.prod.yml`](file:///g:/perimity/Perimity/docker-compose.prod.yml): Production compose file referencing ECR images & infrastructure databases.
- [`scripts/ec2-userdata.sh`](file:///g:/perimity/Perimity/scripts/ec2-userdata.sh): Automated EC2 startup script.
- [`.github/workflows/deploy.yml`](file:///g:/perimity/Perimity/.github/workflows/deploy.yml): GitHub Actions CI/CD workflow for ECR build & deployment.
