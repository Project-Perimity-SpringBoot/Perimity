# Perimity AWS Deployment — Remaining Action Plan & .env Secret Guide

This document details what remains to be done and explains how environment variables (`.env`) are handled securely in production.

---

## Part 1: How the `.env` File Works in Production

### 🔐 Why `.env` is Important
Perimity services require essential environment variables and secrets to run:
- `POSTGRES_USER` & `POSTGRES_PASSWORD` (Database credentials)
- `JWT_SECRET` (Must be at least 32 characters long for cross-service authentication)
- `INTERNAL_API_KEY` (Protects service-to-service internal calls)
- `QR_AES_KEY` (Base64-encoded key for QR service encryption)
- `MAIL_USERNAME` & `MAIL_PASSWORD` (SMTP email configuration)

### 🛡️ Rules for `.env`
1. **Never commit `.env` to GitHub** (it is already in `.gitignore` and enforced by GitHub Actions guard-rails).
2. The file `.env.example` serves as a template.

### 🛠️ How to Place `.env` on Your EC2 Instances
When your EC2 instances launch, `docker-compose.prod.yml` expects a `.env` file located at `/opt/perimity/.env`.

#### Option A: Copy `.env` to EC2 via SSH / SSM (Simplest Method)
Connect to your EC2 instance via AWS Systems Manager Session Manager or SSH and create `/opt/perimity/.env`:
```bash
sudo cat << 'EOF' > /opt/perimity/.env
POSTGRES_USER=perimity
POSTGRES_PASSWORD=perimity_prod_secure_pass_2026
JWT_SECRET=bXlfc3VwZXJfc2VjcmV0X2p3dF9rZXlfcGVyaW1pdHlfMjAyNg==
INTERNAL_API_KEY=perimity_internal_secret_api_key_2026
QR_AES_KEY=4qX9L+vW8z+A7k3m9N2p5Q8r1T4v7X0z3B6e9H2k5M8=
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
MAIL_FROM_NAME=Perimity
EOF
```

#### Option B: Store in AWS Systems Manager Parameter Store (Automated)
Store parameters in AWS Parameter Store under `/perimity/prod/*`. The `ec2-userdata.sh` script automatically fetches them on instance launch to create `.env`.

---

## Part 2: Next Steps Checklist

### 1. Finalize GitHub Secrets Update
Ensure your GitHub Secrets under **Settings** -> **Secrets and variables** -> **Actions** are configured cleanly without spaces:
- `AWS_ACCESS_KEY_ID`: `<YOUR_AWS_ACCESS_KEY_ID>`
- `AWS_SECRET_ACCESS_KEY`: `<YOUR_AWS_SECRET_ACCESS_KEY>`
- `AWS_REGION`: `us-east-1`

### 2. Trigger & Monitor CI/CD Deployment
1. Go to the **Actions** tab in GitHub.
2. Select **`Perimity Production Build and Deploy to AWS ECR & EC2`**.
3. Click **Re-run all jobs**.
4. All 7 Docker images will build and push to AWS ECR cleanly!

### 3. Open Your Public URL
1. Go to AWS Console -> **EC2** -> **Load Balancers** -> **`perimity-alb`**.
2. Copy the **DNS name** (e.g. `http://perimity-alb-xxxx.us-east-1.elb.amazonaws.com`).
3. Open it in your web browser—your site is live on the internet!
